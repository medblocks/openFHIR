
package com.medblocks.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.medblocks.openfhir.fc.schema.Metadata;
import com.medblocks.openfhir.fc.schema.SchemaType;
import com.medblocks.openfhir.fc.schema.Spec;
import java.util.List;


/**
 * FHIRConnect Context Schema
 * <p>
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "engine",
        "type",
        "metadata",
        "spec"
})
public class FhirConnectModel {

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("engine")
    private String engine;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    private SchemaType type;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("metadata")
    private Metadata metadata;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("spec")
    private Spec spec;

    /**
     * Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    private List<Mapping> mappings;



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

    public FhirConnectModel withMappings(List<Mapping> mappings) {
        this.mappings = mappings;
        return this;
    }


    /**
     *
     * (Required)
     *
     */
    @JsonProperty("engine")
    public String getEngine() {
        return engine;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("engine")
    public void setEngine(String engine) {
        this.engine = engine;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    public SchemaType getType() {
        return type;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("type")
    public void setType(SchemaType type) {
        this.type = type;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("metadata")
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("metadata")
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("spec")
    public Spec getSpec() {
        return spec;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("spec")
    public void setSpec(Spec spec) {
        this.spec = spec;
    }


}
