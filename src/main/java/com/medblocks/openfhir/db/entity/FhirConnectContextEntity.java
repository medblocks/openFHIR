package com.medblocks.openfhir.db.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.medblocks.openfhir.fc.model.FhirConnectContext;
import jakarta.persistence.*;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.UuidGenerator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity                             // for postgres
@Table(name = "fhir_connect_context")         // for postgres
public class FhirConnectContextEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;

    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    @Lob
    @JsonIgnore
    String fhirConnectContextJson;


    @Setter(AccessLevel.NONE)  // Prevents setter generation for this field
    @Getter(AccessLevel.NONE)  // Prevents getter generation for this field
    @org.springframework.data.annotation.Transient // so it will be ignored by mongo
    String templateId;

    @Transient
    FhirConnectContext fhirConnectContext;

    @PrePersist
    @PreUpdate
    public void prePersist() {
        if (fhirConnectContext == null) {
            return;
        }
        // Serialize object to JSON before persisting
        this.fhirConnectContextJson = new Gson().toJson(fhirConnectContext);
        if (fhirConnectContext.getOpenEHR() != null) {
            this.templateId = fhirConnectContext.getOpenEHR().getTemplateId();
        }
    }

    @PostLoad
    public void postLoad() {
        if (StringUtils.isEmpty(fhirConnectContextJson)) {
            return;
        }
        // Deserialize JSON after loading from DB
        this.fhirConnectContext = new Gson().fromJson(fhirConnectContextJson, FhirConnectContext.class);
    }
}
