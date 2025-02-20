
package com.medblocks.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "name",
        "openehr",
        "fhirCondition",
        "fhir",
        "openehrCondition"
})
@Data
public class Manual {

    @JsonProperty("name")
    private String name;
    @JsonProperty("openehr")
    private List<ManualEntry> openehr;
    @JsonProperty("fhirCondition")
    private Condition fhirCondition;
    @JsonProperty("fhir")
    private List<ManualEntry> fhir;
    @JsonProperty("openehrCondition")
    private Condition openehrCondition;

    public Manual copy() {
        final Manual with = new Manual();
        with.setName(name);
        with.setFhir(fhir == null ? null : fhir.stream().map(e -> e.copy()).collect(Collectors.toList()));
        with.setOpenehr(openehr == null ? null : openehr.stream().map(e -> e.copy()).collect(Collectors.toList()));
        with.setFhirCondition(fhirCondition);
        with.setOpenehrCondition(openehrCondition);
        return with;
    }

}
