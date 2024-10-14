package com.medblocks.openfhir.db.repository.mongodb;

import com.medblocks.openfhir.db.entity.FhirConnectContextEntity;
import com.medblocks.openfhir.db.repository.FhirConnectContextRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FhirConnectContextMongoRepository extends FhirConnectContextRepository, MongoRepository<FhirConnectContextEntity, String> {

    @Query("{'fhirConnectContext.openEHR.templateId': ?0}")
    FhirConnectContextEntity findByTemplateId(final String templateId);
}
