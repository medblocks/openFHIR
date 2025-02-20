
package com.medblocks.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "value",
        "path"
})
@Data
public class ManualEntry {

    @JsonProperty("value")
    private String value;
    @JsonProperty("path")
    private String path;

    public ManualEntry copy() {
        final ManualEntry with = new ManualEntry();
        with.setValue(value);
        with.setPath(path);
        return with;
    }

}
