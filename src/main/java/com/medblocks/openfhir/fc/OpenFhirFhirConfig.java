
package com.medblocks.openfhir.fc;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.medblocks.openfhir.fc.schema.model.Condition;
import java.util.ArrayList;
import java.util.List;


/**
 * FHIR Config
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "resource",
        "condition",
        "multiple"
})

public class OpenFhirFhirConfig {

    /**
     * (Required)
     */
    @JsonProperty("resource")
    private String resource;

    /**
     * multiple means if multiple Resources of the same type can come out of one Composition, defaults to true
     */
    @JsonProperty("multiple")
    private Boolean multiple;

    @JsonProperty("condition")
    private List<Condition> condition;

    public OpenFhirFhirConfig copy() {
        final OpenFhirFhirConfig fhirConfig = new OpenFhirFhirConfig();
        fhirConfig.setResource(resource);
        fhirConfig.setMultiple(multiple);
        if (condition != null) {
            List<Condition> toAdd = new ArrayList<>();
            for (Condition condition1 : condition) {
                toAdd.add(condition1.copy());
            }
            fhirConfig.setCondition(toAdd);
        }
        return fhirConfig;
    }


    @JsonProperty("condition")
    public List<Condition> getCondition() {
        return condition;
    }

    @JsonProperty("condition")
    public void setCondition(List<Condition> condition) {
        this.condition = condition;
    }

    public OpenFhirFhirConfig withCondition(List<Condition> condition) {
        this.condition = condition;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("resource")
    public String getResource() {
        return resource;
    }

    /**
     * (Required)
     */
    @JsonProperty("resource")
    public void setResource(String resource) {
        this.resource = resource;
    }

    public OpenFhirFhirConfig withResource(String resource) {
        this.resource = resource;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("multiple")
    public Boolean getMultiple() {
        if(multiple == null) {
            return true;
        }
        return multiple;
    }

    /**
     * (Required)
     */
    @JsonProperty("multiple")
    public void setMultiple(Boolean multiple) {
        this.multiple = multiple;
    }

    public OpenFhirFhirConfig withMultiple(Boolean multiple) {
        this.multiple = multiple;
        return this;
    }

}
