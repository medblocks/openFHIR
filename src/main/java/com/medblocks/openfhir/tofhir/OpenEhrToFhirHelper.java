package com.medblocks.openfhir.tofhir;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.schema.model.Condition;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hl7.fhir.r4.model.Base;

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

    private List<Condition> typeConditions;

    /**
     * openEHR Condition
     */
    private Condition openehrCondition;

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

     /**
      * Custom mapping code for plugin-based conversion
      */
      private String mappingCode;

    /**
     * Return the data list, ensuring it's never null
     */
    public List<DataWithIndex> getData() {
        if (data == null) {
            data = new ArrayList<>();
        }
        return data;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DataWithIndex {

        private Base data;
        private int index;
        private String fullOpenEhrPath;
    }

    /**
     * for fallback purposes
     */
    public List<Condition> getTypeConditions() {
        if (typeConditions != null) {
            return typeConditions;
        }
        if (condition != null && FhirConnectConst.CONDITION_OPERATOR_TYPE.equals(condition.getOperator())) {
            return new ArrayList<>(List.of(condition));
        }
        return null;
    }

    public OpenEhrToFhirHelper addTypeCondition(final Condition typeCondition) {
        if(typeConditions == null) {
            typeConditions = new ArrayList<>();
        }
        typeConditions.add(typeCondition);
        return this;
    }

}
