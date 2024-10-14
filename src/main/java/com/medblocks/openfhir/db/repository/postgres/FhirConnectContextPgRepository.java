package com.medblocks.openfhir.db.repository.postgres;

import com.medblocks.openfhir.db.entity.FhirConnectContextEntity;
import com.medblocks.openfhir.db.repository.FhirConnectContextRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FhirConnectContextPgRepository extends FhirConnectContextRepository, JpaRepository<FhirConnectContextEntity, String> {

    @Query("SELECT b FROM FhirConnectContextEntity b WHERE b.templateId = :templateId")
    FhirConnectContextEntity findByTemplateId(final String templateId);
}
