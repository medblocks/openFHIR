package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.FhirConnectMapperEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FhirConnectMapperRepository extends MongoRepository<FhirConnectMapperEntity, String> {

    @Query("{'fhirConnectMapper.openEhrConfig.archetype': { $in: ?0 }}")
    List<FhirConnectMapperEntity> findByArchetype(final List<String> archetype);
}
