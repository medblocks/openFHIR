
package com.medblocks.openfhir.fc.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;
import com.medblocks.openfhir.fc.schema.model.FhirConfig;
import com.medblocks.openfhir.fc.schema.model.OpenEhrConfig;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "system",
    "version",
    "extensionOf",
    "openEhrConfig",
    "fhirConfig"
})
public class Spec {

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    private System system;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    private Version version;
    /**
     *
     * (Required)
     *
     */
    @JsonProperty("extensionOf")
    private String extensionOf;

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
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public System getSystem() {
        return system;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("system")
    public void setSystem(System system) {
        this.system = system;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("extensionOf")
    public String getExtensionOf() {
        return extensionOf;
    }

    /**
     *
     * (Required)
     *
     */
    @JsonProperty("extensionOf")
    public void setSystem(String extensionOf) {
        this.extensionOf = extensionOf;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public Version getVersion() {
        return version;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("version")
    public void setVersion(Version version) {
        this.version = version;
    }

    @JsonProperty("fhirConfig")
    public FhirConfig getFhirConfig() {
        return fhirConfig;
    }

    @JsonProperty("fhirConfig")
    public void setFhirConfig(final FhirConfig fhirConfig) {
        this.fhirConfig = fhirConfig;
    }

    @JsonProperty("openEhrConfig")
    public OpenEhrConfig getOpenEhrConfig() {
        return openEhrConfig;
    }

    @JsonProperty("openEhrConfig")
    public void setOpenEhrConfig(final OpenEhrConfig openEhrConfig) {
        this.openEhrConfig = openEhrConfig;
    }

    public enum System {

        FHIR("FHIR");
        private final String value;
        private final static Map<String, System> CONSTANTS = new HashMap<String, System>();

        static {
            for (System c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        System(String value) {
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
        public static System fromValue(String value) {
            System constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

    
    public enum Version {

        R_4("R4");
        private final String value;
        private final static Map<String, Version> CONSTANTS = new HashMap<String, Version>();

        static {
            for (Version c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Version(String value) {
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
        public static Version fromValue(String value) {
            Version constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }




    public enum Extension {

    }

}
