package com.medblocks.openfhir.db.repository.postgres;

import com.medblocks.openfhir.db.entity.FhirConnectModelEntity;
import com.medblocks.openfhir.db.repository.FhirConnectModelRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FhirConnectModelPgRepository extends FhirConnectModelRepository, JpaRepository<FhirConnectModelEntity, String> {

    @Query("SELECT b FROM FhirConnectModelEntity b WHERE b.archetype IN (:archetype)")
    List<FhirConnectModelEntity> findByArchetype(final List<String> archetype);

    @Query("SELECT b FROM FhirConnectModelEntity b WHERE b.name IN (:name)")
    List<FhirConnectModelEntity> findByName(final List<String> name);
}
