package com.medblocks.openfhir;

import ca.uhn.fhir.parser.JsonParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.medblocks.openfhir.db.entity.FhirConnectContextEntity;
import com.medblocks.openfhir.db.repository.FhirConnectContextRepository;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.model.Condition;
import com.medblocks.openfhir.tofhir.OpenEhrToFhir;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhr;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Component
@Slf4j
public class OpenFhirEngine {

    private FhirToOpenEhr fhirToOpenEhr;
    private OpenEhrToFhir openEhrToFhir;
    private FhirConnectContextRepository fhirConnectContextRepository;
    private JsonParser jsonParser;
    private OpenEhrCachedUtils cachedUtils;
    private FlatJsonUnmarshaller flatJsonUnmarshaller;
    private ProdOpenFhirMappingContext prodOpenFhirMappingContext;
    private OpenFhirStringUtils openFhirStringUtils;
    private FhirPathR4 fhirPathR4;
    private Gson gson;

    @Autowired
    public OpenFhirEngine(final FhirToOpenEhr fhirToOpenEhr,
                          final OpenEhrToFhir openEhrToFhir,
                          final FhirConnectContextRepository fhirConnectContextRepository,
                          final JsonParser jsonParser,
                          final OpenEhrCachedUtils cachedUtils,
                          final FlatJsonUnmarshaller flatJsonUnmarshaller,
                          final ProdOpenFhirMappingContext prodOpenFhirMappingContext,
                          final OpenFhirStringUtils openFhirStringUtils,
                          final FhirPathR4 fhirPathR4,
                          final Gson gson) {
        this.fhirToOpenEhr = fhirToOpenEhr;
        this.openEhrToFhir = openEhrToFhir;
        this.fhirConnectContextRepository = fhirConnectContextRepository;
        this.jsonParser = jsonParser;
        this.cachedUtils = cachedUtils;
        this.flatJsonUnmarshaller = flatJsonUnmarshaller;
        this.prodOpenFhirMappingContext = prodOpenFhirMappingContext;
        this.openFhirStringUtils = openFhirStringUtils;
        this.fhirPathR4 = fhirPathR4;
        this.gson = gson;
    }

