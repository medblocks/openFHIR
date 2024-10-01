package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.FhirConnectContextEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface FhirConnectContextRepository extends MongoRepository<FhirConnectContextEntity, String> {

    @Query("{'fhirConnectContext.openEHR.templateId': ?0}")
    FhirConnectContextEntity findByTemplateId(final String templateId);
}
