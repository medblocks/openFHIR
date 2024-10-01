
package com.medblocks.openfhir.fc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "fhir",
        "openehr",
        "type"
})

public class With {

    /**
     * (Required)
     */
    @JsonProperty("fhir")
    private String fhir;
    /**
     * (Required)
     */
    @JsonProperty("openehr")
    private String openehr;
    @JsonProperty("type")
    private String type;

    public With copy() {
        final With with = new With();
        with.setFhir(fhir);
        with.setOpenehr(openehr);
        with.setType(type);
        return with;
    }

    /**
     * (Required)
     */
    @JsonProperty("fhir")
    public String getFhir() {
        return fhir;
    }

    /**
     * (Required)
     */
    @JsonProperty("fhir")
    public void setFhir(String fhir) {
        this.fhir = fhir;
    }

    public With withFhir(String fhir) {
        this.fhir = fhir;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    /**
     * (Required)
     */
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public With withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("openehr")
    public String getOpenehr() {
        return openehr;
    }

    /**
     * (Required)
     */
    @JsonProperty("openehr")
    public void setOpenehr(String openehr) {
        this.openehr = openehr;
    }

    public With withOpenehr(String openehr) {
        this.openehr = openehr;
        return this;
    }

}
