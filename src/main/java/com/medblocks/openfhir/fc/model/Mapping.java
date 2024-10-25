
package com.medblocks.openfhir.fc.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Model Mapping
 * <p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "name",
        "with",
        "condition",
        "map",
        "followedBy",
        "reference"
})
public class Mapping {

    /**
     * (Required)
     */
    @JsonProperty("name")
    private String name;
    @JsonProperty("slotArchetype")
    private String slotArchetype;
    /**
     * (Required)
     */
    @JsonProperty("with")
    private With with;
    @JsonProperty("condition")
    private Condition condition;
    @JsonProperty("followedBy")
    private FollowedBy followedBy;
    @JsonProperty("reference")
    private FhirConnectReference reference;

    public Mapping doCopy() {
        final Mapping mapping = new Mapping();
        mapping.setName(name);
        mapping.setSlotArchetype(slotArchetype);
        mapping.setWith(with == null ? null : with.doCopy());
        mapping.setCondition(condition == null ? null : condition.doCopy());
        mapping.setFollowedBy(followedBy == null ? null : followedBy.doCopy());
        mapping.setReference(reference == null ? null : reference.doCopy());
        return mapping;
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

    @JsonProperty("condition")
    public Condition getCondition() {
        return condition;
    }

    @JsonProperty("condition")
    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public Mapping withCondition(Condition condition) {
        this.condition = condition;
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

}
