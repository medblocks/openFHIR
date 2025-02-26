
package com.medblocks.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.medblocks.openfhir.fc.schema.Metadata;
import com.medblocks.openfhir.fc.schema.SchemaType;
import com.medblocks.openfhir.fc.schema.Spec;


/**
 * FHIRConnect Context Schema
 * <p>
 *
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "grammar",
        "type",
        "metadata",
        "spec",
        "context"
})
public class FhirConnectContext {

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("grammar")
    private String grammar;
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
     *
     * (Required)
     *
     */
    @JsonProperty("context")
    private Context context;

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("grammar")
    public String getGrammar() {
        return grammar;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("grammar")
    public void setGrammar(String grammar) {
        this.grammar = grammar;
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

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("context")
    public Context getContext() {
        return context;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("context")
    public void setContext(Context context) {
        this.context = context;
    }

}
