package com.medblocks.openfhir.db.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptEntity {
    String id;
    String content;
    String templateId;
    String originalTemplateId;
    String displayTemplateId;
}
