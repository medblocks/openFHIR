package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.entity.OptEntity;

import java.util.List;

public interface OptRepository {


    OptEntity findByTemplateId(final String templateId);
    List<OptEntity> findAll();

    OptEntity save(OptEntity entity);

    void deleteAll();
}
