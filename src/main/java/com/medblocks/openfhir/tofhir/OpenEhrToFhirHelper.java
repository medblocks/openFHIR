package com.medblocks.openfhir.tofhir;

import com.medblocks.openfhir.fc.model.Condition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.Base;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenEhrToFhirHelper {

    private String mainArchetype;
    private String targetResource;
    private String targetResourceCondition;

    private String fhirPath;
    private String openEhrPath;
    private String openEhrType;
    private List<DataWithIndex> data;
    private Condition condition;
    private boolean isFollowedBy;
    private String parentFollowedByFhirPath;
    private String parentFollowedByOpenEhr;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DataWithIndex {
        private Base data;
        private int index;
        private String fullOpenEhrPath;
    }

}
