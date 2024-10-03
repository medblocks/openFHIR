package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.FhirConnectContextEntity;

import java.util.List;

public interface FhirConnectContextRepository {
    FhirConnectContextEntity findByTemplateId(final String templateId);
    List<FhirConnectContextEntity> findAll();

    FhirConnectContextEntity save(final FhirConnectContextEntity entity);

    void deleteAll();
}
