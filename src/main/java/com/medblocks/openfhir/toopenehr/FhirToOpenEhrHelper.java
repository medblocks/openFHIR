package com.medblocks.openfhir.toopenehr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

/**
 * Class for helping with mapping from FHIR to openEHR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirToOpenEhrHelper {
    /**
     * fhir path for extracting data point from a specific FHIR Resource
     */
    private String fhirPath;

    /**
     * limiting criteria as constructed from Conditions
     */
    private String limitingCriteria;

    /**
     * simplified openEHR path as constructed from fhir connect model mappers
     */
    private String openEhrPath;

    /**
     * openEHR type as defined in the fhir connect model mapper
     */
    private String openEhrType;

    /**
     * archetype id
     */
    private String archetype;

    private String hardcodingValue;

    /**
     * if a specific model mapper returns multiple resources or a single one; default is TRUE
     */
    private Boolean multiple;

    /**
     * if a mapping contains an external program name
     */
    private String mappingCode;

    /**
     * inner elements populated if a mapping is followed by other mappings or slot mappings
     */
    private List<FhirToOpenEhrHelper> fhirToOpenEhrHelpers;

    public FhirToOpenEhrHelper doClone() {
        FhirToOpenEhrHelper clone = FhirToOpenEhrHelper.builder()
                .archetype(this.archetype)
                .fhirPath(this.fhirPath)
                .limitingCriteria(this.limitingCriteria)
                .multiple(this.multiple)
                .hardcodingValue(this.hardcodingValue)
                .openEhrPath(this.openEhrPath)
                .openEhrType(this.openEhrType)
                .mappingCode(this.mappingCode)
                .build();
        
        if (this.fhirToOpenEhrHelpers != null) {
            List<FhirToOpenEhrHelper> clonedHelpers = new ArrayList<>();
            for (FhirToOpenEhrHelper helper : this.fhirToOpenEhrHelpers) {
                clonedHelpers.add(helper.doClone());
            }
            clone.setFhirToOpenEhrHelpers(clonedHelpers);
        }
        
        return clone;
    }

    public Boolean getMultiple() {
        if (multiple == null) {
            return true;
        }
        return multiple;
    }
}
