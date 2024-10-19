package com.medblocks.openfhir.db.repository.mongodb;

import com.medblocks.openfhir.db.entity.OptEntity;
import com.medblocks.openfhir.db.repository.OptRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;


public interface OptMongoRepository extends OptRepository, MongoRepository<OptEntity, String> {

    @Query(value = "{}", fields = "{ 'content' : 0 }")
    List<OptEntity> searchWithEmptyContent();

    OptEntity findByTemplateId(final String templateId);
}
