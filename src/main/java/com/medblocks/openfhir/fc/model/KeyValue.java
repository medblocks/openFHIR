package com.medblocks.openfhir.fc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "key",
        "value",
})
public class KeyValue {
    @JsonProperty("key")
    private String key;
    @JsonProperty("value")
    private String value;

    @JsonProperty("key")
    public String getKey() {
        return key;
    }


    public KeyValue withKey(String key) {
        this.key =  key;
        return this;
    }

    @JsonProperty("key")
    public void setKey(String key) {
        this.key = key;
    }

    @JsonProperty("value")
    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    public void setValue(String value) {
        this.value = value;
    }

    public KeyValue withValue(String value) {
        this.value =  value;
        return this;
    }


}
