package com.medblocks.openfhir.db.repository.postgres;

import com.medblocks.openfhir.db.entity.OptEntity;
import com.medblocks.openfhir.db.repository.OptRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface OptPgRepository extends OptRepository, JpaRepository<OptEntity, String> {

    @Query("SELECT o FROM OptEntity o WHERE o.templateId = :templateId")
    OptEntity findByTemplateId(@Param("templateId") final String templateId);
}
