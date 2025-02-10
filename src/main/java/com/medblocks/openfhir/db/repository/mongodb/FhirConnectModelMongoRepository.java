package com.medblocks.openfhir.db.repository.mongodb;

import com.medblocks.openfhir.db.entity.FhirConnectModelEntity;
import com.medblocks.openfhir.db.repository.FhirConnectModelRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FhirConnectModelMongoRepository extends FhirConnectModelRepository, MongoRepository<FhirConnectModelEntity, String> {
    @Query("{'fhirConnectModel.openEhrConfig.archetype': { $in: ?0 }}")
    List<FhirConnectModelEntity> findByArchetype(final List<String> archetype);

    @Query("{'fhirConnectModel.metadata.name': { $in: ?0 }}")
    List<FhirConnectModelEntity> findByName(final List<String> name);
}
