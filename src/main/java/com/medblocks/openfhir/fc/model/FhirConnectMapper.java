
package com.medblocks.openfhir.fc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;


/**
 * Model Mapping
 * <p>
 * Model Mapping schema for file format v0.0.2
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "format",
        "version",
        "fhirConfig",
        "openEhrConfig",
        "mappings"
})
public class FhirConnectMapper {

    /**
     * (Required)
     */
    @JsonProperty("format")
    private String format;
    @JsonProperty("version")
    private String version;
    /**
     * FHIR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("fhirConfig")
    private FhirConfig fhirConfig;
    /**
     * openEHR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("openEhrConfig")
    private OpenEhrConfig openEhrConfig;
    /**
     * Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    private List<Mapping> mappings;

    /**
     * (Required)
     */
    @JsonProperty("format")
    public String getFormat() {
        return format;
    }

    public FhirConnectMapper copy() {
        final FhirConnectMapper fhirConnectMapper = new FhirConnectMapper();
        fhirConnectMapper.setFhirConfig(fhirConfig == null ? null : fhirConfig.doCopy());
        fhirConnectMapper.setFormat(format);
        fhirConnectMapper.setVersion(version);
        fhirConnectMapper.setOpenEhrConfig(openEhrConfig == null ? null : openEhrConfig.copy());
        if (mappings != null) {
            final List<Mapping> copiedMappings = new ArrayList<>();
            for (Mapping mapping : mappings) {
                copiedMappings.add(mapping.doCopy());
            }
            fhirConnectMapper.setMappings(copiedMappings);
        }
        return fhirConnectMapper;
    }

    /**
     * (Required)
     */
    @JsonProperty("format")
    public void setFormat(String format) {
        this.format = format;
    }

    public FhirConnectMapper withFormat(String format) {
        this.format = format;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    /**
     * (Required)
     */
    @JsonProperty("version")
    public void setVersion(String version) {
        this.version = version;
    }

    public FhirConnectMapper withVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * FHIR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("fhirConfig")
    public FhirConfig getFhirConfig() {
        return fhirConfig;
    }

    /**
     * FHIR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("fhirConfig")
    public void setFhirConfig(FhirConfig fhirConfig) {
        this.fhirConfig = fhirConfig;
    }

    public FhirConnectMapper withFhirConfig(FhirConfig fhirConfig) {
        this.fhirConfig = fhirConfig;
        return this;
    }

    /**
     * openEHR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("openEhrConfig")
    public OpenEhrConfig getOpenEhrConfig() {
        return openEhrConfig;
    }

    /**
     * openEHR Config
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("openEhrConfig")
    public void setOpenEhrConfig(OpenEhrConfig openEhrConfig) {
        this.openEhrConfig = openEhrConfig;
    }

    public FhirConnectMapper withOpenEhrConfig(OpenEhrConfig openEhrConfig) {
        this.openEhrConfig = openEhrConfig;
        return this;
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

    public FhirConnectMapper withMappings(List<Mapping> mappings) {
        this.mappings = mappings;
        return this;
    }

}
