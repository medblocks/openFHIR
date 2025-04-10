package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.FhirConnectModelEntity;

import com.medblocks.openfhir.db.entity.OptEntity;
import java.util.List;

public interface FhirConnectModelRepository {

    List<FhirConnectModelEntity> findByArchetype(final List<String> archetype);
    List<FhirConnectModelEntity> findByName(final List<String> name);
    List<FhirConnectModelEntity> findAll();
    FhirConnectModelEntity byId(String id);

    FhirConnectModelEntity save(final FhirConnectModelEntity entity);

    void deleteAll();
}
