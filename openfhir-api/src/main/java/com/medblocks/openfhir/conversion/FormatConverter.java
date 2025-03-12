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
} 