package com.medblocks.openfhir.db.repository.postgres;

import com.medblocks.openfhir.db.entity.FhirConnectMapperEntity;
import com.medblocks.openfhir.db.repository.FhirConnectMapperRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FhirConnectMapperPgRepository extends FhirConnectMapperRepository, JpaRepository<FhirConnectMapperEntity, String> {

    @Query("SELECT b FROM FhirConnectMapperEntity b WHERE b.archetype IN (:archetype)")
    List<FhirConnectMapperEntity> findByArchetype(final List<String> archetype);
}
