
package com.medblocks.openfhir.fc.schema.model;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.HashMap;
import java.util.Map;


/**
 * Model Mapping
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "name",
        "extension",
        "appendTo",
        "with",
        "fhirCondition",
        "openehrCondition",
        "followedBy",
        "reference"
})
public class Mapping {

    /**
     * (Required)
     */
    @JsonProperty("name")
    private String name;
    @JsonProperty("extension")
    private ModelExtension extension;
    @JsonProperty("appendTo")
    private String appendTo;
    @JsonProperty("slotArchetype")
    private String slotArchetype;
    /**
     * (Required)
     */
    @JsonProperty("with")
    private With with;
    @JsonProperty("fhirCondition")
    private Condition fhirCondition;
    @JsonProperty("openehrCondition")
    private Condition openehrCondition;
    @JsonProperty("followedBy")
    private FollowedBy followedBy;
    @JsonProperty("reference")
    private FhirConnectReference reference;

    public Mapping copy() {
        final Mapping mapping = new Mapping();
        mapping.setName(name);
        mapping.setExtension(extension);
        mapping.setAppendTo(appendTo);
        mapping.setSlotArchetype(slotArchetype);
        mapping.setWith(with == null ? null : with.copy());
        mapping.setFhirCondition(fhirCondition == null ? null : fhirCondition.copy());
        mapping.setOpenehrCondition(openehrCondition == null ? null : openehrCondition.copy());
        mapping.setFollowedBy(followedBy == null ? null : followedBy.copy());
        mapping.setReference(reference == null ? null : reference.copy());
        return mapping;
    }

    public Mapping copyOverWith(final Mapping copyingFrom) {
        this.setName(copyingFrom.getName());
        this.setExtension(copyingFrom.getExtension());
        this.setSlotArchetype(copyingFrom.getSlotArchetype());
        this.setAppendTo(copyingFrom.getAppendTo());
        this.setWith(copyingFrom.getWith());
        this.setFhirCondition(copyingFrom.getFhirCondition());
        this.setOpenehrCondition(copyingFrom.getOpenehrCondition());
        this.setFollowedBy(copyingFrom.getFollowedBy());
        this.setReference(copyingFrom.getReference());
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * (Required)
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Mapping withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("appendTo")
    public String getAppendTo() {
        return appendTo;
    }

    /**
     * (Required)
     */
    @JsonProperty("appendTo")
    public void setAppendTo(String appendTo) {
        this.appendTo = appendTo;
    }

    public Mapping withAppendTo(String appendTo) {
        this.appendTo = name;
        return this;
    }

    @JsonProperty("extension")
    public ModelExtension getExtension() {
        return extension;
    }

    @JsonProperty("extension")
    public void setExtension(ModelExtension extension) {
        this.extension = extension;
    }

    public Mapping withExtension(ModelExtension extension) {
        this.extension = extension;
        return this;
    }


    @JsonProperty("reference")
    public FhirConnectReference getReference() {
        return reference;
    }


    @JsonProperty("reference")
    public void setReference(FhirConnectReference reference) {
        this.reference = reference;
    }

    public Mapping withReference(FhirConnectReference reference) {
        this.reference = reference;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("with")
    public With getWith() {
        return with;
    }

    /**
     * (Required)
     */
    @JsonProperty("with")
    public void setWith(With with) {
        this.with = with;
    }

    public Mapping withWith(With with) {
        this.with = with;
        return this;
    }

    @JsonProperty("fhirCondition")
    public Condition getFhirCondition() {
        return fhirCondition;
    }

    @JsonProperty("fhirCondition")
    public void setFhirCondition(Condition fhirCondition) {
        this.fhirCondition = fhirCondition;
    }

    public Mapping withFhirCondition(Condition fhirCondition) {
        this.fhirCondition = fhirCondition;
        return this;
    }

    @JsonProperty("openehrCondition")
    public Condition getOpenehrCondition() {
        return openehrCondition;
    }

    @JsonProperty("openehrCondition")
    public void setOpenehrCondition(Condition openehrCondition) {
        this.openehrCondition = openehrCondition;
    }

    public Mapping withOpenehrCondition(Condition openehrCondition) {
        this.openehrCondition = openehrCondition;
        return this;
    }

    @JsonProperty("followedBy")
    public FollowedBy getFollowedBy() {
        return followedBy;
    }

    @JsonProperty("followedBy")
    public void setFollowedBy(FollowedBy followedBy) {
        this.followedBy = followedBy;
    }

    public Mapping withFollowedBy(FollowedBy followedBy) {
        this.followedBy = followedBy;
        return this;
    }

    @JsonProperty("slotArchetype")
    public String getSlotArchetype() {
        return slotArchetype;
    }

    @JsonProperty("slotArchetype")
    public void setSlotArchetype(String slotArchetype) {
        this.slotArchetype = slotArchetype;
    }

    public Mapping withSlotArchetype(String slotArchetype) {
        this.slotArchetype = slotArchetype;
        return this;
    }

    public enum ModelExtension {

        ADD("ADD"),
        APPEND("APPEND"),
        OVERWRITE("OVERWRITE");
        private final String value;
        private final static Map<String, ModelExtension> CONSTANTS = new HashMap<String, ModelExtension>();

        static {
            for (ModelExtension c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        ModelExtension(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static ModelExtension fromValue(String value) {
            ModelExtension constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
