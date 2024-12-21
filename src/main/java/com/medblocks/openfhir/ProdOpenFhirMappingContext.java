package com.medblocks.openfhir;

import com.medblocks.openfhir.db.entity.FhirConnectModelEntity;
import com.medblocks.openfhir.db.repository.FhirConnectModelRepository;
import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.fc.schema.context.Context;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.util.FhirConnectModelMerger;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * RequestScoped cache of all the needed information for mapping (Context mappers, Model mappers and parsed
 * OPERATIONALTEMPLATE and WebTemplate. This is requested multiple time throughout the mapping but except for the first
 * time, should only be taken from cache directly to avoid performance issues
 */
@Component
@RequestScope
@Slf4j
public class ProdOpenFhirMappingContext extends OpenFhirMappingContext {

    private FhirConnectModelRepository fhirConnectModelRepository;
    private FhirConnectModelMerger modelMerger;

    @Autowired
    public ProdOpenFhirMappingContext(final FhirPathR4 fhirPathR4,
                                      final OpenFhirStringUtils openFhirStringUtils,
                                      final FhirConnectModelRepository fhirConnectModelRepository,
                                      final FhirConnectModelMerger modelMerger) {
        super(fhirPathR4, openFhirStringUtils);
        this.fhirConnectModelRepository = fhirConnectModelRepository;
        this.modelMerger = modelMerger;
    }

    public void initMappingCache(final FhirConnectContext context,
                                 final OPERATIONALTEMPLATE operationaltemplate,
                                 final WebTemplate webTemplate) {
        final String templateId = context.getContext().getTemplateId();
        final String normalizedRepoId = normalizeTemplateId(templateId);
        if (repository.containsKey(normalizedRepoId)) {
            log.info("Repository for template {} already initialized", normalizedRepoId);
            return;
        }
        final OpenFhirContextRepository fhirContextRepo = new OpenFhirContextRepository();
        fhirContextRepo.setOperationaltemplate(operationaltemplate);
        fhirContextRepo.setWebTemplate(webTemplate);

        final List<OpenFhirFhirConnectModelMapper> openFhirFhirConnectModelMappers = prepareJoinedModels(
                context.getContext());

        final Map<String, List<OpenFhirFhirConnectModelMapper>> mappers = new HashMap<>();
        final Map<String, List<OpenFhirFhirConnectModelMapper>> slotMappers = new HashMap<>();

        openFhirFhirConnectModelMappers.forEach(mapperEntity -> {
            final String archetype = mapperEntity.getOpenEhrConfig().getArchetype();
            if (mapperEntity.getFhirConfig() == null) {
                if (!slotMappers.containsKey(archetype)) {
                    slotMappers.put(archetype,
                                    new ArrayList<>()); // todo: the fact there is no fhirConfig no longer means it's a slot archetype.. however maybe we don't need to differentiate between them anymore
                }
                slotMappers.get(archetype).add(mapperEntity);
            } else {
                if (!mappers.containsKey(archetype)) {
                    mappers.put(archetype, new ArrayList<>());
                }
                mappers.get(archetype).add(mapperEntity);
            }

        });

        fhirContextRepo.setMappers(mappers);
        fhirContextRepo.setSlotMappers(slotMappers);

        repository.put(normalizedRepoId, fhirContextRepo);
    }

    private List<OpenFhirFhirConnectModelMapper> prepareJoinedModels(final Context context) {
        // now load mappings
        final List<FhirConnectModelEntity> modelEntities = fhirConnectModelRepository.findByName(
                context.getArchetypes());
        if (modelEntities == null || modelEntities.isEmpty()) {
            log.error("Couldn't find any model entities that would match {}", context.getArchetypes());
            throw new IllegalArgumentException("Couldn't find any model entities for this template.");
        }

        final List<FhirConnectModel> vanillaModels = modelEntities.stream()
                .map(FhirConnectModelEntity::getFhirConnectModel)
                .collect(Collectors.toList());
        final List<FhirConnectModel> extensionsModels = loadExtensions(context.getExtensions());
        return modelMerger.joinModelMappers(vanillaModels, extensionsModels);
    }

    private List<FhirConnectModel> loadExtensions(final List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            log.debug("No extensions defined.");
            return null;
        }
        final List<FhirConnectModelEntity> extensionEntities = fhirConnectModelRepository.findByName(extensions);
        if (extensionEntities == null || extensionEntities.isEmpty()) {
            log.error("Couldn't find extension model mappers ({}) in the database.", extensions);
            throw new IllegalArgumentException("Couldn't find defined extension model mappers in the database.");
        }
        return extensionEntities.stream()
                .map(FhirConnectModelEntity::getFhirConnectModel)
                .collect(Collectors.toList());
    }

}
