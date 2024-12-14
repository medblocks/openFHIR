package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.BootstrapEntity;
import java.util.List;

public interface BootstrapRepository {
    List<BootstrapEntity> findByFile(final String file);

    BootstrapEntity save(final BootstrapEntity entity);
}
