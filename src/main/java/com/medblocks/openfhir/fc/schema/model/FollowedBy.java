
package com.medblocks.openfhir.fc.schema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "mappings"
})

public class FollowedBy {

    /**
     * Following Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    private List<Mapping> mappings;

    public FollowedBy copy() {
        final FollowedBy followedBy = new FollowedBy();
        if (mappings != null) {
            final List<Mapping> mappings = new ArrayList<>();
            for (Mapping mapping : this.mappings) {
                mappings.add(mapping.copy());
            }
            followedBy.setMappings(mappings);
        }
        return followedBy;
    }

    /**
     * Following Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    public List<Mapping> getMappings() {
        return mappings;
    }

    /**
     * Following Mappings
     * <p>
     * <p>
     * (Required)
     */
    @JsonProperty("mappings")
    public void setMappings(List<Mapping> mappings) {
        this.mappings = mappings;
    }

    public FollowedBy withMappings(List<Mapping> mappings) {
        this.mappings = mappings;
        return this;
    }

}
