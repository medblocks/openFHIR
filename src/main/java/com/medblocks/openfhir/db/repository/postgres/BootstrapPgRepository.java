package com.medblocks.openfhir.db.repository.postgres;

import com.medblocks.openfhir.db.entity.BootstrapEntity;
import com.medblocks.openfhir.db.repository.BootstrapRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BootstrapPgRepository extends BootstrapRepository, JpaRepository<BootstrapEntity, String> {

    @Query("SELECT b FROM BootstrapEntity b WHERE b.file = :file")
    List<BootstrapEntity> findByFile(final String file);
}
