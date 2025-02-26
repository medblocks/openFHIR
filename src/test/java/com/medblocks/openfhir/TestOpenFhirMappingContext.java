package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.fc.schema.Metadata;
import com.medblocks.openfhir.fc.schema.context.Context;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.util.FhirConnectModelMerger;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import com.medblocks.openfhir.util.OpenFhirTestUtility;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.yaml.snakeyaml.Yaml;

@Component
@RequestScope
@Slf4j
public class TestOpenFhirMappingContext extends OpenFhirMappingContext {


    @Autowired
    public TestOpenFhirMappingContext(final FhirPathR4 fhirPathR4,
                                      final OpenFhirStringUtils openFhirStringUtils,
                                      final FhirConnectModelMerger modelMerger) {
        super(fhirPathR4, openFhirStringUtils, modelMerger);
    }


    @Deprecated
    public void initRepository(final FhirConnectContext context, final String dir) {
        final String templateId = context.getContext().getTemplate().getId();
        final String normalizedRepoId = normalizeTemplateId(templateId);
        if (repository.containsKey(normalizedRepoId)) {
            log.info("Repository for template {} already initialized");
            return;
        }
        final OpenFhirContextRepository fhirContextRepo = new OpenFhirContextRepository();
        final boolean initialized = initRepository(fhirContextRepo, context, null, dir);

        try {
            fhirContextRepo.setOperationaltemplate(
                    TemplateDocument.Factory.parse(FileUtils.openInputStream(new File(dir + templateId + ".opt")))
                            .getTemplate());
        } catch (final Exception e) {
            log.error("", e);
        }

        if (initialized) {
            repository.put(normalizedRepoId, fhirContextRepo);
        } else {
            log.warn("Not adding repo as it wasn't properly initialized.");
        }
    }

    public void initRepository(final FhirConnectContext context,
                               final OPERATIONALTEMPLATE operationaltemplate,
                               final String modelsDir) {
        final String templateId = context.getContext().getTemplate().getId();
        final String normalizedRepoId = normalizeTemplateId(templateId);
        if (repository.containsKey(normalizedRepoId)) {
            log.info("Repository for template {} already initialized");
            return;
        }
        final OpenFhirContextRepository fhirContextRepo = new OpenFhirContextRepository();
        final boolean initialized = initRepository(fhirContextRepo, context, operationaltemplate, modelsDir);


        if (initialized) {
            repository.put(normalizedRepoId, fhirContextRepo);
        } else {
            log.warn("Not adding repo as it wasn't properly initialized.");
        }
    }

    public boolean initRepository(final OpenFhirContextRepository fhirContextRepo,
                                  final FhirConnectContext context,
                                  final OPERATIONALTEMPLATE operationaltemplate,
                                  final String modelsDir) {
        if (fhirContextRepo.getMappers() != null) {
            log.debug("Repo already initialized.");
            return false;
        }
        try {
            fhirContextRepo.setOperationaltemplate(operationaltemplate);
            fhirContextRepo.setWebTemplate(new OPTParser(fhirContextRepo.getOperationaltemplate()).parse());
            fhirContextRepo.setMappers(loadMappings(modelsDir, context));
            return true;
        } catch (final Exception e) {
            log.error("Couldn't initialize OpenFhirContextRepository", e);
            return false;
        }
    }

    private Map<String, List<OpenFhirFhirConnectModelMapper>> loadMappings(final String dir,
                                                                           final FhirConnectContext context) {
        return loadMappings(new File(dir), context);
    }

    private Map<String, List<OpenFhirFhirConnectModelMapper>> loadMappings(final File directory,
                                                                           final FhirConnectContext context) {
        Map<String, List<OpenFhirFhirConnectModelMapper>> mappers = new HashMap<>();
        final List<FhirConnectModel> extensionModels = new ArrayList<>();
        final List<FhirConnectModel> coreModels = new ArrayList<>();

        loadCoreAndExtensionMappings(directory, context, extensionModels, coreModels);

        final List<OpenFhirFhirConnectModelMapper> mergedOpenFhirModelMappers = modelMerger.joinModelMappers(
                coreModels, extensionModels);

        mergedOpenFhirModelMappers.forEach(mapperEntity -> {
            final String archetype = mapperEntity.getOpenEhrConfig().getArchetype();
            final String mappingName = mapperEntity.getName();
            if (!mappers.containsKey(mappingName)) {
                mappers.put(mappingName, new ArrayList<>());
            }
            if (!mappers.containsKey(archetype) && !archetype.equals(mappingName)) {
                mappers.put(archetype, new ArrayList<>());
            }
            mappers.get(mappingName).add(mapperEntity);
            if (!archetype.equals(mappingName)) {
                mappers.get(archetype).add(mapperEntity);
            }
        });

        return mappers;
    }

    /**
     * Loops through the directory and sub-directories finding for all core and extension mappings
     *
     * @param directory currently looking in this directory
     * @param context required because in there we see what's core mapping and what's extension mapping
     * @param extensionModels list being populated with parsed extension mappings
     * @param coreModels list being populated with parsed core mappings
     */
    private void loadCoreAndExtensionMappings(final File directory,
                                              final FhirConnectContext context,
                                              final List<FhirConnectModel> extensionModels,
                                              final List<FhirConnectModel> coreModels) {
        for (final File file : directory.listFiles()) {
            if (file.isDirectory()) {
                loadCoreAndExtensionMappings(file, context, extensionModels, coreModels);
            }
            if (!isRelevantFile(file)) {
                continue;
            }
            final FhirConnectModel fhirConnectMapper = parseFile(file);
            if (fhirConnectMapper == null) {
                continue;
            }
            final Context contextMetadata = context.getContext();
            final Metadata parsedMapperMetadata = fhirConnectMapper.getMetadata();
            if (contextMetadata.getArchetypes().contains(parsedMapperMetadata.getName())) {
                coreModels.add(fhirConnectMapper);
            } else if (contextMetadata.getExtensions().contains(parsedMapperMetadata.getName())) {
                extensionModels.add(fhirConnectMapper);
            }
        }
    }

    private FhirConnectModel parseFile(final File file) {
        try {
            final FileInputStream modelInputStream = FileUtils.openInputStream(file);

            final Yaml yaml = OpenFhirTestUtility.getYaml();

            return yaml.loadAs(modelInputStream, FhirConnectModel.class);
        } catch (Exception e) {
            log.warn("Couldn't parse file: {}", file.getName(), e);
            return null;
        }
    }

    private boolean isContextMapper(final String fileName) {
        return fileName.endsWith("context.yaml") || fileName.endsWith("context.yml");
    }

    private boolean isYamlFile(final String fileName) {
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }

    private boolean isRelevantFile(final File file) {
        return !isContextMapper(file.getName()) && isYamlFile(file.getName());
    }

}
