package com.medblocks.openfhir.tofhir;

import com.medblocks.openfhir.fc.schema.model.Condition;
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

    /**
     * openEHR archetype being mapped from
     */
    private String mainArchetype;

    /**
     * FHIR Resource being mapped to
     */
    private String targetResource;

    /**
     * FHIR path use for a specific fhir data point, without condition that's separately stored in Condition
     */
    private String fhirPath;

    /**
     * openEHR flat path, still in a simplified format (no recurring elements)
     */
    private String openEhrPath;

    /**
     * openEHR data type
     */
    private String openEhrType;

    /**
     * data as obtained from a flat path, including the index if element is recurring, if not, index is -1
     */
    private List<DataWithIndex> data;

    /**
     * FHIR Condition
     */
    private Condition condition;

    /**
     * if this specific mapping is followed my another mapping (true if its a followedBy or if it's a slot)
     */
    private boolean isFollowedBy;

    /**
     * if it's followed by, here is a FHIR Path of a parent element
     */
    private String parentFollowedByFhirPath;

    /**
     * if it's followed by, here is an openEHR Path of a parent element
     */
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
