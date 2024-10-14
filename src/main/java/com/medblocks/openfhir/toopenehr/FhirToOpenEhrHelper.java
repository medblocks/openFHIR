package com.medblocks.openfhir.toopenehr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FhirToOpenEhrHelper {
    private String fhirPath;
    private String limitingCriteria;
    private String openEhrPath;
    private String openEhrType;
    private String archetype;
    private Boolean multiple;
    private List<FhirToOpenEhrHelper> fhirToOpenEhrHelpers;

    public FhirToOpenEhrHelper clone() {
        return FhirToOpenEhrHelper.builder()
                .fhirPath(fhirPath)
                .limitingCriteria(limitingCriteria)
                .openEhrPath(openEhrPath)
                .openEhrType(openEhrType)
                .archetype(archetype)
                .multiple(multiple)
                .fhirToOpenEhrHelpers(fhirToOpenEhrHelpers == null ? null : fhirToOpenEhrHelpers.stream().map(en -> en.clone()).collect(Collectors.toList()))
                .build();
    }

    public Boolean getMultiple() {
        if (multiple == null) {
            return true;
        }
        return multiple;
    }
}
