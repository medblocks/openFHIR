package com.medblocks.openfhir;

import com.medblocks.openfhir.db.entity.FhirConnectMapperEntity;
import com.medblocks.openfhir.db.repository.FhirConnectMapperRepository;
import com.medblocks.openfhir.fc.model.FhirConnectContext;
import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RequestScoped cache of all the needed information for mapping (Context mappers, Model mappers and parsed
 * OPERATIONALTEMPLATE and WebTemplate. This is requested multiple time throughout the mapping but except for the first
 * time, should only be taken from cache directly to avoid performance issues
 */
@Component
@RequestScope
@Slf4j
public class ProdOpenFhirMappingContext extends OpenFhirMappingContext {

    private FhirConnectMapperRepository connectMapperRepository;

    @Autowired
    public ProdOpenFhirMappingContext(final FhirPathR4 fhirPathR4,
                                      final OpenFhirStringUtils openFhirStringUtils,
                                      final FhirConnectMapperRepository connectMapperRepository) {
        super(fhirPathR4, openFhirStringUtils);
        this.connectMapperRepository = connectMapperRepository;
    }

    public void initMappingCache(final FhirConnectContext context,
                                 final OPERATIONALTEMPLATE operationaltemplate,
                                 final WebTemplate webTemplate) {
        final String templateId = context.getOpenEHR().getTemplateId();
        final String normalizedRepoId = normalizeTemplateId(templateId);
        if (repository.containsKey(normalizedRepoId)) {
            log.info("Repository for template {} already initialized", normalizedRepoId);
            return;
        }
        final OpenFhirContextRepository fhirContextRepo = new OpenFhirContextRepository();
        fhirContextRepo.setOperationaltemplate(operationaltemplate);
        fhirContextRepo.setWebTemplate(webTemplate);

        // now load mappings
        final List<FhirConnectMapperEntity> mapperEntities = connectMapperRepository.findByArchetype(context.getOpenEHR().getArchetypes());
        final Map<String, List<FhirConnectMapper>> mappers = new HashMap<>();
        final Map<String, List<FhirConnectMapper>> slotMappers = new HashMap<>();
        mapperEntities.forEach(mapperEntity -> {
            final String archetype = mapperEntity.getFhirConnectMapper().getOpenEhrConfig().getArchetype();
            if(mapperEntity.getFhirConnectMapper().getFhirConfig() == null) {
                if (!slotMappers.containsKey(archetype)) {
                    slotMappers.put(archetype, new ArrayList<>());
                }
                slotMappers.get(archetype).add(mapperEntity.getFhirConnectMapper());
            }else {
                if (!mappers.containsKey(archetype)) {
                    mappers.put(archetype, new ArrayList<>());
                }
                mappers.get(archetype).add(mapperEntity.getFhirConnectMapper());
            }

        });
        fhirContextRepo.setMappers(mappers);
        fhirContextRepo.setSlotMappers(slotMappers);

        repository.put(normalizedRepoId, fhirContextRepo);
    }


}
