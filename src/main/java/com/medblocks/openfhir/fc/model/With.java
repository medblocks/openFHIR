
package com.medblocks.openfhir.fc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "fhir",
        "openehr",
        "value",
        "unidirectional",
        "type"
})

public class With {

    @JsonProperty("fhir")
    private String fhir;
    @JsonProperty("openehr")
    private String openehr;
    @JsonProperty("type")
    private String type;
    @JsonProperty("value")
    private String value;
    @JsonProperty("unidirectional")
    private String unidirectional;

    public With doCopy() {
        final With with = new With();
        with.setFhir(fhir);
        with.setOpenehr(openehr);
        with.setType(type);
        with.setValue(value);
        with.setUnidirectional(unidirectional);
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

    @JsonProperty("unidirectional")
    public String getUnidirectional() {
        return unidirectional;
    }

    /**
     * (Required)
     */
    @JsonProperty("unidirectional")
    public void setUnidirectional(String unidirectional) {
        this.unidirectional = unidirectional;
    }

    public With withUnidirectional(String unidirectional) {
        this.unidirectional = unidirectional;
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

    @JsonProperty("value")
    public String getValue() {
        return value;
    }
    @JsonProperty("value")
    public void setValue(String value) {
        this.value = value;
    }

    public With withValue(String value) {
        this.value = value;
        return this;
    }

}
