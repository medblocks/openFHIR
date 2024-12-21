package com.medblocks.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "resourceType",
        "mappings"
})
public class FhirConnectReference {

    @JsonProperty("mappings")
    private List<Mapping> mappings;


    @JsonProperty("resourceType")
    private String resourceType;

    public FhirConnectReference copy() {
        final FhirConnectReference fhirConnectReference = new FhirConnectReference();
        fhirConnectReference.setResourceType(resourceType);
        if(mappings != null) {
            List<Mapping> toAdd = new ArrayList<>();
            for (Mapping mapping : mappings) {
                toAdd.add(mapping.copy());
            }
            fhirConnectReference.setMappings(toAdd);
        }
        return fhirConnectReference;
    }


    /**
     * Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    public List<Mapping> getMappings() {
        return mappings;
    }

    /**
     * Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    public FhirConnectReference withMappings(List<Mapping> mappings) {
        this.mappings = mappings;
        return this;
    }


    @JsonProperty("resourceType")
    public String getResourceType() {
        return resourceType;
    }

    /**
     * (Required)
     */
    @JsonProperty("resourceType")
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public FhirConnectReference withResourceType(String resourceType) {
        this.resourceType = resourceType;
        return this;
    }
}
