package com.medblocks.plugins;

import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.medblocks.openfhir.conversion.FormatConverter;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Quantity;

import java.util.HashMap;
import java.util.Map;

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
            log.info("Applying custom mapping function: {}", mappingCode);
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
        
        /**
         * Converts FHIR dosage duration to OpenEHR administration duration
         */
        private boolean dosageDurationToAdministrationDuration(String openEhrPath, Object fhirValue, 
                                                          String openEhrType, Object flatComposition) {
            log.info("Converting dosage duration to administration duration");
            
            try {
                // Extract the duration value from FHIR
                // This is a simplified example - actual implementation would depend on the structure of fhirValue
                if (fhirValue == null) {
                    log.warn("No FHIR value provided for dosage duration");
                    return false;
                }
                
                // Example conversion logic
                // In a real implementation, you would need to:
                // 1. Extract the correct value and unit from fhirValue
                // 2. Convert to the appropriate OpenEHR format
                // 3. Set the value in the flatComposition at the correct path
                
                // For this example, we'll just log
                log.info("Would set duration value {} to OpenEHR path: {}", fhirValue, openEhrPath);
                
                // Here, you would modify the flatComposition object to set the value
                // flatComposition.setValueAt(openEhrType, convertedValue);
                
                return true;
            } catch (Exception e) {
                log.error("Error mapping dosage duration to administration duration", e);
                return false;
            }
        }

        /**
         * Converts FHIR Ratio to OpenEHR DV_QUANTITY
         * FHIR Ratio typically has numerator and denominator, while
         * OpenEHR DV_QUANTITY has magnitude, units, and precision
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
                
                // Extract values from numerator and denominator
                Quantity numerator = ratio.getNumerator();
                Quantity denominator = ratio.getDenominator();
                
                if (numerator == null || denominator == null || 
                    numerator.getValue() == null || denominator.getValue() == null) {
                    log.warn("Incomplete ratio value: numerator or denominator missing values");
                    return false;
                }
                
                // Calculate the magnitude as numerator value / denominator value
                double numeratorValue = numerator.getValue().doubleValue();
                double denominatorValue = denominator.getValue().doubleValue();
                double magnitude = denominatorValue != 0 ? numeratorValue / denominatorValue : 0.0;
                
                // Combine units from numerator and denominator
                String numeratorUnit = numerator.getUnit() != null ? numerator.getUnit() : numerator.getCode();
                String denominatorUnit = denominator.getUnit() != null ? denominator.getUnit() : denominator.getCode();
                String combinedUnit = numeratorUnit + "/" + denominatorUnit;
                
                // Set the magnitude and unit in the flat composition
                flatJson.add(openEhrPath + "|magnitude", new JsonPrimitive(magnitude));
                flatJson.add(openEhrPath + "|unit", new JsonPrimitive(combinedUnit));
                
                log.info("Converted ratio to DV_QUANTITY: magnitude={}, unit={}", magnitude, combinedUnit);
                return true;
                
            } catch (Exception e) {
                log.error("Error mapping FHIR Ratio to OpenEHR DV_QUANTITY", e);
                return false;
            }
        }
    }
} 
