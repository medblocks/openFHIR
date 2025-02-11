package com.medblocks.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.Setter;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "composer",
        "systemId"
})
@Getter
@Setter
public class CompositionAdditionalConfig {

    @JsonProperty("composer")
    private String composer;

    @JsonProperty("systemId")
    private String systemId;

}
