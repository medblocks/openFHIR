package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.FhirConnectMapperEntity;

import java.util.List;

public interface FhirConnectMapperRepository {

    List<FhirConnectMapperEntity> findByArchetype(final List<String> archetype);
    List<FhirConnectMapperEntity> findAll();

    FhirConnectMapperEntity save(final FhirConnectMapperEntity entity);

    void deleteAll();
}
