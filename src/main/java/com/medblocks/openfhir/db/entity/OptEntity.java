package com.medblocks.openfhir.db.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity                             // for postgres
@Table(name = "opt")         // for postgres
public class OptEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    String id;
    @Lob
    String content;
    @Column(unique = true) // for pg
    String templateId;
    String originalTemplateId;
    String displayTemplateId;

    public OptEntity copy() {
        return new OptEntity(id, content, templateId, originalTemplateId, displayTemplateId);
    }
}
