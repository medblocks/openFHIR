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
                                             String fhirType, List<Object> createdValues) {
            log.info("Applying OpenEHR to FHIR mapping function: {}", mappingCode);
            log.info("FHIR Path: {}, Value type: {}, FHIR Type: {}", fhirPath, 
                     openEhrValue != null ? openEhrValue.getClass().getName() : "null", fhirType);
            
            // Dispatch to the appropriate mapping function based on the mappingCode
            switch (mappingCode) {
                case "ratio_to_dv_quantity":
                    return dv_quantity_to_ratio(fhirPath, openEhrValue, fhirType, createdValues);
                case "dosageDurationToAdministrationDuration":
                    return administrationDurationToDosageDuration(fhirPath, openEhrValue, fhirType, createdValues);
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
                                        String fhirType, List<Object> createdValues) {
            log.info("Converting OpenEHR DV_QUANTITY to FHIR Ratio");
            
            try {
                // Extract magnitude and unit from the OpenEHR data
                double magnitude = 0.0;
                String unit = "";
                
                // Handle different potential input types
                if (openEhrValue instanceof JsonObject) {
                    JsonObject jsonValue = (JsonObject) openEhrValue;
                    
                    // Check if we have the magnitude and unit directly
                    if (jsonValue.has("magnitude") && jsonValue.has("unit")) {
                        magnitude = jsonValue.get("magnitude").getAsDouble();
                        unit = jsonValue.get("unit").getAsString();
                    } else {
                        // Look for paths with quantity_value|magnitude pattern
                        for (Map.Entry<String, JsonElement> entry : jsonValue.entrySet()) {
                            String key = entry.getKey();
                            if (key.endsWith("|magnitude")) {
                                String basePath = key.substring(0, key.indexOf("|magnitude"));
                                String unitPath = basePath + "|unit";
                                
                                if (jsonValue.has(unitPath)) {
                                    magnitude = jsonValue.get(key).getAsDouble();
                                    unit = jsonValue.get(unitPath).getAsString();
                                    break;
                                }
                            }
                        }
                    }
                } else if (openEhrValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) openEhrValue;
                    
                    // Check for direct magnitude and unit keys
                    if (dataMap.containsKey("magnitude") && dataMap.containsKey("unit")) {
                        magnitude = Double.parseDouble(dataMap.get("magnitude").toString());
                        unit = dataMap.get("unit").toString();
                    } else {
                        // Look for nested paths
                        for (String key : dataMap.keySet()) {
                            if (key.endsWith("|magnitude")) {
                                String basePath = key.substring(0, key.indexOf("|magnitude"));
                                String unitPath = basePath + "|unit";
                                
                                if (dataMap.containsKey(unitPath)) {
                                    magnitude = Double.parseDouble(dataMap.get(key).toString());
                                    unit = dataMap.get(unitPath).toString();
                                    break;
                                }
                            }
                        }
                    }
                } else if (openEhrValue instanceof String) {
                    // Try to parse a string like "600 mg"
                    String valueStr = (String) openEhrValue;
                    Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(.+)");
                    Matcher matcher = pattern.matcher(valueStr);
                    if (matcher.find()) {
                        magnitude = Double.parseDouble(matcher.group(1));
                        unit = matcher.group(2).trim();
                    } else {
                        log.warn("Could not parse quantity from string: {}", valueStr);
                        return false;
                    }
                } else {
                    log.warn("Unsupported OpenEHR value type: {}", openEhrValue.getClass().getName());
                    return false;
                }
                
                if (magnitude == 0.0 && unit.isEmpty()) {
                    log.warn("Could not extract magnitude and unit from OpenEHR value");
                    return false;
                }
                
                // Create FHIR Ratio
                Ratio ratio = new Ratio();
                
                // Set numerator with the extracted values
                Quantity numerator = new Quantity();
                numerator.setValue(magnitude);
                numerator.setUnit(unit);
                numerator.setSystem("http://unitsofmeasure.org");
                numerator.setCode(unit);
                ratio.setNumerator(numerator);
                
                // Add the created ratio to the result list
                createdValues.add(ratio);
                
                log.info("Created FHIR Ratio with numerator: {} {}", magnitude, unit);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping OpenEHR DV_QUANTITY to FHIR Ratio", e);
                return false;
            }
        }

        /**
         * Converts OpenEHR administration duration to FHIR dosage duration
         * This function extracts the time duration from an OpenEHR DV_DURATION in ISO 8601 format
         * and maps it to the denominator of a FHIR Ratio
         */
        private boolean administrationDurationToDosageDuration(String fhirPath, Object openEhrValue, 
                                                              String fhirType, List<Object> createdValues) {
            log.info("Converting OpenEHR administration duration to FHIR dosage duration");
            
            try {
                // Extract the ISO 8601 duration string from the OpenEHR data
                String isoDuration = null;
                
                if (openEhrValue instanceof JsonObject) {
                    JsonObject jsonValue = (JsonObject) openEhrValue;
                    
                    // Look for a duration field
                    for (Map.Entry<String, JsonElement> entry : jsonValue.entrySet()) {
                        String key = entry.getKey();
                        if (key.endsWith("/duration") || key.equals("duration")) {
                            isoDuration = jsonValue.get(key).getAsString();
                            break;
                        }
                    }
                } else if (openEhrValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) openEhrValue;
                    
                    // Look for a duration field
                    for (String key : dataMap.keySet()) {
                        if (key.endsWith("/duration") || key.equals("duration")) {
                            isoDuration = dataMap.get(key).toString();
                            break;
                        }
                    }
                } else if (openEhrValue instanceof String) {
                    // Assume the string is the ISO duration directly
                    isoDuration = (String) openEhrValue;
                    
                    // Validate that it looks like an ISO 8601 duration
                    if (!isoDuration.startsWith("P")) {
                        log.warn("String value does not appear to be an ISO 8601 duration: {}", isoDuration);
                        return false;
                    }
                } else {
                    log.warn("Unsupported OpenEHR value type for duration: {}", 
                            openEhrValue != null ? openEhrValue.getClass().getName() : "null");
                    return false;
                }
                
                if (isoDuration == null || isoDuration.isEmpty()) {
                    log.warn("Could not extract ISO 8601 duration from OpenEHR value");
                    return false;
                }
                
                // Parse the ISO 8601 duration
                // Format: P[nnY][nnM][nnW][nnD][T[nnH][nnM][nnS]]
                int value = 0;
                String unit = "";
                
                // Extract the numeric value and unit from the ISO duration
                if (isoDuration.contains("T")) {
                    // Time component exists
                    String timeComponent = isoDuration.substring(isoDuration.indexOf("T") + 1);
                    
                    if (timeComponent.contains("H")) {
                        value = extractNumericValue(timeComponent, "H");
                        unit = "h";
                    } else if (timeComponent.contains("M")) {
                        value = extractNumericValue(timeComponent, "M");
                        unit = "min";
                    } else if (timeComponent.contains("S")) {
                        value = extractNumericValue(timeComponent, "S");
                        unit = "s";
                    }
                } else {
                    // Date component only
                    if (isoDuration.contains("Y")) {
                        value = extractNumericValue(isoDuration, "Y");
                        unit = "a";
                    } else if (isoDuration.contains("M")) {
                        value = extractNumericValue(isoDuration, "M");
                        unit = "mo";
                    } else if (isoDuration.contains("W")) {
                        value = extractNumericValue(isoDuration, "W");
                        unit = "wk";
                    } else if (isoDuration.contains("D")) {
                        value = extractNumericValue(isoDuration, "D");
                        unit = "d";
                    }
                }
                
                if (value == 0 || unit.isEmpty()) {
                    log.warn("Could not parse value and unit from ISO duration: {}", isoDuration);
                    return false;
                }
                
                // Create FHIR Ratio with denominator
                Ratio ratio = new Ratio();
                
                // Set denominator with the extracted time value and unit
                Quantity denominator = new Quantity();
                denominator.setValue(value);
                denominator.setUnit(unit);
                denominator.setSystem("http://unitsofmeasure.org");
                denominator.setCode(unit);
                ratio.setDenominator(denominator);
                
                // Add the created ratio to the result list
                createdValues.add(ratio);
                
                log.info("Created FHIR Ratio with denominator: {} {}", value, unit);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping OpenEHR administration duration to FHIR dosage duration", e);
                return false;
            }
        }

        /**
         * Helper method to extract numeric value from ISO 8601 duration string
         * @param durationStr the duration string
         * @param unitChar the unit character to look for (Y, M, W, D, H, M, S)
         * @return the numeric value
         */
        private int extractNumericValue(String durationStr, String unitChar) {
            Pattern pattern = Pattern.compile("(\\d+)" + unitChar);
            Matcher matcher = pattern.matcher(durationStr);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            return 0;
        }
    }
} 
