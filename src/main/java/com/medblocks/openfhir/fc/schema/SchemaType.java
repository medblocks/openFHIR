package com.medblocks.openfhir.fc.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.HashMap;
import java.util.Map;

public enum SchemaType {
    CONTEXT("context"),
    MODEL("model"),
    EXTENSION("extension");
    private final String value;
    private final static Map<String, SchemaType> CONSTANTS = new HashMap<String, SchemaType>();

    static {
        for (SchemaType c: values()) {
            CONSTANTS.put(c.value, c);
        }
    }

    SchemaType(String value) {
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
    public static SchemaType fromValue(String value) {
        SchemaType constant = CONSTANTS.get(value);
        if (constant == null) {
            throw new IllegalArgumentException(value);
        } else {
            return constant;
        }
    }
}
