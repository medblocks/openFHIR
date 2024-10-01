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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class FhirConnectService {

    @Autowired
    private FhirConnectMapperRepository mapperRepository;

    @Autowired
    private FhirConnectContextRepository contextRepository;

    @Autowired
    private FhirConnectValidator validator;

    /**
     * Creates a model mapper based on FHIR Connect specification.
     *
     * @param body YAML payload as per model-mapping.schema.json
     * @return created Model Mapper populated with database assigned attributes (namely id)
     * @throws RequestValidationException if incoming BODY is not according to model-mapping json schema
     * @throws RequestValidationException if FHIR Paths within the mappers are not valid FHIR Paths
     */
    public FhirConnectMapperEntity createModelMapper(final String body, final String reqId) {
        log.debug("Receive CREATE FhirConnectMapper, reqId: {}", reqId);
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
                    .id(UUID.randomUUID().toString())
                    .build();
            return mapperRepository.insert(build);
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
    public FhirConnectContextEntity createContextMapper(final String body, final String reqId) {

        log.debug("Receive CREATE FhirConnectContext, reqId: {}", reqId);
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
                    .id(UUID.randomUUID().toString())
                    .build();

            // only if the same one for that template id doesn't already exist!!
            if (contextRepository.findByTemplateId(fhirContext.getOpenEHR().getTemplateId()) != null) {
                log.error("[{}] A context mapper for this templateId {} already exists.", reqId, fhirContext.getOpenEHR().getTemplateId());
                throw new IllegalArgumentException("Couldn't create a FhirConnectContext. Invalid one.");
            }
            return contextRepository.insert(build);
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create a FhirConnectContext, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a FhirConnectContext. Invalid one.");
        }
    }

    public List<FhirConnectMapperEntity> all() {
        return mapperRepository.findAll();
    }

}
