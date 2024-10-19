package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.model.Condition;
import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Resource;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
public abstract class OpenFhirMappingContext {
    @Getter
    Map<String, OpenFhirContextRepository> repository = new HashMap<>();

    private final FhirPathR4 fhirPathR4;
    private final OpenFhirStringUtils openFhirStringUtils;

    public OpenFhirMappingContext(FhirPathR4 fhirPathR4, OpenFhirStringUtils openFhirStringUtils) {
        this.fhirPathR4 = fhirPathR4;
        this.openFhirStringUtils = openFhirStringUtils;
    }

    /**
     * Returns a fhir connect model mapper for a specific archetype within a template.
     */
    public List<FhirConnectMapper> getMapperForArchetype(final String templateId, final String archetypeId) {
        final OpenFhirContextRepository repoForTemplate = repository.get(normalizeTemplateId(templateId));
        if (repoForTemplate == null) {
            log.warn("No repo exists for template: {}", templateId);
            return null;
        }
        final List<FhirConnectMapper> fhirConnectMapper = repoForTemplate.getMappers().get(archetypeId);
        if (fhirConnectMapper == null) {
            return null;
        }
        return fhirConnectMapper.stream().map(map -> map.copy()).collect(Collectors.toList());
    }

    /**
     * Returns a fhir connect model mapper for a specific archetype within a template. It retrieves from a slotArchetype
     * repository cache instead of from the main one.
     */
    public List<FhirConnectMapper> getSlotMapperForArchetype(final String templateId, final String archetypeId) {
        final OpenFhirContextRepository repoForTemplate = repository.get(normalizeTemplateId(templateId));
        if (repoForTemplate == null) {
            log.warn("No repo exists for template: {}", templateId);
            return null;
        }
        final List<FhirConnectMapper> fhirConnectMapper = repoForTemplate.getSlotMappers().get(archetypeId);
        if (fhirConnectMapper == null) {
            return null;
        }
        return fhirConnectMapper.stream().map(map -> map.copy()).collect(Collectors.toList());
    }

    /**
     * Finds specific mappers for a Resource then used in a FHIR to openEHR mapping, where an additional business logic
     * needs to be used to find correct context and model mappers, whereas when mapping from openEHR to FHIR, you can simply
     * do this based on an archetypeId/templateId.
     * <p>
     * In this case of a FHIR to openEHR mapping, the following business logic is applied when finding the correct mappers:
     * <p>
     * - iterate over all available model mappers within a context (the right context has been defined beforehand)
     * - evaluate a fhirConfig.condition on the Resource
     * - if fhirpath evaluation returns a result, it means this specific mapping in question is the right one
     * - if no condition is present and the mere fhir resource type matches the incoming Resource, it also adds it to the
     * available one
     *
     * @param resource incoming Resource that is to be mapped
     * @return a list of relevant FhirConnectMappers for the incoming FHIR Resource
     */
    public List<FhirConnectMapper> getMapperForResource(final Resource resource) {
        final List<FhirConnectMapper> relevantMappers = new ArrayList<>();
        final Set<Map.Entry<String, OpenFhirContextRepository>> repos = repository.entrySet();
        for (Map.Entry<String, OpenFhirContextRepository> repo : repos) {
            final OpenFhirContextRepository specificRepo = repo.getValue();
            final Map<String, List<FhirConnectMapper>> mappers = specificRepo.getMappers();
            for (Map.Entry<String, List<FhirConnectMapper>> mapperEntry : mappers.entrySet()) {
                final List<FhirConnectMapper> connectMappers = mapperEntry.getValue();
                for (FhirConnectMapper connectMapper : connectMappers) {
                    if (connectMapper.getFhirConfig() == null) {
                        continue;
                    }
                    final List<Condition> conditions = connectMapper.getFhirConfig().getCondition();
                    final String fhirPathWithCondition = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                            conditions, connectMapper.getFhirConfig().getResource());
                    if (StringUtils.isEmpty(fhirPathWithCondition) || fhirPathWithCondition.equals(connectMapper.getFhirConfig().getResource())) {
                        log.warn("No fhirpath defined for resource type, mapper relevant for all Resources of this type?");
                        relevantMappers.add(connectMapper.copy()); // IMPORTANT! as a mapper is being edited as part of the mapping process, this needs to be copied!
                    } else {
                        final Optional<Base> evaluated = fhirPathR4.evaluateFirst(resource, fhirPathWithCondition, Base.class);
                        // if is present and is of type boolean, it also needs to be true
                        // if is present and is not of type boolean, then the mere presence means the mapper is for this resource
                        if (evaluated.isPresent() && ((!(evaluated.get() instanceof BooleanType) || ((BooleanType) evaluated.get()).getValue()))) {
                            // mapper matches this Resource, it can handle it
                            relevantMappers.add(connectMapper.copy()); // IMPORTANT! as a mapper is being edited as part of the mapping process, this needs to be copied!
                        }
                    }
                }
            }
        }
        if (relevantMappers.isEmpty()) {
            log.error("No mappers found for Resource: {}, id: {}", resource.getResourceType().name(), resource.getId());
            return null;
        }
        if (relevantMappers.size() > 1) {
            log.info("More than one mapper found for Resource: {}, id: {}", resource.getResourceType().name(), resource.getId());
        }
        return relevantMappers;
    }

    public static String normalizeTemplateId(final String templateId) {
        return templateId.toLowerCase().replace(" ", "_");
    }
}
