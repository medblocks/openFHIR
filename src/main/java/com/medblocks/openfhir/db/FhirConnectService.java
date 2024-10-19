package com.medblocks.openfhir.db;

import com.medblocks.openfhir.db.entity.FhirConnectContextEntity;
import com.medblocks.openfhir.db.entity.FhirConnectMapperEntity;
import com.medblocks.openfhir.db.repository.FhirConnectContextRepository;
import com.medblocks.openfhir.db.repository.FhirConnectMapperRepository;
import com.medblocks.openfhir.fc.model.FhirConnectContext;
import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import com.medblocks.openfhir.rest.RequestValidationException;
import com.medblocks.openfhir.util.FhirConnectValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@Transactional
public class FhirConnectService {

    private final FhirConnectMapperRepository mapperRepository;
    private final FhirConnectContextRepository contextRepository;
    private final FhirConnectValidator validator;

    @Autowired
    public FhirConnectService(FhirConnectMapperRepository mapperRepository,
                              FhirConnectContextRepository contextRepository,
                              FhirConnectValidator validator) {
        this.mapperRepository = mapperRepository;
        this.contextRepository = contextRepository;
        this.validator = validator;
    }

    /**
     * Creates a model mapper based on FHIR Connect specification.
     *
     * @param body YAML payload as per model-mapping.schema.json
     * @return created Model Mapper populated with database assigned attributes (namely id)
     * @throws RequestValidationException if incoming BODY is not according to model-mapping json schema
     * @throws RequestValidationException if FHIR Paths within the mappers are not valid FHIR Paths
     */
    public FhirConnectMapperEntity upsertModelMapper(final String body, final String id, final String reqId) {
        log.debug("Receive CREATE/UPDATE FhirConnectMapper, id {}, reqId: {}", id, reqId);
        try {
            final Yaml yaml = new Yaml();
            final FhirConnectMapper fhirConnectMapper = yaml.loadAs(body, FhirConnectMapper.class);

            final List<String> strings = validator.validateAgainstModelSchema(fhirConnectMapper);
            if (strings != null && !strings.isEmpty()) {
                log.error("[{}] Error occurred trying to validate connect mapper against the schema. Nothing has been created.", reqId);
                throw new RequestValidationException("Couldn't validate against the yaml schema", strings);
            }

            final List<String> semanticErrors = validator.validateFhirConnectMapper(fhirConnectMapper);
            if (semanticErrors != null && !semanticErrors.isEmpty()) {
                log.error("[{}] Error occurred trying to validate semantic correctness of the mapper.", reqId);
                throw new RequestValidationException("Error occurred trying to validate semantic correctness of the mapper,", semanticErrors);
            }

            final FhirConnectMapperEntity build = FhirConnectMapperEntity.builder()
                    .fhirConnectMapper(fhirConnectMapper)
                    .id(StringUtils.isBlank(id) ? null : id)
                    .build();
            final FhirConnectMapperEntity saved = mapperRepository.save(build);
            saved.setFhirConnectMapper(fhirConnectMapper); // unless we do this, when postgres is used, this will be empty in response
            return saved;
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create a FhirConnectMapper, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a FhirConnectMapper. Invalid one.");
        }
    }

    public FhirConnectContextEntity findContextMapperByTemplate(final String normalizedTemplateId) {
        return contextRepository.findByTemplateId(normalizedTemplateId);
    }

    /**
     * Creates a context mapper based on FHIR Connect specification.
     *
     * @param body YAML payload as per contextual-mapping.schema.json
     * @return created Context Mapper populated with database assigned attributes (namely id)
     * @throws RequestValidationException if incoming BODY is not according to contextual-mapping json schema
     * @throws IllegalArgumentException   if a context mapper fot the given template already exists (there can only be
     *                                    one context mapper for a specific template id)
     */
    public FhirConnectContextEntity upsertContextMapper(final String body, final String id, final String reqId) {

        log.debug("Receive CREATE/UPDATE FhirConnectContext, id {}, reqId: {}", id, reqId);
        try {
            final Yaml yaml = new Yaml();
            final FhirConnectContext fhirContext = yaml.loadAs(body, FhirConnectContext.class);

            final List<String> strings = validator.validateAgainstContextSchema(fhirContext);
            if (strings != null && !strings.isEmpty()) {
                log.error("[{}] Error occurred trying to validate connect context mapper against the schema. Nothing has been created.", reqId);
                throw new RequestValidationException("Couldn't validate against the yaml schema", strings);
            }

            final FhirConnectContextEntity build = FhirConnectContextEntity.builder()
                    .fhirConnectContext(fhirContext)
                    .id(StringUtils.isBlank(id) ? null : id)
                    .build();

            // only if the same one for that template id doesn't already exist!!
            if (StringUtils.isBlank(id) && contextRepository.findByTemplateId(fhirContext.getOpenEHR().getTemplateId()) != null) {
                log.error("[{}] A context mapper for this templateId {} already exists.", reqId, fhirContext.getOpenEHR().getTemplateId());
                throw new RequestValidationException("Couldn't create a FhirConnectContext. Invalid one.", Arrays.asList("A context mapper for this template already exists."));
            }
            final FhirConnectContextEntity saved = contextRepository.save(build);
            saved.setFhirConnectContext(fhirContext); // unless we do this, when postgres is used, this will be empty in response
            return saved;
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create/update a FhirConnectContext, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a FhirConnectContext. Invalid one.");
        }
    }

    public List<FhirConnectMapperEntity> all(final String reqId) {
        return mapperRepository.findAll();
    }

    public List<FhirConnectMapperEntity> findByUserAndArchetypes(final List<String> archetypes, final String reqId) {
        return mapperRepository.findByArchetype(archetypes);
    }

}
