package com.medblocks.openfhir.db.entity;

import com.medblocks.openfhir.fc.model.FhirConnectContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FhirConnectContextEntity {
    String id;
    FhirConnectContext fhirConnectContext;
}
