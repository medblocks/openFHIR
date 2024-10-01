
package com.medblocks.openfhir.fc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;


/**
 * openEHR
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "templateId",
    "archetypes"
})
public class OpenEHR {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("templateId")
    private String templateId;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("archetypes")
    private List<String> archetypes;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("templateId")
    public String getTemplateId() {
        return templateId;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("templateId")
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public OpenEHR withTemplateId(String templateId) {
        this.templateId = templateId;
        return this;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("archetypes")
    public List<String> getArchetypes() {
        return archetypes;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("archetypes")
    public void setArchetypes(List<String> archetypes) {
        this.archetypes = archetypes;
    }

    public OpenEHR withArchetypes(List<String> archetypes) {
        this.archetypes = archetypes;
        return this;
    }

}
