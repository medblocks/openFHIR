
package com.medblocks.openfhir.fc.schema.model;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "targetRoot",
        "targetAttribute",
        "targetAttributes",
        "operator",
        "criteria",
        "identifying",
})

public class Condition {

    /**
     * (Required)
     */
    @JsonProperty("targetRoot")
    private String targetRoot;
    /**
     * (Required)
     */
    @JsonProperty("targetAttributes")
    private List<String> targetAttributes; // if multiple, then OR is implied between them. If you want AND, you need to write multiple conditions

    @JsonProperty("targetAttribute")
    private String targetAttribute; // if multiple, then OR is implied between them. If you want AND, you need to write multiple conditions

    /**
     * (Required)
     */
    @JsonProperty("operator")
    private String operator;
    /**
     * (Required)
     */
    @JsonProperty("criteria")
    private String criteria;
    @JsonProperty("identifying")
    private Boolean identifying;

    public Condition copy() {
        final Condition condition = new Condition();
        condition.setTargetRoot(targetRoot);
        condition.setTargetAttributes(targetAttributes);
        condition.setTargetAttribute(targetAttribute);
        condition.setOperator(operator);
        condition.setCriteria(criteria);
        condition.setIdentifying(identifying == null ? null : new Boolean(identifying.booleanValue()));
        return condition;
    }

    /**
     * (Required)
     */
    @JsonProperty("targetRoot")
    public String getTargetRoot() {
        return targetRoot;
    }

    /**
     * (Required)
     */
    @JsonProperty("targetRoot")
    public void setTargetRoot(String targetRoot) {
        this.targetRoot = targetRoot;
    }

    public Condition withTargetRoot(String targetRoot) {
        this.targetRoot = targetRoot;
        return this;
    }

    /**
     * Will return all targetAttributes. If only the singular (targetAttribute) is populated,
     * that one will be added to the list and returned as a list
     */
    @JsonProperty("targetAttributes")
    public List<String> getTargetAttributes() {
        if(StringUtils.isNotBlank(targetAttribute)) {
            return List.of(targetAttribute);
        }
        return targetAttributes;
    }

    /**
     * (Required)
     */
    @JsonProperty("targetAttributes")
    public void setTargetAttributes(List<String> targetAttributes) {
        this.targetAttributes = targetAttributes;
    }

    public Condition withTargetAttributes(List<String> targetAttributes) {
        this.targetAttributes = targetAttributes;
        return this;
    }

    /**
     * You should always us the one returning you an array and handle it accordingly.
     * @return
     */
    @Deprecated
    @JsonProperty("targetAttribute")
    public String getTargetAttribute() {
        return targetAttribute;
    }

    /**
     * (Required)
     */
    @JsonProperty("targetAttribute")
    public void setTargetAttribute(String targetAttribute) {
        this.targetAttribute = targetAttribute;
    }

    public Condition withTargetAttribute(String targetAttribute) {
        this.targetAttribute = targetAttribute;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("operator")
    public String getOperator() {
        return operator;
    }

    /**
     * (Required)
     */
    @JsonProperty("operator")
    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Condition withOperator(String operator) {
        this.operator = operator;
        return this;
    }

    /**
     * (Required)
     */
    @JsonProperty("criteria")
    public String getCriteria() {
        return criteria;
    }

    /**
     * (Required)
     */
    @JsonProperty("criteria")
    public void setCriteria(String criteria) {
        this.criteria = criteria;
    }

    public Condition withCriteria(String criteria) {
        this.criteria = criteria;
        return this;
    }

    @JsonProperty("identifying")
    public Boolean getIdentifying() {
        return identifying;
    }

    @JsonProperty("identifying")
    public void setIdentifying(Boolean identifying) {
        this.identifying = identifying;
    }

    public Condition withIdentifying(Boolean identifying) {
        this.identifying = identifying;
        return this;
    }

}
