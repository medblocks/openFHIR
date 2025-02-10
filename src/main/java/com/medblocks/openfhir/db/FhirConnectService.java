package com.medblocks.openfhir.db;

import com.medblocks.openfhir.db.entity.FhirConnectContextEntity;
import com.medblocks.openfhir.db.entity.FhirConnectModelEntity;
import com.medblocks.openfhir.db.repository.FhirConnectContextRepository;
import com.medblocks.openfhir.db.repository.FhirConnectModelRepository;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.rest.RequestValidationException;
import com.medblocks.openfhir.util.FhirConnectValidator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

@Component
@Slf4j
@Transactional
public class FhirConnectService {

    private final FhirConnectModelRepository mapperRepository;
    private final FhirConnectContextRepository contextRepository;
    private final FhirConnectValidator validator;
    private final Yaml yamlParser;

    @Autowired
    public FhirConnectService(FhirConnectModelRepository mapperRepository,
                              FhirConnectContextRepository contextRepository,
                              FhirConnectValidator validator,
                              final Yaml yamlParser) {
        this.mapperRepository = mapperRepository;
        this.contextRepository = contextRepository;
        this.validator = validator;
        this.yamlParser = yamlParser;
    }

    /**
     * Creates a model mapper based on FHIR Connect specification.
     *
     * @param body YAML payload as per model-mapping.schema.json
     * @return created Model Mapper populated with database assigned attributes (namely id)
     * @throws RequestValidationException if incoming BODY is not according to model-mapping json schema
     * @throws RequestValidationException if FHIR Paths within the mappers are not valid FHIR Paths
     */
    public FhirConnectModelEntity upsertModelMapper(final String body, final String id, final String reqId) {
        log.debug("Receive CREATE/UPDATE FhirConnectModel, id {}, reqId: {}", id, reqId);
        try {
            final FhirConnectModel fhirConnectModel = yamlParser.loadAs(body, FhirConnectModel.class);

            final List<String> strings = validator.validateAgainstModelSchema(fhirConnectModel);
            if (strings != null && !strings.isEmpty()) {
                log.error(
                        "[{}] Error occurred trying to validate FC model mapper against the schema. Nothing has been created. Errors: {}",
                        reqId, strings);
                throw new RequestValidationException("Couldn't validate against the yaml schema", strings);
            }

            final List<String> semanticErrors = validator.validateFhirConnectModel(fhirConnectModel);
            if (semanticErrors != null && !semanticErrors.isEmpty()) {
                log.error("[{}] Error occurred trying to validate semantic correctness of the mapper.", reqId);
                throw new RequestValidationException(
                        "Error occurred trying to validate semantic correctness of the mapper,", semanticErrors);
            }

            final FhirConnectModelEntity build = FhirConnectModelEntity.builder()
                    .fhirConnectModel(fhirConnectModel)
                    .id(StringUtils.isBlank(id) ? null : id)
                    .build();
            final FhirConnectModelEntity saved = mapperRepository.save(build);
            saved.setFhirConnectModel(
                    fhirConnectModel); // unless we do this, when postgres is used, this will be empty in response
            return saved;
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create a FhirConnectModel, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a FhirConnectModel. Invalid one.", e);
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
     * @throws IllegalArgumentException if a context mapper fot the given template already exists (there can
     *         only be
     *         one context mapper for a specific template id)
     */
    public FhirConnectContextEntity upsertContextMapper(final String body, final String id, final String reqId) {

        log.debug("Receive CREATE/UPDATE FhirConnectContext, id {}, reqId: {}", id, reqId);
        try {
            final FhirConnectContext fhirContext = yamlParser.loadAs(body, FhirConnectContext.class);

            final List<String> strings = validator.validateAgainstContextSchema(fhirContext);
            if (strings != null && !strings.isEmpty()) {
                log.error(
                        "[{}] Error occurred trying to validate connect context mapper against the schema. Nothing has been created. Errors: {}",
                        reqId, strings);
                throw new RequestValidationException("Couldn't validate against the yaml schema", strings);
            }

            final FhirConnectContextEntity build = FhirConnectContextEntity.builder()
                    .fhirConnectContext(fhirContext)
                    .id(StringUtils.isBlank(id) ? null : id)
                    .build();

            // only if the same one for that template id doesn't already exist!!
            if (StringUtils.isBlank(id)
                    && contextRepository.findByTemplateId(fhirContext.getContext().getTemplateId()) != null) {
                log.error("[{}] A context mapper for this templateId {} already exists.", reqId,
                          fhirContext.getContext().getTemplateId());
                throw new RequestValidationException("Couldn't create a FhirConnectContext. Invalid one.",
                                                     List.of("A context mapper for this template already exists."));
            }
            final FhirConnectContextEntity saved = contextRepository.save(build);
            saved.setFhirConnectContext(
                    fhirContext); // unless we do this, when postgres is used, this will be empty in response
            return saved;
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create/update a FhirConnectContext, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a FhirConnectContext. Invalid one.", e);
        }
    }

    public List<FhirConnectModelEntity> all(final String reqId) {
        return mapperRepository.findAll();
    }

}
