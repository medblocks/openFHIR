package com.medblocks.openfhir.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.UuidGenerator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity                             // for postgres
@Table(name = "fhir_connect_model")         // for postgres
public class FhirConnectModelEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;

    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    @Lob
    @JsonIgnore
    String fhirConnectModelJson;

    String archetype;
    String name;

    @Transient
    FhirConnectModel fhirConnectModel;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (fhirConnectModel == null) {
            return;
        }
        // Serialize object to JSON before persisting
        this.fhirConnectModelJson = new Gson().toJson(fhirConnectModel);
        if (fhirConnectModel.getSpec().getOpenEhrConfig() != null) {
            this.archetype = fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype();
        }
        if (fhirConnectModel.getMetadata() != null) {
            this.name = fhirConnectModel.getMetadata().getName();
        }
    }

    @PostLoad
    public void postLoad() {
        if (StringUtils.isEmpty(fhirConnectModelJson)) {
            return;
        }
        // Deserialize JSON after loading from DB
        this.fhirConnectModel = new Gson().fromJson(fhirConnectModelJson, FhirConnectModel.class);
    }
}
