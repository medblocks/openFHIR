package com.medblocks.openfhir.fc.schema.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "sem_ver"
})
@Data
public class ContextTemplate {

    @JsonProperty("id")
    private String id;

    @JsonProperty("sem_ver")
    private String sem_ver;
}