    /**
     * Returns context for when mapping from FHIR to openEHR, where context is either gotten from the provided
     * templateId, or if none is provided, it will loop through all user's contexts and try to apply
     * fhir condition on them.
     *
     * @param templateId
     * @param incomingFhirResource
     * @return
     */
    private FhirConnectContextEntity getContextForFhir(final String templateId,
                                                       final String incomingFhirResource) {
        log.debug("Getting context for template {}", templateId);
        if (StringUtils.isNotBlank(templateId)) {
            return fhirConnectContextRepository.findByTemplateId(templateId);
        }
        final List<FhirConnectContextEntity> allUserContexts = fhirConnectContextRepository.findAll();

        FhirConnectContextEntity fallbackContext = null;

        final Resource resource = parseIncomingFhirResource(incomingFhirResource);
        for (FhirConnectContextEntity context : allUserContexts) {
            final Condition condition = context.getFhirConnectContext().getFhir().getCondition();
            final String resourceType = context.getFhirConnectContext().getFhir().getResourceType();
            final String fhirPathWithCondition = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                    Arrays.asList(condition),
                    resourceType);
            if (StringUtils.isEmpty(fhirPathWithCondition) || fhirPathWithCondition.equals(resourceType)) {
                log.warn("No fhirpath defined for resource type, context relevant for all?");
                fallbackContext = context; // assign it to the variable in case there really is no other suitable one.. in which case, this will be returned (or the last occurrence of such a context mapper 'for all'
            } else {
                final Optional<Base> evaluated = fhirPathR4.evaluateFirst(resource, fhirPathWithCondition, Base.class);
                // if is present and is of type boolean, it also needs to be true
                // if is present and is not of type boolean, then the mere presence means the mapper is for this resource
                if (evaluated.isPresent() && ((!(evaluated.get() instanceof BooleanType) || ((BooleanType) evaluated.get()).getValue()))) {
                    // mapper matches this Resource, it can handle it
                    log.info("Found a relevant context ({}) for this input fhir Resource. If there are more relevant other than this one, others will be ignored as this was the first one found.",
                            context.getId());
                    return context;
                }
            }
        }
        if (fallbackContext != null) {
            log.warn("Returning a fallback context for this input fhir Resource {}", fallbackContext.getId());
        }
        return fallbackContext;
    }

    /**
     * Returns context for when mapping from openEHR to FHIR. It will first take the templateId from the incoming
     * payload (flatJson or Composition JSON) and then find it by user and templateId
     *
     * @return
     */
    private FhirConnectContextEntity getContextForOpenEhr(final String incomingOpenEhr,
                                                          final String incomingTemplateId) {
        log.debug("Getting context for template {}", incomingTemplateId);
        if (StringUtils.isNotBlank(incomingTemplateId)) {
            return fhirConnectContextRepository.findByTemplateId(incomingTemplateId);
        }
        log.debug("Will try to obtain template id from the incoming openEhr object");
        final String templateId = getTemplateIdFromOpenEhr(incomingOpenEhr);
        return fhirConnectContextRepository.findByTemplateId(templateId);
    }

    String getTemplateIdFromOpenEhr(final String incomingOpenEhr) {
        final JsonObject jsonObject = gson.fromJson(incomingOpenEhr, JsonObject.class);
        if (jsonObject.get("_type") != null && "COMPOSITION".equals(jsonObject.get("_type").getAsString())) {
            return jsonObject.get("archetype_details").getAsJsonObject().get("template_id").getAsJsonObject().get("value").getAsString();
        }
        final Set<String> keys = jsonObject.keySet();
        return new ArrayList<>(keys).get(0).split("/")[0];
    }


    /**
     * templateId is right now required. In the future, context mapper should also have a fhir path condition in there
     * so we could dynamically determine which context mapper is for which Request (incoming Bundle)
     * <p>
     * providing a templateId as an invoker though would mean performance optimization, although I am not sure
     * if the caller will always know which template to use?
     */
    public String toOpenEhr(final String incomingFhirResource, final String incomingTemplateId, final Boolean flat) {
        // get context and operational template
        final FhirConnectContextEntity fhirConnectContext = getContextForFhir(incomingTemplateId, incomingFhirResource);
        final String templateIdToUse = fhirConnectContext.getFhirConnectContext().getOpenEHR().getTemplateId();

        validatePrerequisites(fhirConnectContext, templateIdToUse);

        final OPERATIONALTEMPLATE operationalTemplate = cachedUtils.getOperationalTemplate(templateIdToUse);
        final WebTemplate webTemplate = cachedUtils.parseWebTemplate(operationalTemplate);

        prodOpenFhirMappingContext.initMappingCache(fhirConnectContext.getFhirConnectContext(), operationalTemplate, webTemplate);

        final Resource resource = parseIncomingFhirResource(incomingFhirResource);
        if (flat != null && flat) {
            final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(fhirConnectContext.getFhirConnectContext(),
                    resource,
                    operationalTemplate);
            return gson.toJson(jsonObject);
        } else {
            final Composition composition = fhirToOpenEhr.fhirToCompositionRm(fhirConnectContext.getFhirConnectContext(),
                    resource,
                    operationalTemplate);
            return new CanonicalJson().marshal(composition);
        }
    }

    private void preProcessIncomingResource() {
        //todo: FhirToOpenEhrPreProcessor
    }

    private Resource parseIncomingFhirResource(final String incomingFhirResource) {
        try {
            return jsonParser.parseResource(Bundle.class, incomingFhirResource);
        } catch (final Exception e) {
            return (Resource) jsonParser.parseResource(incomingFhirResource);
        }
    }

    public String toFhir(final String flatJson, final String incomingTemplateId) {
        final FhirConnectContextEntity fhirConnectContext = getContextForOpenEhr(flatJson, incomingTemplateId);
        final String templateIdToUse = fhirConnectContext.getFhirConnectContext().getOpenEHR().getTemplateId();

        validatePrerequisites(fhirConnectContext, templateIdToUse);

        final OPERATIONALTEMPLATE operationalTemplate = cachedUtils.getOperationalTemplate(templateIdToUse);
        final WebTemplate webTemplate = cachedUtils.parseWebTemplate(operationalTemplate);

        prodOpenFhirMappingContext.initMappingCache(fhirConnectContext.getFhirConnectContext(), operationalTemplate, webTemplate);

        Composition composition;
        try {
            composition = flatJsonUnmarshaller.unmarshal(flatJson, cachedUtils.parseWebTemplate(operationalTemplate));
        } catch (Exception e) {
            log.error("Error trying to unmarshall flat path, {}. Will try with a canonical json unmarshaller.", e.getMessage());
            composition = new CanonicalJson().unmarshal(flatJson);
            if(composition.getContent().isEmpty()) {
                log.error("Composition not properly unmarshalled. Empty content. Aborting translation.", e);
                throw new IllegalArgumentException("Composition not properly unmarshalled. Empty content. Aborting translation. See log for more info.");
            }
        }

        final Bundle fhir = openEhrToFhir.compositionToFhir(fhirConnectContext.getFhirConnectContext(),
                composition,
                operationalTemplate);
        return jsonParser.encodeResourceToString(fhir);
    }

    private void validatePrerequisites(final FhirConnectContextEntity fhirConnectContext, final String templateId) {
        if (fhirConnectContext == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format("Context not found for this template '%s'", templateId));
        }
        final OPERATIONALTEMPLATE operationalTemplate = cachedUtils.getOperationalTemplate(templateId);
        if (operationalTemplate == null) {
            log.error("Operational template {} could not be found.", templateId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format("Context for this template '%s' found, but no OPT has been found for it.", templateId));
        }
        final WebTemplate webTemplate = cachedUtils.parseWebTemplate(operationalTemplate);
        if (webTemplate == null) {
            log.error("Web template couldn't be created from an operation template {}.", templateId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, String.format("Could not create WebTemplate from this OPT '%s'. Please validate the template or contact OpenFHIR support team.", templateId));
        }
    }


}
