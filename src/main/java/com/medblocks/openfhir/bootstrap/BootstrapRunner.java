package com.medblocks.openfhir.bootstrap;

import com.medblocks.openfhir.db.FhirConnectService;
import com.medblocks.openfhir.db.OptService;
import com.medblocks.openfhir.db.entity.BootstrapEntity;
import com.medblocks.openfhir.db.repository.BootstrapRepository;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BootstrapRunner implements ApplicationRunner {

    @Value("${bootstrap.dir:/app/bootstrap/}")
    private String bootstrapDir;

    final String MODEL_SUFFIX = "model.yaml";
    final String MODEL_SUFFIX2 = "model.yml";

    final String CONTEXT_SUFFIX = "context.yaml";
    final String CONTEXT_SUFFIX2 = "context.yml";
    final String OPT_SUFFIX = ".opt";
    final String CONCEPTMAP_SUFFIX = ".json";

    private final BootstrapRepository bootstrapRepository;
    private final FhirConnectService service;
    private final OptService optService;

    @Autowired
    public BootstrapRunner(final BootstrapRepository bootstrapRepository,
                           final FhirConnectService service,
                           final OptService optService) {
        this.bootstrapRepository = bootstrapRepository;
        this.service = service;
        this.optService = optService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (bootstrapDir == null) {
            log.debug("No bootstrap dir defined.");
            return;
        }
        if (!new File(bootstrapDir).isDirectory()) {
            log.warn("Defined bootstrap directory '{}' is not a directory.", bootstrapDir);
            return;
        }
        if (new File(bootstrapDir).listFiles() == null) {
            log.warn("No files in the bootstrap dir. Aborting.");
            return;
        }
        for (final File file : Objects.requireNonNull(new File(bootstrapDir).listFiles())) {
            final String fileName = file.getName();
            if (fileName.isEmpty()) {
                continue;
            }
            if (fileName.endsWith(MODEL_SUFFIX) || fileName.endsWith(MODEL_SUFFIX2)) {
                log.info("Found a file '{}' that matches model suffix", fileName);
                runIfNotRanYet(file, FileType.MODEL);
            } else if (fileName.endsWith(CONTEXT_SUFFIX) || fileName.endsWith(CONTEXT_SUFFIX2)) {
                log.info("Found a file '{}' that matches context suffix", fileName);
                runIfNotRanYet(file, FileType.CONTEXT);
            } else if (fileName.endsWith(OPT_SUFFIX)) {
                log.info("Found a file '{}' that matches opt suffix", fileName);
                runIfNotRanYet(file, FileType.OPT);
            } else if (fileName.endsWith(CONCEPTMAP_SUFFIX)) {
                log.info("Found a file '{}' that matches concept map suffix", fileName);
                runIfNotRanYet(file, FileType.CONCEPTMAP);
            } else {
                log.warn("Found file '{}' doesn't match any pattern. If you want it to be bootstrapped, it has to end with {} or {} or {} or {}", fileName, MODEL_SUFFIX, CONTEXT_SUFFIX, OPT_SUFFIX, CONCEPTMAP_SUFFIX);
            }
        }
    }

    private void runIfNotRanYet(final File file, final FileType fileType) {
        final String fileName = file.getName();
        final List<BootstrapEntity> byFile = bootstrapRepository.findByFile(fileName);
        if (byFile != null && !byFile.isEmpty()) {
            log.info("File {} already bootstrapped at {}. Skipping.", fileName, byFile.get(0).getDate());
            return;
        }
        try {
            final String fileContents = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

            if (fileType == FileType.MODEL) {
                log.info("Creating model file {} from bootstrap.", fileName);
                service.upsertModelMapper(fileContents, null, "bootstrap-req");
            } else if (fileType == FileType.CONTEXT) {
                log.info("Creating context file {} from bootstrap.", fileName);
                service.upsertContextMapper(fileContents, null, "bootstrap-req");
            } else if (fileType == FileType.OPT) {
                log.info("Creating OPT file {} from bootstrap.", fileName);
                optService.upsert(fileContents, null, "bootstrap-req");
            }

            bootstrapRepository.save(new BootstrapEntity(UUID.randomUUID().toString(),
                    fileName,
                    new java.util.Date()));
        } catch (final Exception e) {
            log.error("File {} couldn't be bootstrapped due to ", file, e);
        }
    }

    enum FileType {
        MODEL, CONTEXT, OPT, CONCEPTMAP
    }
}
