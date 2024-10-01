package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.OptEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface OptRepository extends MongoRepository<OptEntity, String> {
    @Query(fields = "{ 'content' : 0 }")
    List<OptEntity> findEmptyContent();
    OptEntity findByTemplateId(final String templateId);
}
