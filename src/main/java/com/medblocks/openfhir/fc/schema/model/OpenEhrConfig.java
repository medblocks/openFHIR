
package com.medblocks.openfhir.fc.schema.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;


/**
 * openEHR Config
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "archetype",
    "revision"
})
@Data
public class OpenEhrConfig {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("archetype")
    private String archetype;

    @JsonProperty("revision")
    private String revision;

    public OpenEhrConfig copy() {
        final OpenEhrConfig openEhrConfig = new OpenEhrConfig();
        openEhrConfig.setArchetype(archetype);
        openEhrConfig.setRevision(revision);
        return openEhrConfig;
    }

    public OpenEhrConfig withArchetype(String archetype) {
        this.archetype = archetype;
        return this;
    }

}
