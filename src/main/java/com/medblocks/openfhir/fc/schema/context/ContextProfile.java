package com.medblocks.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.net.URL;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "url",
        "version"
})
@Data
public class ContextProfile {

    @JsonProperty("url")
    private String url;
    @JsonProperty("version")
    private String version;
}
