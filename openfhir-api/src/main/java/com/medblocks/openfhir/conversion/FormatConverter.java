package com.medblocks.openfhir.conversion;

import org.pf4j.ExtensionPoint;

/**
 * Extension point for converting between different healthcare data formats
 */
public interface FormatConverter extends ExtensionPoint {
    
    /**
     * Converts data from OpenEHR format to FHIR format
     * 
     * @param openEhrData The OpenEHR formatted data
     * @return The FHIR formatted data
     */
    String toFHIR(String openEhrData);
    
    /**
     * Converts data from FHIR format to OpenEHR format
     * 
     * @param fhirData The FHIR formatted data
     * @return The OpenEHR formatted data
     */
    String toOpenEHR(String fhirData);
    
    /**
     * Applies a specific mapping function to convert FHIR data to OpenEHR format
     * 
     * @param mappingCode The code identifying the mapping function to use
     * @param fhirPath The path to the FHIR data
     * @param fhirValue The FHIR value to convert
     * @param openEhrType The OpenEHR data type
     * @param flatComposition The OpenEHR flat composition to update
     * @return True if mapping was successful, false otherwise
     */
    boolean applyFhirToOpenEhrMapping(String mappingCode, String fhirPath, Object fhirValue, 
                                     String openEhrType, Object flatComposition);
} 