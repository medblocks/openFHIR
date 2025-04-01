
package com.medblocks.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.net.URI;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "profile",
    "template",
    "archetypes",
    "extensions",
    "start"
})
public class Context {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("profile")
    private ContextProfile profile;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("template")
    private ContextTemplate template;
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
    @JsonProperty("extensions")
    private List<String> extensions;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("start")
    private String start;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("profile")
    public ContextProfile getProfile() {
        return profile;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("profile")
    public void setProfile(ContextProfile profile) {
        this.profile = profile;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("template")
    public ContextTemplate getTemplate() {
        return template;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("template")
    public void setTemplate(ContextTemplate template) {
        this.template = template;
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

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("extensions")
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("extensions")
    public void setExtensions(List<String> extensions) {
        this.extensions = extensions;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("start")
    public String getStart() {
        return start;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("start")
    public void setStart(String start) {
        this.start = start;
    }

}
