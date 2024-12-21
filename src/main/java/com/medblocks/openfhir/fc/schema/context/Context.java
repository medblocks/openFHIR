
package com.medblocks.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.net.URI;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "profileUrl",
    "templateId",
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
    @JsonProperty("profileUrl")
    private URI profileUrl;
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
    @JsonProperty("profileUrl")
    public URI getProfileUrl() {
        return profileUrl;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("profileUrl")
    public void setProfileUrl(URI profileUrl) {
        this.profileUrl = profileUrl;
    }

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
