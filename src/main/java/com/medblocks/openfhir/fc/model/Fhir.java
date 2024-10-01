
package com.medblocks.openfhir.fc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * FHIR
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "resourceType",
    "condition"
})
public class Fhir {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("resourceType")
    private String resourceType;

    @JsonProperty("condition")
    private Condition condition;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("resourceType")
    public String getResourceType() {
        return resourceType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("resourceType")
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Fhir withResourceType(String resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    @JsonProperty("condition")
    public Condition getCondition() {
        return condition;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("condition")
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public Fhir withCondition(Condition condition) {
        this.condition = condition;
        return this;
    }

}
