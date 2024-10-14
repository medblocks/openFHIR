
package com.medblocks.openfhir.fc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Contextual Mapping
 * <p>
 * Contextual Mapping schema for file format v0.2.0
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "format",
    "openEHR",
    "fhir"
})
public class FhirConnectContext {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("format")
    private String format;
    /**
     * openEHR
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("openEHR")
    private OpenEHR openEHR;
    /**
     * FHIR
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("fhir")
    private Fhir fhir;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("format")
    public String getFormat() {
        return format;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("format")
    public void setFormat(String format) {
        this.format = format;
    }

    public FhirConnectContext withFormat(String format) {
        this.format = format;
        return this;
    }

    /**
     * openEHR
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("openEHR")
    public OpenEHR getOpenEHR() {
        return openEHR;
    }

    /**
     * openEHR
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("openEHR")
    public void setOpenEHR(OpenEHR openEHR) {
        this.openEHR = openEHR;
    }

    public FhirConnectContext withOpenEHR(OpenEHR openEHR) {
        this.openEHR = openEHR;
        return this;
    }

    /**
     * FHIR
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("fhir")
    public Fhir getFhir() {
        return fhir;
    }

    /**
     * FHIR
     * <p>
     * 
     * (Required)
     * 
     */
    @JsonProperty("fhir")
    public void setFhir(Fhir fhir) {
        this.fhir = fhir;
    }

    public FhirConnectContext withFhir(Fhir fhir) {
        this.fhir = fhir;
        return this;
    }

}
