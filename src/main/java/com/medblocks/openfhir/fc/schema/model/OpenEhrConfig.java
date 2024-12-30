
package com.medblocks.openfhir.fc.schema.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * openEHR Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "archetype"
})

public class OpenEhrConfig {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("archetype")
    private String archetype;

    public OpenEhrConfig copy() {
        final OpenEhrConfig openEhrConfig = new OpenEhrConfig();
        openEhrConfig.setArchetype(archetype);
        return openEhrConfig;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("archetype")
    public String getArchetype() {
        return archetype;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("archetype")
    public void setArchetype(String archetype) {
        this.archetype = archetype;
    }

    public OpenEhrConfig withArchetype(String archetype) {
        this.archetype = archetype;
        return this;
    }

}
