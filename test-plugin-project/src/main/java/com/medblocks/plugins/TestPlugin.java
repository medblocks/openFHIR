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
                // Add other mapping functions as needed
                default:
                    log.warn("Unknown mapping code for OpenEHR to FHIR: {}", mappingCode);
                    return false;
            }
        }
        
        /**
         * Converts FHIR dosage duration to OpenEHR administration duration
         * This function extracts the time duration from the denominator of a FHIR Ratio
         * and maps it to an OpenEHR DV_DURATION
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
                
                // Map the time unit to the appropriate OpenEHR duration field
                // The value is cast to int as DV_DURATION fields are integers
                int timeValue = (int) Math.round(value);
                flatJson.add(openEhrPath + "|" + timeUnit, new JsonPrimitive(timeValue));
                
                log.info("Mapped duration: {} {} to {}|{}", timeValue, timeUnit, openEhrPath, timeUnit);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping dosage duration to administration duration", e);
                return false;
            }
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
         * OpenEHR DV_QUANTITY has magnitude and unit (e.g., "ml/h")
         * FHIR Ratio requires separate numerator and denominator quantities
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
                    // We're likely receiving the full flat JSON object
                    JsonObject flatJson = (JsonObject) openEhrValue;
                    
                    // Look for paths that might contain our DV_QUANTITY values
                    // This would typically use the fhirPath parameter to determine what we're looking for
                    for (String key : flatJson.keySet()) {
                        if (key.endsWith("|magnitude")) {
                            String basePath = key.substring(0, key.indexOf("|magnitude"));
                            String unitPath = basePath + "|unit";
                            
                            if (flatJson.has(unitPath) && key.contains("rate")) {
                                // Found our magnitude and unit
                                magnitude = flatJson.get(key).getAsDouble();
                                unit = flatJson.get(unitPath).getAsString();
                                break;
                            }
                        }
                    }
                    
                    if (unit.isEmpty()) {
                        log.warn("Could not find rate quantity in flat JSON");
                        return false;
                    }
                } else if (openEhrValue instanceof StringType) {
                    // If we're given a string, parse it for magnitude and unit
                    String valueStr = ((StringType) openEhrValue).getValue();
                    // Example: "250 ml/h" needs to be parsed into magnitude=250, unit="ml/h"
                    Pattern pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(.+)");
                    Matcher matcher = pattern.matcher(valueStr);
                    if (matcher.find()) {
                        magnitude = Double.parseDouble(matcher.group(1));
                        unit = matcher.group(2).trim();
                    } else {
                        log.warn("Could not parse quantity from string: {}", valueStr);
                        return false;
                    }
                } else if (openEhrValue instanceof Map) {
                    // Handle case where we get a map with magnitude and unit keys
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) openEhrValue;
                    if (dataMap.containsKey("magnitude")) {
                        magnitude = Double.parseDouble(dataMap.get("magnitude").toString());
                    }
                    if (dataMap.containsKey("unit")) {
                        unit = dataMap.get("unit").toString();
                    }
                } else {
                    log.warn("Unexpected OpenEHR value type: {}", openEhrValue.getClass().getName());
                    return false;
                }
                
                // Parse the unit into numerator and denominator
                // Example: "ml/h" -> numerator="ml", denominator="h"
                String numeratorUnit;
                String denominatorUnit;
                if (unit.contains("/")) {
                    String[] parts = unit.split("/", 2);
                    numeratorUnit = parts[0].trim();
                    denominatorUnit = parts[1].trim();
                } else {
                    // If no division in unit, use the whole unit as numerator and "1" as denominator
                    numeratorUnit = unit;
                    denominatorUnit = "1";
                }
                
                // Create FHIR Ratio
                Ratio ratio = new Ratio();
                
                // Set numerator (e.g., 250 ml)
                Quantity numerator = new Quantity();
                numerator.setValue(magnitude);
                numerator.setUnit(numeratorUnit);
                numerator.setSystem("http://unitsofmeasure.org");
                numerator.setCode(numeratorUnit);
                ratio.setNumerator(numerator);
                
                // Set denominator (e.g., 1 h)
                Quantity denominator = new Quantity();
                denominator.setValue(1); // Assumed to be 1 as per convention
                denominator.setUnit(denominatorUnit);
                denominator.setSystem("http://unitsofmeasure.org");
                denominator.setCode(denominatorUnit);
                ratio.setDenominator(denominator);
                
                // Add the created ratio to the result list
                createdValues.add(ratio);
                
                log.info("Created FHIR Ratio: numerator={} {}, denominator={} {}", 
                        magnitude, numeratorUnit, 1, denominatorUnit);
                
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping OpenEHR DV_QUANTITY to FHIR Ratio", e);
                return false;
            }
        }
    }
} 
