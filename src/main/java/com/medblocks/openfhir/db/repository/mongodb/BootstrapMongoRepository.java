package com.medblocks.openfhir.db.repository.mongodb;

import com.medblocks.openfhir.db.entity.BootstrapEntity;
import com.medblocks.openfhir.db.repository.BootstrapRepository;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BootstrapMongoRepository extends BootstrapRepository, MongoRepository<BootstrapEntity, String> {
    List<BootstrapEntity> findByFile(final String file);
}
