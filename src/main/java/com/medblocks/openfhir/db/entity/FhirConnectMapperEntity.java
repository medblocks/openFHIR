package com.medblocks.openfhir.db.entity;

import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FhirConnectMapperEntity {
    String id;
    FhirConnectMapper fhirConnectMapper;
}
