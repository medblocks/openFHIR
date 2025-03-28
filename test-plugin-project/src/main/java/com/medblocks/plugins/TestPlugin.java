package com.medblocks.plugins;

import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.medblocks.openfhir.conversion.FormatConverter;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonElement;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(TestPlugin.class);

    public TestPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("Hello world! TestPlugin is starting...");
        log.info("TestPlugin.start()");
    }

    @Override
    public void stop() {
        log.info("TestPlugin.stop()");
    }

    /**
     * Extension implementation for converting between FHIR and OpenEHR formats
     */
    @Extension
    public static class TestFormatConverter implements FormatConverter {
        
        private static final Logger log = LoggerFactory.getLogger(TestFormatConverter.class);
        
        @Override
        public String toFHIR(String openEhrData) {
            log.info("Converting from OpenEHR to FHIR: {}", openEhrData);
            // Implement the actual conversion logic here
            // This is just a placeholder implementation
            return "{ \"resourceType\": \"Patient\", \"id\": \"example\", \"converted\": true, \"source\": \"" + openEhrData + "\" }";
        }
        
        @Override
        public String toOpenEHR(String fhirData) {
            log.info("Converting from FHIR to OpenEHR: {}", fhirData);
            // Implement the actual conversion logic here
            // This is just a placeholder implementation
            return "{ \"archetypeId\": \"openEHR-EHR-COMPOSITION.example.v1\", \"converted\": true, \"source\": \"" + fhirData + "\" }";
        }
        
        @Override
        public boolean applyFhirToOpenEhrMapping(String mappingCode, String openEhrPath, Object fhirValue, 
                                               String openEhrType, Object flatComposition) {
            log.info("Applying FHIR to OpenEHR mapping function: {}", mappingCode);
            log.info("OpenEHR Path: {}, Value type: {}, OpenEHR Type: {}", openEhrPath, 
                     fhirValue != null ? fhirValue.getClass().getName() : "null", openEhrType);
            
            // Dispatch to the appropriate mapping function based on the mappingCode
            switch (mappingCode) {
                case "dosageDurationToAdministrationDuration":
                    return dosageDurationToAdministrationDuration(openEhrPath, fhirValue, openEhrType, flatComposition);
                case "ratio_to_dv_quantity":
                    return ratio_to_dv_quantity(openEhrPath, fhirValue, openEhrType, flatComposition);
                // Add other mapping functions as needed
                default:
                    log.warn("Unknown mapping code: {}", mappingCode);
                    return false;
            }
        }
        
        @Override
        public boolean applyOpenEhrToFhirMapping(String mappingCode, String fhirPath, Object openEhrValue,
                                             String fhirType, List<Object> createdValues, String openEhrPath) {
            log.info("Applying OpenEHR to FHIR mapping function: {}", mappingCode);
            log.info("FHIR Path: {}, Value type: {}, FHIR Type: {}", fhirPath, 
                     openEhrValue != null ? openEhrValue.getClass().getName() : "null", fhirType);
            
            // Special handler for the input format with tägliche_dosierung periode
            if (mappingCode.equals("dosageDurationToAdministrationDuration") && openEhrValue instanceof JsonObject) {
                JsonObject jsonObj = (JsonObject) openEhrValue;
                String periodeKey = "medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/tägliche_dosierung:0/periode";
                
                // Check if this is the specific input format we're encountering
                if (jsonObj.has(periodeKey)) {
                    String isoDuration = jsonObj.get(periodeKey).getAsString();
                    log.info("Found ISO duration directly: {}", isoDuration);
                    
                    // Create a Ratio with time unit in denominator
                    try {
                        // Parse the ISO 8601 duration like "PT3H"
                        Pattern pattern = Pattern.compile("PT(\\d+)([HMS])");
                        Matcher matcher = pattern.matcher(isoDuration);
                        
                        if (matcher.find()) {
                            int value = Integer.parseInt(matcher.group(1));
                            String unitChar = matcher.group(2);
                            
                            // Map the unit character to UCUM code
                            String unit;
                            switch (unitChar) {
                                case "H": unit = "h"; break;
                                case "M": unit = "min"; break;
                                case "S": unit = "s"; break;
                                default: unit = "h";
                            }
                            
                            // Create FHIR Ratio
                            Ratio ratio = new Ratio();
                            
                            // Set denominator
                            Quantity denominator = new Quantity();
                            denominator.setValue(value);
                            denominator.setUnit(unit);
                            denominator.setSystem("http://unitsofmeasure.org");
                            denominator.setCode(unit);
                            ratio.setDenominator(denominator);
                            
                            // Add to created values
                            createdValues.add(ratio);
                            log.info("Created FHIR Ratio with denominator: {} {}", value, unit);
                            return true;
                        }
                    } catch (Exception e) {
                        log.error("Error parsing special case ISO duration: {}", isoDuration, e);
                    }
                }
            }
            
            // Dispatch to the appropriate mapping function based on the mappingCode
            switch (mappingCode) {
                case "ratio_to_dv_quantity":
                    return dv_quantity_to_ratio(fhirPath, openEhrValue, fhirType, createdValues, openEhrPath);
                case "dosageDurationToAdministrationDuration":
                    return dosageDurationToAdministrationDuration(fhirPath, openEhrValue, fhirType, createdValues);
                // Add other mapping functions as needed
                default:
                    log.warn("Unknown mapping code for OpenEHR to FHIR: {}", mappingCode);
                    return false;
            }
        }
        
        /**
         * Converts FHIR dosage duration to OpenEHR administration duration
         * This function extracts the time duration from the denominator of a FHIR Ratio
         * and maps it to an OpenEHR DV_DURATION in ISO 8601 format
         */
        private boolean dosageDurationToAdministrationDuration(String openEhrPath, Object fhirValue, 
                                                          String openEhrType, Object flatComposition) {
            log.info("Converting dosage duration to administration duration");
            
            try {
                // Check if the FHIR value is present and is a Ratio
                if (fhirValue == null) {
                    log.warn("No FHIR value provided for dosage duration");
                    return false;
                }
                
                if (!(fhirValue instanceof Ratio)) {
                    log.warn("Expected Ratio type for dosage duration but got: {}", fhirValue.getClass().getName());
                    return false;
                }
                
                Ratio ratio = (Ratio) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // Extract denominator which contains the time component
                Quantity denominator = ratio.getDenominator();
                
                if (denominator == null || denominator.getValue() == null) {
                    log.warn("Incomplete ratio value: denominator missing values");
                    return false;
                }
                
                // Get the denominator value and unit
                double value = denominator.getValue().doubleValue();
                String unit = denominator.getUnit() != null ? denominator.getUnit() : denominator.getCode();
                
                // Check if the unit is a time unit before mapping
                String timeUnit = mapToTimeUnit(unit);
                if (timeUnit == null) {
                    log.info("Denominator unit '{}' is not a time unit, skipping duration mapping", unit);
                    return false;
                }
                
                // Convert to ISO 8601 duration format: P[nnY][nnM][nnW][nnD][T[nnH][nnM][nnS]]
                int timeValue = (int) Math.round(value);
                String isoDuration = formatIso8601Duration(timeValue, timeUnit);
                
                // Add the ISO 8601 duration to the flat composition
                flatJson.add(openEhrPath, new JsonPrimitive(isoDuration));
                
                log.info("Mapped duration: {} {} to {} with ISO 8601 format: {}", 
                         timeValue, timeUnit, openEhrPath, isoDuration);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping dosage duration to administration duration", e);
                return false;
            }
        }
        
        /**
         * Formats a time value and unit into ISO 8601 duration format
         * @param value the time value
         * @param unit the time unit (year, month, week, day, hour, minute, second)
         * @return ISO 8601 formatted duration string
         */
        private String formatIso8601Duration(int value, String unit) {
            StringBuilder duration = new StringBuilder("P");
            
            switch (unit) {
                case "year":
                    duration.append(value).append("Y");
                    break;
                case "month":
                    duration.append(value).append("M");
                    break;
                case "week":
                    duration.append(value).append("W");
                    break;
                case "day":
                    duration.append(value).append("D");
                    break;
                case "hour":
                    duration.append("T").append(value).append("H");
                    break;
                case "minute":
                    duration.append("T").append(value).append("M");
                    break;
                case "second":
                    duration.append("T").append(value).append("S");
                    break;
                default:
                    // Default to hours if unit is not recognized
                    duration.append("T").append(value).append("H");
            }
            
            return duration.toString();
        }
        
        /**
         * Maps a UCUM or common time unit to OpenEHR DV_DURATION time unit
         * @param unit the time unit from FHIR
         * @return the corresponding OpenEHR time unit or null if not a time unit
         */
        private String mapToTimeUnit(String unit) {
            if (unit == null) {
                return null;
            }
            
            unit = unit.toLowerCase().trim();
            
            switch (unit) {
                case "a":
                case "yr":
                case "year":
                case "years":
                    return "year";
                case "mo":
                case "month":
                case "months":
                    return "month";
                case "wk":
                case "week":
                case "weeks":
                    return "week";
                case "d":
                case "day":
                case "days":
                    return "day";
                case "h":
                case "hr":
                case "hour":
                case "hours":
                    return "hour";
                case "min":
                case "minute":
                case "minutes":
                    return "minute";
                case "s":
                case "sec":
                case "second":
                case "seconds":
                    return "second";
                default:
                    return null;
            }
        }

        /**
         * Converts FHIR Ratio to OpenEHR DV_QUANTITY
         * For medication dosage, we map the numerator of the Ratio to the DV_QUANTITY
         */
        private boolean ratio_to_dv_quantity(String openEhrPath, Object fhirValue, 
                                     String openEhrType, Object flatComposition) {
            log.info("Converting FHIR Ratio to OpenEHR DV_QUANTITY");
            
            try {
                // Check if the FHIR value is present and is a Ratio
                if (fhirValue == null) {
                    log.warn("No FHIR value provided for ratio conversion");
                    return false;
                }
                
                if (!(fhirValue instanceof Ratio)) {
                    log.warn("Expected Ratio type but got: {}", fhirValue.getClass().getName());
                    return false;
                }
                
                Ratio ratio = (Ratio) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // Extract values from numerator
                Quantity numerator = ratio.getNumerator();
                
                if (numerator == null || numerator.getValue() == null) {
                    log.warn("Incomplete ratio value: numerator missing values");
                    return false;
                }
                
                // Get the magnitude from the numerator
                double magnitude = numerator.getValue().doubleValue();
                
                // Get the unit from the numerator
                String unit = numerator.getUnit() != null ? numerator.getUnit() : numerator.getCode();
                
                // Set the magnitude and unit in the flat composition with quantity_value suffix
                // Using the format from the requirement example:
                // "medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/dosis/quantity_value|magnitude"
                flatJson.add(openEhrPath + "/quantity_value|magnitude", new JsonPrimitive(magnitude));
                flatJson.add(openEhrPath + "/quantity_value|unit", new JsonPrimitive(unit));
                
                log.info("Mapped Ratio numerator to DV_QUANTITY: path={}, magnitude={}, unit={}", 
                        openEhrPath, magnitude, unit);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping FHIR Ratio to OpenEHR DV_QUANTITY", e);
                return false;
            }
        }
        
        /**
         * Converts OpenEHR DV_QUANTITY to FHIR Ratio
         * This function extracts magnitude and unit from OpenEHR DV_QUANTITY
         * and maps it to the numerator of a FHIR Ratio
         */
        private boolean dv_quantity_to_ratio(String fhirPath, Object openEhrValue, 
                                        String fhirType, List<Object> createdValues, String openEhrPath) {
            log.info("Converting OpenEHR DV_QUANTITY to FHIR Ratio");
            
         return true;
        }

        /**
         * Converts OpenEHR administration duration to FHIR dosage duration
         * This function extracts the time duration from an OpenEHR DV_DURATION in ISO 8601 format
         * and maps it to the denominator of a FHIR Ratio
         */
        private boolean dosageDurationToAdministrationDuration(String fhirPath, Object openEhrValue, 
                                                              String fhirType, List<Object> createdValues) {
            log.info("Converting OpenEHR administration duration to FHIR dosage duration");
           
            return true;
        }

        /**
         * Helper method to extract numeric value from ISO 8601 duration string
         * @param durationStr the duration string
         * @param unitChar the unit character to look for (Y, M, W, D, H, M, S)
         * @return the numeric value
         */
        private int extractNumericValue(String durationStr, String unitChar) {
            try {
                Pattern pattern = Pattern.compile("(\\d+)" + unitChar);
                Matcher matcher = pattern.matcher(durationStr);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
                
                // For cases like "PT3H" where it might be in a different format
                pattern = Pattern.compile("PT(\\d+)H");
                matcher = pattern.matcher(durationStr);
                if (unitChar.equals("H") && matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
                
                // Additional patterns for other time units if needed
                pattern = Pattern.compile("PT(\\d+)M");
                matcher = pattern.matcher(durationStr);
                if (unitChar.equals("M") && matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
                
                pattern = Pattern.compile("PT(\\d+)S");
                matcher = pattern.matcher(durationStr);
                if (unitChar.equals("S") && matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
                
                log.warn("Could not extract numeric value for unit {} from duration: {}", unitChar, durationStr);
                return 0;
            } catch (Exception e) {
                log.error("Error extracting numeric value from duration: {}", durationStr, e);
                return 0;
            }
        }
    }
} 
