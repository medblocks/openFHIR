package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component // rethink, should be per user prolly?
@RequestScope
@Slf4j
public class TestOpenFhirMappingContext extends OpenFhirMappingContext {
    @Autowired
    public TestOpenFhirMappingContext(final FhirPathR4 fhirPathR4,
                                      final OpenFhirStringUtils openFhirStringUtils) {
        super(fhirPathR4, openFhirStringUtils);
    }

    public void initRepository(final FhirConnectContext context, final String dir) {
        final String templateId = context.getContext().getTemplateId();
        final String normalizedRepoId = normalizeTemplateId(templateId);
        if (repository.containsKey(normalizedRepoId)) {
            log.info("Repository for template {} already initialized");
            return;
        }
        final OpenFhirContextRepository fhirContextRepo = new OpenFhirContextRepository();
        final boolean initialized = initRepository(fhirContextRepo, context, dir);


        if (initialized) {
            repository.put(normalizedRepoId, fhirContextRepo);
        } else {
            log.warn("Not adding repo as it wasn't properly initialized.");
        }
    }

    public boolean initRepository(final OpenFhirContextRepository fhirContextRepo, final FhirConnectContext context, final String dir) {
        if (fhirContextRepo.getMappers() != null) {
            log.debug("Repo already initialized.");
            return false;
        }
        final String templateId = context.getContext().getTemplateId();
        try {
            fhirContextRepo.setOperationaltemplate(TemplateDocument.Factory.parse(FileUtils.openInputStream(new File(dir + templateId + ".opt"))).getTemplate());
            fhirContextRepo.setWebTemplate(new OPTParser(fhirContextRepo.getOperationaltemplate()).parse());
            fhirContextRepo.setMappers(loadMappings(dir, context, false));
            fhirContextRepo.setSlotMappers(loadMappings(dir, context, true));
            return true;
        } catch (final Exception e) {
            log.error("Couldn't initialize OpenFhirContextRepository", e);
            return false;
        }
    }

    private Map<String, List<OpenFhirFhirConnectModelMapper>> loadMappings(final String dir,
                                                                           final FhirConnectContext context,
                                                                           boolean slot) {
        Map<String, List<OpenFhirFhirConnectModelMapper>> mappers = new HashMap<>();
        for (File file : new File(dir).listFiles()) {
            if (file.getName().endsWith(".model.yml") || file.getName().endsWith(".model.yaml")) {
                try {
                    final FileInputStream modelInputStream = FileUtils.openInputStream(file);
                    final Representer representer = new Representer(new DumperOptions());
                    representer.getPropertyUtils().setSkipMissingProperties(true);

                    final Yaml yaml = new Yaml(representer);

                    final OpenFhirFhirConnectModelMapper fhirConnectMapper = yaml.loadAs(modelInputStream, OpenFhirFhirConnectModelMapper.class);
                    if (context.getContext().getArchetypes().contains(fhirConnectMapper.getOpenEhrConfig().getArchetype())) {
                        final String arch = fhirConnectMapper.getOpenEhrConfig().getArchetype();
                        if(!slot || fhirConnectMapper.getFhirConfig() == null) {
                            if (!mappers.containsKey(arch)) {
                                mappers.put(arch, new ArrayList<>());
                            }
                            mappers.get(fhirConnectMapper.getOpenEhrConfig().getArchetype()).add(fhirConnectMapper);
                        }

                    }
                } catch (Exception e) {
                    log.warn("Couldn't open file: {}", file.getName(), e);
                }
            }
        }
        return mappers;
    }

}
