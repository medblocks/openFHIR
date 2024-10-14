package com.medblocks.openfhir.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.UuidGenerator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity                             // for postgres
@Table(name = "fhir_connect_mapper")         // for postgres
public class FhirConnectMapperEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;

    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    @Lob
    @JsonIgnore
    String fhirConnectMapperJson;

    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @Getter(AccessLevel.NONE)  // Prevents getter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    String archetype;

    @Transient
    FhirConnectMapper fhirConnectMapper;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (fhirConnectMapper == null) {
            return;
        }
        // Serialize object to JSON before persisting
        this.fhirConnectMapperJson = new Gson().toJson(fhirConnectMapper);
        if (fhirConnectMapper.getOpenEhrConfig() != null) {
            this.archetype = fhirConnectMapper.getOpenEhrConfig().getArchetype();
        }
    }

    @PostLoad
    public void postLoad() {
        if (StringUtils.isEmpty(fhirConnectMapperJson)) {
            return;
        }
        // Deserialize JSON after loading from DB
        this.fhirConnectMapper = new Gson().fromJson(fhirConnectMapperJson, FhirConnectMapper.class);
    }
}
