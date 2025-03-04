package com.medblocks.openfhir.tofhir;

import static com.medblocks.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_EMPTY;
import static com.medblocks.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_NOT_OF;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_ARCHETYPE_FC;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_COMPOSITION_FC;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_MEDIA;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
import static com.medblocks.openfhir.fc.FhirConnectConst.THIS;
import static com.medblocks.openfhir.fc.FhirConnectConst.UNIDIRECTIONAL_TOOPENEHR;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX_ESCAPED;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.WHERE;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.OpenFhirFhirConfig;
import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import com.medblocks.openfhir.fc.schema.model.With;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import com.medblocks.openfhir.util.FhirInstanceCreator;
import com.medblocks.openfhir.util.FhirInstanceCreatorUtility;
import com.medblocks.openfhir.util.FhirInstancePopulator;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenEhrConditionEvaluator;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Engine doing translation from openEHR to FHIR according to the openFHIR state configuration
 */
@Slf4j
@Component
public class OpenEhrToFhir {

    final private FlatJsonMarshaller flatJsonMarshaller;
    final private OpenFhirMappingContext openFhirTemplateRepo;
    final private OpenEhrCachedUtils openEhrApplicationScopedUtils;
    final private Gson gson;
    final private OpenFhirStringUtils openFhirStringUtils;
    final private OpenEhrRmWorker openEhrRmWorker;
    final private OpenFhirMapperUtils openFhirMapperUtils;
    final private FhirInstancePopulator fhirInstancePopulator;
    final private FhirInstanceCreator fhirInstanceCreator;
    final private FhirInstanceCreatorUtility fhirInstanceCreatorUtility;
    final private FhirPathR4 fhirPathR4;
    final private IntermediateCacheProcessing intermediateCacheProcessing;
    final private OpenEhrConditionEvaluator openEhrConditionEvaluator;


    @Autowired
    public OpenEhrToFhir(final FlatJsonMarshaller flatJsonMarshaller,
                         final OpenFhirMappingContext openFhirTemplateRepo,
                         final OpenEhrCachedUtils openEhrApplicationScopedUtils,
                         final Gson gson, OpenFhirStringUtils openFhirStringUtils,
                         final OpenEhrRmWorker openEhrRmWorker,
                         final OpenFhirMapperUtils openFhirMapperUtils,
                         final FhirInstancePopulator fhirInstancePopulator,
                         final FhirInstanceCreator fhirInstanceCreator,
                         final FhirInstanceCreatorUtility fhirInstanceCreatorUtility,
                         final FhirPathR4 fhirPathR4,
                         final IntermediateCacheProcessing intermediateCacheProcessing,
                         final OpenEhrConditionEvaluator openEhrConditionEvaluator) {
        this.flatJsonMarshaller = flatJsonMarshaller;
        this.openFhirTemplateRepo = openFhirTemplateRepo;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.gson = gson;
        this.openFhirStringUtils = openFhirStringUtils;
        this.openEhrRmWorker = openEhrRmWorker;
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.fhirInstancePopulator = fhirInstancePopulator;
        this.fhirInstanceCreator = fhirInstanceCreator;
        this.fhirInstanceCreatorUtility = fhirInstanceCreatorUtility;
        this.fhirPathR4 = fhirPathR4;
        this.intermediateCacheProcessing = intermediateCacheProcessing;
        this.openEhrConditionEvaluator = openEhrConditionEvaluator;
    }

    /**
     * Main method that handles business logic of mapping incoming OpenEHR Composition to a FHIR Bundle
     *
     * @param context fhir connect context mapper
     * @param composition incoming Composition that needs to be mapped (this is serialized immediately to a
     *         flat json format, meaning if it already comes like this to the openFHIR engine,
     *         we're doing 2 de/serializations; rethink if it makes sense or not - right not, this is
     *         also how we're implicitly validating incoming request, but that could be done smarter)
     * @param operationaltemplate operational template that is related to the incoming Composition
     * @return Bundle that is a result of the mapping engine
     */
    public Bundle compositionToFhir(final FhirConnectContext context,
                                    final Composition composition,
                                    final OPERATIONALTEMPLATE operationaltemplate) {
        // create flat from composition
        final WebTemplate webTemplate = openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
        final String flatJson = flatJsonMarshaller.toFlatJson(composition, webTemplate);
        final JsonObject flatJsonObject = gson.fromJson(flatJson, JsonObject.class);
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(context.getContext().getTemplateId());
        final Bundle creatingBundle = prepareBundle();
        final Map<String, Boolean> isMultipleByResourceType = new HashMap<>();
        final Map<String, Map<String, Object>> intermediateCaches = new HashMap<>();
        final Set<String> createdAndAdded = new HashSet<>();
        final Set<String> archetypesAlreadyProcessed = new HashSet<>();

        // loop through top level content/archetypes within the Composition
        for (final ContentItem archetypesWithinContent : composition.getContent()) {

            // elements instantiated throughout the mapping (FHIR dataelements instantiated, key'd by created object + fhir path + openehr path)
            // instanced here so multiple archetypes can share them
            final Map<String, Object> instantiatedIntermediateElements = new HashMap<>();

            final String archetypeNodeId = archetypesWithinContent.getArchetypeNodeId();
            if (archetypesAlreadyProcessed.contains(archetypeNodeId)) {
                continue;
            }

            // get mapper by templateid (context) + archetype id (model)
            final List<OpenFhirFhirConnectModelMapper> theMappers = openFhirTemplateRepo.getMapperForArchetype(
                    templateId, archetypeNodeId);
            if (theMappers == null) {
                log.error("No mappers defined for archetype within this composition: {}. No mapping possible.",
                          archetypeNodeId);
                continue;
            }
            handleMappings(theMappers,
                           createdAndAdded,
                           intermediateCaches,
                           isMultipleByResourceType,
                           flatJsonObject,
                           webTemplate,
                           instantiatedIntermediateElements,
                           creatingBundle,
                           archetypesAlreadyProcessed,
                           archetypesWithinContent,
                           archetypeNodeId);
        }

        return creatingBundle;
    }

    /**
     * Loops over available mappings, creates helpers for mappings and then corresponding FHIR Resources
     * to given openEHR Compositions
     *
     * @param theMappers fhir connect mappers available for mapping
     * @param createdAndAdded set of string of already created Resources, so we don't do duplicates
     * @param isMultipleByResourceType if certain mapping produces multiple resources
     * @param flatJsonObject Composition in a flat json format that needs to be mapped
     * @param webTemplate web template of the inbound Composition
     * @param instantiatedIntermediateElements elements instantiated throughout the mapping (FHIR dataelements
     *         instantiated,
     * @param intermediateCaches cached intermediate caches per Resource type
     *         key'd by created object + fhir path + openehr path)
     * @param creatingBundle Bundle that is being created as part of the mappings
     * @param archetypesAlreadyProcessed set of archetypes already processed
     * @param archetypesWithinContent archetype within a Composition that is currently being mapped
     * @param archetypeNodeId archetype id within a Composition that is currently being mapped
     */
    private void handleMappings(final List<OpenFhirFhirConnectModelMapper> theMappers,
                                final Set<String> createdAndAdded,
                                final Map<String, Map<String, Object>> intermediateCaches,
                                final Map<String, Boolean> isMultipleByResourceType,
                                final JsonObject flatJsonObject,
                                final WebTemplate webTemplate,
                                final Map<String, Object> instantiatedIntermediateElements,
                                final Bundle creatingBundle,
                                final Set<String> archetypesAlreadyProcessed,
                                final ContentItem archetypesWithinContent,
                                final String archetypeNodeId) {
        for (final OpenFhirFhirConnectModelMapper theMapper : theMappers) {
            if (theMapper.getFhirConfig() == null) {
                // if fhir config is null, it means it's a slot mapper and it can't be a first-level Composition.content one
                continue;
            }

            final Boolean existingEntry = isMultipleByResourceType.getOrDefault(theMapper.getFhirConfig().getResource(),
                                                                                true);

            // fhirConfig.multiple signals if model mapper should return in multiple base FHIR Resources or a single one
            // if not multiple, then we need to get an existing already created FHIR Resource and use that one for the
            // following mappings
            final boolean shouldUseExisting = existingEntry && !theMapper.getFhirConfig().getMultiple();
            isMultipleByResourceType.put(theMapper.getFhirConfig().getResource(), shouldUseExisting);
            intermediateCaches.put(theMapper.getFhirConfig().getResource(),
                                   intermediateCaches.getOrDefault(theMapper.getFhirConfig().getResource(),
                                                                   instantiatedIntermediateElements));

            // helper POJOs that help for openEHR to FHIR mappings
            final List<OpenEhrToFhirHelper> helpers = new ArrayList<>();
            String firstFlatPath;
            if (!theMapper.getOpenEhrConfig().getArchetype().contains("CLUSTER")) {
                firstFlatPath =
                        webTemplate.getTree().getId() + "/content[" + theMapper.getOpenEhrConfig().getArchetype() + "]";
            } else {
                firstFlatPath = webTemplate.getTree().getId();
            }

            prepareOpenEhrToFhirHelpers(theMapper,
                                        theMapper.getFhirConfig().getResource(),
                                        firstFlatPath,
                                        theMapper.getMappings(),
                                        helpers,
                                        webTemplate,
                                        flatJsonObject,
                                        false,
                                        null,
                                        null,
                                        firstFlatPath,
                                        false);

            // within helpers, you should have everything you need to create a FHIR Resource now
            final List<Resource> created = createResourceFromOpenEhrToFhirHelper(helpers,
                                                                                 theMapper.getFhirConfig(),
                                                                                 shouldUseExisting
                                                                                         ? creatingBundle.getEntry()
                                                                                         .stream()
                                                                                         .map(Bundle.BundleEntryComponent::getResource)
                                                                                         .filter(en -> en.getResourceType()
                                                                                                 .name()
                                                                                                 .equals(theMapper.getFhirConfig()
                                                                                                                 .getResource()))
                                                                                         .findAny()
                                                                                         .orElse(null) : null,
                                                                                 shouldUseExisting
                                                                                         ? intermediateCaches.getOrDefault(
                                                                                         theMapper.getFhirConfig()
                                                                                                 .getResource(),
                                                                                         instantiatedIntermediateElements)
                                                                                         : instantiatedIntermediateElements);

            log.info("Constructed {} resources for archetype {}.", created.size(),
                     archetypesWithinContent.getArchetypeNodeId());

            addEntriesToBundle(creatingBundle, created, createdAndAdded);
            archetypesAlreadyProcessed.add(archetypeNodeId);
        }
    }

    /**
     * Prepares Bundle that is being created. This method should handle references between resources,
     * Bundle metadata, ....
     *
     * @return prepared Bundle
     */
    private Bundle prepareBundle() {
        return new Bundle(); // todo: metadatas
    }

    /**
     * Utility method to add a Resource to a Bundle.entry
     *
     * @param bundle that is being created
     * @param resource that needs to be added to the Bundle
     */
    private void addEntryToBundle(final Bundle bundle, final Resource resource) {
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(resource));
    }

    /**
     * Utility method to add a Resource to a Bundle.entry
     *
     * @param bundle that is being created
     * @param resources that need to be added to the Bundle
     * @param createdAndAdded hash code of resources that were already added to the Bundle, to avoid duplicated
     *         entries
     *         being added
     */
    private void addEntriesToBundle(final Bundle bundle, final List<Resource> resources,
                                    final Set<String> createdAndAdded) {

        resources.forEach(res -> {
            if (createdAndAdded.contains(String.valueOf(res.hashCode()))) {
                return;
            }
            createdAndAdded.add(String.valueOf(res.hashCode()));
            addEntryToBundle(bundle, res);
        });
    }

    /**
     * create FHIR Resources from the OpenEhrToFhirHelpers constructed in previous step of the mapping flow
     *
     * @param helpers that present helpers for mapping
     * @param fhirConfig config of a specific fhir connect mapping
     * @param existingCreatingResource resource that was already created as part of previous mappings (can be
     *         null)
     * @param instantiatedIntermediateElements elements instantiated with other preceding mappings
     * @return created Resources
     */
    private List<Resource> createResourceFromOpenEhrToFhirHelper(final List<OpenEhrToFhirHelper> helpers,
                                                                 final OpenFhirFhirConfig fhirConfig,
                                                                 final Resource existingCreatingResource,
                                                                 final Map<String, Object> instantiatedIntermediateElements) {

        final String generatingResource = fhirConfig.getResource();
        final List<Condition> conditions = fhirConfig.getCondition();

        final List<Resource> separatelyCreatedResources = new ArrayList<>();
        // cache of created Resources by index
        final Map<String, Resource> createdPerIndex = new HashMap<>();
        if (existingCreatingResource != null) {
            createdPerIndex.put(createKey(0, fhirConfig.getResource()), existingCreatingResource);
        }
        final String conditioningFhirPath = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                                                                              conditions,
                                                                              generatingResource);
        for (final OpenEhrToFhirHelper helper : helpers) {

            final String conditionLog = helper.getCondition() == null ? ""
                    : (helper.getCondition().getTargetRoot() + " where " + helper.getCondition().getTargetAttribute()
                            + " " + helper.getCondition().getOperator() + " " + helper.getCondition().getCriteria());
            log.debug(
                    "Processing: archetpye '{}', targetResource '{}', fhirPath '{}', openEhrPath '{}', openEhrType '{}', data size '{}', condition '{}', parentFhirPath '{}', parentOpenEhrPath '{}'",
                    helper.getMainArchetype(), helper.getTargetResource(), helper.getFhirPath(),
                    helper.getOpenEhrPath(), helper.getOpenEhrType(), helper.getData().size(), conditionLog,
                    helper.getParentFollowedByFhirPath(), helper.getOpenEhrPath());

            final List<OpenEhrToFhirHelper.DataWithIndex> datas = helper.getData();
            if (datas.isEmpty()) {
                log.warn("No data has been parsed for path: {}", helper.getOpenEhrPath());
            }

            sortByLastIndex(datas);

            for (final OpenEhrToFhirHelper.DataWithIndex data : datas) {
                if (data.getIndex() == -1) {
                    // -1 means it's for all Resources, it's handled afterward, below
                    continue;
                }
                final String fullOpenEhrPath = data.getFullOpenEhrPath();

                // first index within the openehr flat path represents occurrence of the main resource being created
                final Integer index;
                if (OPENEHR_ARCHETYPE_FC.equals(fullOpenEhrPath)) {
                    // it's hardcoding, take index from data instead of from fullOpenEhrPath
                    index = data.getIndex();
                } else {
                    index = fhirConfig.getMultiple() ? openFhirStringUtils.getFirstIndex(fullOpenEhrPath) : 0;
                }
                final String mapKey = createKey(index, conditioningFhirPath);

                final Resource instance = getOrCreateResource(createdPerIndex, generatingResource, mapKey);
                handleMapping(data, createdPerIndex, instance, fullOpenEhrPath, generatingResource,
                              helper, instantiatedIntermediateElements, separatelyCreatedResources, mapKey);
            }

            for (OpenEhrToFhirHelper.DataWithIndex dataForAllResources : datas.stream()
                    .filter(data -> data.getIndex() == -1).toList()) {
                final String fullOpenEhrPath = dataForAllResources.getFullOpenEhrPath();
                final ArrayList<Resource> resources = new ArrayList<>(createdPerIndex.values());
                if (resources.isEmpty()) {
                    final Resource nowInstantiated = fhirInstanceCreatorUtility.create(helper.getTargetResource());
                    createdPerIndex.put(createKey(0, conditioningFhirPath), nowInstantiated);
                    resources.add(nowInstantiated); //add at least one if none was created as part of the previous step
                }
                for (final Resource instance : resources) {
                    handleMapping(dataForAllResources, null, instance, fullOpenEhrPath, generatingResource,
                                  helper, instantiatedIntermediateElements, separatelyCreatedResources, null);
                }
            }
        }

        final List<Resource> createdResources = new ArrayList<>(createdPerIndex.values());
        // now handle "hardcoded" things within conditions
        postProcessMappingFromCoverConditions(createdResources, conditions);

        createdResources.addAll(separatelyCreatedResources);
        return createdResources;
    }

    private void cacheReturnedItems(final FindingOuterMost findingOuterMost,
                                    final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn,
                                    final Resource instance,
                                    final String fhirPath,
                                    final String generatingResource,
                                    final boolean removedPathIsOnlyWhere,
                                    final String fullOpenEhrPath,
                                    final Map<String, Object> instantiatedIntermediateElements,
                                    final OpenEhrToFhirHelper helper) {
        /*
          Needs to have full path to this item that will be added to the cache
         */
        final String preparedFullFhirPathForCachePopulation;
        if (findingOuterMost.getRemovedPath().startsWith(".as(")) {
            // was casting
            final String removedPath = findingOuterMost.getRemovedPath();
            final String[] splitRemovedPathByDot = removedPath.split("\\.");
            final String castString = splitRemovedPathByDot[1];
            final String actualRemovedAndResolvedPartString =
                    splitRemovedPathByDot.length > 2 ? ("." + splitRemovedPathByDot[2]) : null;
            preparedFullFhirPathForCachePopulation = fhirPath
                    .replace(generatingResource + ".", "")
                    .replace(removedPath, "") + "." + castString + actualRemovedAndResolvedPartString;


        } else {
            if (removedPathIsOnlyWhere) {
                preparedFullFhirPathForCachePopulation = fhirPath;
            } else {
                String removedPath = findingOuterMost.getRemovedPath();
                final boolean startsWithWhere = removedPath.startsWith("." + WHERE + "(");
                if (startsWithWhere) {
                    removedPath = removedPath.replace("." + openFhirStringUtils.extractWhereCondition(removedPath), "");
                }
                final List<String> splitByDots = Arrays.stream(removedPath.split("\\.")).filter(StringUtils::isNotBlank)
                        .toList();
                final String suffix = splitByDots.get(0);
                final String where = splitByDots.size() > 1 && splitByDots.get(1).startsWith(WHERE) ? ("."
                        + openFhirStringUtils.extractWhereCondition(removedPath)) : "";
                final String cast =
                        splitByDots.size() > 1 && splitByDots.get(1).startsWith("as") ? ("." + splitByDots.get(1)) : "";

                preparedFullFhirPathForCachePopulation = fhirPath
                        .replace(generatingResource + ".", "")
                        .replace(removedPath, "")
                        + "."
                        + suffix + where + cast;
            }
        }
        hardcodedReturn.setPath(preparedFullFhirPathForCachePopulation);


        intermediateCacheProcessing.populateIntermediateCache(hardcodedReturn,
                                                              instance.toString(),
                                                              instantiatedIntermediateElements,
                                                              instance.getResourceType().name(),
                                                              fullOpenEhrPath,
                                                              helper.getParentFollowedByFhirPath(),
                                                              helper.getParentFollowedByOpenEhr());
    }

    /**
     * When from instantiated cache we get a list of some elements and removedPath is a where, we need to check if
     * something within that list actually matches the where or not. If it does - good - removedPath is applied on that
     * element,
     * but if it doesn't, setting removedPath objects on that element would overwrite what was already set there. So in
     * this case,
     * (when no elements within the list match the where), we rather instantiate a new such element, add it to the list
     * and
     * let the fhirInstanceCreator.instantiateAndSetElement be processed on that one.
     * <p>
     * Example of such a thing is where you get a list of extensions from the intermediate cache, but your fhirPath
     * defines only a very specific extension (i.e. the one with url=123).
     */
    private void handleReturnedListWithWhereCondition(final FindingOuterMost findingOuterMost) {
        if (findingOuterMost.getLastObject() instanceof List<?> && (
                findingOuterMost.getRemovedPath().startsWith("." + WHERE) || findingOuterMost.getRemovedPath()
                        .startsWith("where"))) {
            // i.e., returned found element was an array of extensions and what we're looking for is a very specific extension, not necessarily the one within the list
            String where = openFhirStringUtils.extractWhereCondition(findingOuterMost.getRemovedPath());
            if (where.startsWith(".")) {
                where = where.substring(1);
            }

            boolean matchFound = false;
            for (Object baseObj : ((List<?>) findingOuterMost.getLastObject())) {
                final List<Base> matchingElements = fhirPathR4.evaluate((Base) baseObj, where, Base.class);
                matchFound = !matchingElements.isEmpty();
                if (matchFound) {
                    break;
                }
            }

            if (!matchFound) {
                // it means a new one needs to be added because the one currently in there is not the one we should be setting anything to!
                try {
                    final Object newInstanceOfThisObject = ((List<Object>) findingOuterMost.getLastObject()).get(0)
                            .getClass().getDeclaredConstructor().newInstance();
                    ((List<Object>) findingOuterMost.getLastObject()).add(newInstanceOfThisObject);
                    findingOuterMost.setRemovedPath(findingOuterMost.getRemovedPath()
                                                            .replace("." + where, "")
                                                            .replace(where, ""));
                } catch (final Exception e) {
                    log.error("Error trying to handle returning list with a where condition", e);
                }
            }
        }
    }

    /**
     * Handle condition mapping finds an element within the instantiated ones relevant for this condition mapping
     * and sets the hardcoded values on it. I.e. of a condition mapping specific an extension needs to have 'url'
     * of a specific value, this method will make sure that's added to the generated item.
     *
     * @param condition as it's defined in the fhir connect model mapper
     * @param instance fhir resource being generated
     * @param fullOpenEhrPath openehr path of the model mapper
     * @param instantiatedIntermediateElements intermediate cache where we'll try to find element that's just
     *         been
     *         instantiated
     * @param targetResource target resource if it's a resolve() mapping
     */
    private void handleConditionMapping(final Condition condition,
                                        final Resource instance,
                                        final String fullOpenEhrPath,
                                        final Map<String, Object> instantiatedIntermediateElements,
                                        final String targetResource,
                                        final boolean isFollowedBy,
                                        final String parentFhirEhr,
                                        final String parentOpenEhr) {
        if (condition == null || CONDITION_OPERATOR_EMPTY.equals(condition.getOperator())) {
            return;
        }
        final String stringFromCriteria = openFhirStringUtils.getStringFromCriteria(condition.getCriteria()).getCode();

        final String fhirPathSuffix = ("." + condition.getTargetAttribute());

        final String conditionFhirPathWithConditions =
                openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, targetResource,
                                                              parentFhirEhr) + fhirPathSuffix;
        final FindingOuterMost existing = intermediateCacheProcessing.findTheOuterMostThatExistsWithinCache(
                instantiatedIntermediateElements,
                instance,
                conditionFhirPathWithConditions,
                fullOpenEhrPath,
                "",
                isFollowedBy,
                parentOpenEhr);

        if (existing.getLastObject() != null) {
            final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(
                    existing.getLastObject(),
                    existing.getLastObject().getClass(),
                    existing.getRemovedPath(),
                    null);

            hardcodedReturn.setPath(
                    openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, targetResource,
                                                                  parentFhirEhr).replace(targetResource + ".", "") + "."
                            + hardcodedReturn.getPath());

            intermediateCacheProcessing.populateIntermediateCache(hardcodedReturn,
                                                                  instance.toString(),
                                                                  instantiatedIntermediateElements,
                                                                  instance.getResourceType().name(),
                                                                  fullOpenEhrPath,
                                                                  parentFhirEhr,
                                                                  parentOpenEhr);

            fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(),
                                                  new StringType(stringFromCriteria));
        } else {
            // here you have to instantiate the actual where items, so prepare fhir path as such
            final String fhirPathWithoutConditions = openFhirStringUtils.getFhirPathWithoutConditions(
                    condition.getTargetRoot(), condition, targetResource);
            final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(
                    instance,
                    instance.getClass(),
                    fhirPathWithoutConditions,
                    null);
            // find the one that needs criteria
//            hardcodedReturn.setPath(
//                    openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, targetResource,
//                                                                  parentFhirEhr).replace(targetResource + ".",
//                                                                                         "")); // this may not be entirely correct, should probably replace differently....depending on whether targetRoot is fhirResource or not

            setCriteriaOnPath(hardcodedReturn, condition, targetResource, parentFhirEhr);

            intermediateCacheProcessing.populateIntermediateCache(hardcodedReturn,
                                                                  instance.toString(),
                                                                  instantiatedIntermediateElements,
                                                                  instance.getResourceType().name(),
                                                                  fullOpenEhrPath,
                                                                  parentFhirEhr,
                                                                  parentOpenEhr);

            fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(),
                                                  new StringType(stringFromCriteria));
        }
    }

    private void setCriteriaOnPath(final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn,
                                   final Condition condition, final String targetResource, final String parentFhirEhr) {
        if(hardcodedReturn == null) {
            return;
        }
        if(condition.getTargetRoot().endsWith(hardcodedReturn.getPath())) {
            final String targetRoot = condition.getTargetRoot().replace(FhirConnectConst.FHIR_RESOURCE_FC, targetResource);
            hardcodedReturn.setPath(openFhirStringUtils.getFhirPathWithConditions(targetRoot, condition, targetResource,
                                                                                  parentFhirEhr)
                                            .replace(targetRoot, hardcodedReturn.getPath()));
            System.out.println();
        } else {
            setCriteriaOnPath(hardcodedReturn.getInner(), condition, targetResource, parentFhirEhr);
        }

    }


    /**
     * Method that tries to find an existing element within intermediate cache. If none is found, a new one is
     * created and added to the cache.
     *
     * @param instantiatedIntermediateElements cache of already created FHIR elements
     * @param coverInstance main instance that is being populated/created (FHIR Resource)
     * @param fhirPath fhir path of the given mapping
     * @param type type as defined in fhir connect model mapping (and/or as found within the WebTemplate)
     * @param resolveResourceType if fhir path is a resolve(), then this needs to contain resource type of the
     *         resolved
     *         element
     * @param fullOpenEhrPath full openEhr flat path
     * @param isFollowedBy if mapping is a followed by mapping of another one (also true if it's slot mapping)
     * @param parentFollowedByMapping if it's followed by, this will contain parent's fhir path
     * @param parentFollowedByOpenEhr if it's followed by, this will contain parent's openehr path
     * @param separatelyCreatedResources resources that were separately created as part of the mapping
     *         (separately
     *         created means they were not directly created as part of the mapping but
     *         as part of the resolve() procedure)
     * @return FindingOuterMost that presents an object with the found cache item
     */
    private FindingOuterMost getOrInstantiateIntermediateItem(
            final Map<String, Object> instantiatedIntermediateElements,
            final Resource coverInstance,
            final String fhirPath,
            final String type,
            final String resolveResourceType,
            final String fullOpenEhrPath,
            final boolean isFollowedBy,
            final String parentFollowedByMapping,
            final String parentFollowedByOpenEhr,
            final List<Resource> separatelyCreatedResources) {
        final FindingOuterMost existing = intermediateCacheProcessing.findTheOuterMostThatExistsWithinCache(
                instantiatedIntermediateElements,
                coverInstance,
                fhirPath,
                fullOpenEhrPath,
                "",
                isFollowedBy,
                parentFollowedByOpenEhr);
        if (existing.getLastObject() != null) {
            return existing;
        }

        // Resource override; if 'fhir' starts with upper case, it references a new Resource that needs to be created
        // independently (and is not a reference to anything within that resource actually)
        final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn;

        final String[] splitFhirPath = fhirPath.split("\\.");
        final String resType = splitFhirPath[0];
        if (StringUtils.isNotEmpty(resType) && !coverInstance.getResourceType().name().equals(resType)) {
            final Resource newCoverInstance = fhirInstanceCreatorUtility.create(resType);
            separatelyCreatedResources.add(newCoverInstance);
            hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(newCoverInstance,
                                                                           newCoverInstance.getClass(),
                                                                           fhirPath.substring(
                                                                                   fhirPath.indexOf(".") + 1),
                                                                           openFhirMapperUtils.getFhirConnectTypeToFhir(
                                                                                   type),
                                                                           resType);
            hardcodedReturn.setPath(resType + "." + hardcodedReturn.getPath());
            // populate with the new cover instance as well
            final FhirInstanceCreator.InstantiateAndSetReturn newCoverInstanceForCache = new FhirInstanceCreator.InstantiateAndSetReturn(
                    newCoverInstance,
                    false,
                    null,
                    resType);

            intermediateCacheProcessing.populateIntermediateCache(newCoverInstanceForCache,
                                                                  coverInstance.toString(),
                                                                  instantiatedIntermediateElements,
                                                                  resType,
                                                                  fullOpenEhrPath,
                                                                  parentFollowedByMapping,
                                                                  parentFollowedByOpenEhr);
        } else {
            hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(coverInstance,
                                                                           coverInstance.getClass(),
                                                                           fhirPath,
                                                                           openFhirMapperUtils.getFhirConnectTypeToFhir(
                                                                                   type),
                                                                           resolveResourceType);
        }


        final FhirInstanceCreator.InstantiateAndSetReturn lastReturn = getLastReturn(hardcodedReturn);
        final Object toSetCriteriaOn = lastReturn.getReturning();

        intermediateCacheProcessing.populateIntermediateCache(hardcodedReturn,
                                                              coverInstance.toString(),
                                                              instantiatedIntermediateElements,
                                                              coverInstance.getResourceType().name(),
                                                              fullOpenEhrPath,
                                                              parentFollowedByMapping,
                                                              parentFollowedByOpenEhr);

        return new FindingOuterMost(toSetCriteriaOn, null);
    }

    /**
     * Creates key for the main Resource creation cache, constructed from an integer the represents occurrence from flat
     * path
     * and limitingResourceCriteria that's a fhir path constructed from Conditions
     */
    private String createKey(final Integer index, final String limitingResourceCriteria) {
        return String.format("%s_%s", index, limitingResourceCriteria);
    }

    /**
     * Find a Resource within createdPerIndex or create one if it doesn't exist already
     *
     * @param createdPerIndex cache where this method will be searching in
     * @param targetResource Resource type that we're looking for (for the purpose of creating it)
     * @param key that should point to a Resource within the cache
     * @return Resource from the cache (or created one if there was none)
     */
    private Resource getOrCreateResource(final Map<String, Resource> createdPerIndex,
                                         final String targetResource,
                                         final String key) {
        if (createdPerIndex.containsKey(key)) {
            return createdPerIndex.get(key);
        }
        log.info("Create a new instance of a resource {}", targetResource);
        return fhirInstanceCreatorUtility.create(targetResource);
    }

    /**
     * Prepare helper objects for openEHR to FHIR mappings. Helper objects in a friendly and easily accessible way store
     * see @javadoc of OpenEhrToFhirHelper.class
     *
     * @param theMapper fhir connect mapper
     * @param resourceType fhir resource type being mapped to
     * @param firstFlatPath first flat path - in most cases template id
     * @param mappings model mapper mappings
     * @param helpers a list of helpers being constructed
     * @param webTemplate openEHR web template object
     * @param originalFlatJsonObject flat json we're constructing FHIR from
     * @param isFollowedBy if a mapper is a followed by mapper (true if followedBy or slotArchetype)
     * @param parentFollowedByFhir if followed by, this is parent's fhir path
     * @param parentFollowedByOpenEhr if followed by, this is parent's openehr path
     * @param slotContext if slot context mapper, this is the base flat path you use as a root for context
     *         mappings
     * @param possibleRecursion if there's a possibility we're in a recursion loop (i.e. if we came here from a
     *         slot mapping
     */
    void prepareOpenEhrToFhirHelpers(final OpenFhirFhirConnectModelMapper theMapper,
                                     final String resourceType,
                                     final String firstFlatPath,
                                     final List<Mapping> mappings,
                                     final List<OpenEhrToFhirHelper> helpers,
                                     final WebTemplate webTemplate,
                                     final JsonObject originalFlatJsonObject,
                                     boolean isFollowedBy,
                                     final String parentFollowedByFhir,
                                     final String parentFollowedByOpenEhr,
                                     final String slotContext,
                                     final boolean possibleRecursion) {
        if (mappings == null) {
            return;
        }
        for (final Mapping mapping : mappings) {

            final With with = mapping.getWith();
            final String hardcodedValue = with.getValue();
            if (with.getFhir() == null) {
                // it means it's hardcoding to openEHR, we can therefore skip it when mapping to FHIR
                continue;
            }
            if (with.getOpenehr() == null && hardcodedValue != null) {
                // hardcoding to FHIR
                with.setOpenehr(OPENEHR_ARCHETYPE_FC);
            }

            if (with.getUnidirectional() != null && UNIDIRECTIONAL_TOOPENEHR.equalsIgnoreCase(
                    with.getUnidirectional())) {
                // this is unidirectional mapping to openEHR only, ignore
                continue;
            }
            final String definedMappingWithOpenEhr = with.getOpenehr();
            String fixedOpenEhr = definedMappingWithOpenEhr.replace(OPENEHR_ARCHETYPE_FC, firstFlatPath)
                    .replace(OPENEHR_COMPOSITION_FC, webTemplate.getTree().getId());
            String openehrAqlPath = getOpenEhrKey(fixedOpenEhr, parentFollowedByOpenEhr, firstFlatPath);
            String openehr = getPathFromAqlPath(openehrAqlPath, webTemplate, mapping.getWith().getType());
            String parentFollowedByOpenEhrWithOutAqlPath = null;
            if (parentFollowedByOpenEhr != null) {
                String parentFollowedByOpenEhrWithAqlPath = openFhirStringUtils.prepareOpenEhrSyntax(
                        parentFollowedByOpenEhr, firstFlatPath);
                parentFollowedByOpenEhrWithOutAqlPath = getPathFromAqlPath(parentFollowedByOpenEhrWithAqlPath,
                                                                           webTemplate, mapping.getWith().getType());
                parentFollowedByOpenEhrWithOutAqlPath = parentFollowedByOpenEhrWithOutAqlPath.replace(RECURRING_SYNTAX,
                                                                                                      "");
            }
            final Condition openEhrCondition = mapping.getOpenehrCondition();
            prepareOpenEhrCondition(openEhrCondition, firstFlatPath, webTemplate);

            final JsonObject flatJsonObject = openEhrConditionEvaluator.splitByOpenEhrCondition(originalFlatJsonObject,
                                                                                                openEhrCondition,
                                                                                                parentFollowedByOpenEhr
                                                                                                        == null
                                                                                                        ? firstFlatPath
                                                                                                        : parentFollowedByOpenEhrWithOutAqlPath);

            final String rmType = getRmType(openehr, mapping, webTemplate);

            // get fhir path with conditions included in the fhir path itself
            final String fhirPath = openFhirStringUtils.amendFhirPath(with.getFhir(),
                                                                      null, // should condition be added here?
                                                                      theMapper.getFhirConfig().getResource());

            /*
              handling of $reference mappings as defined in the fhir connect spec
             */
            if (definedMappingWithOpenEhr.startsWith(FhirConnectConst.REFERENCE) && mapping.getReference() != null) {
                handleReferenceMapping(mapping, resourceType, parentFollowedByFhir, parentFollowedByOpenEhr, theMapper,
                                       firstFlatPath, definedMappingWithOpenEhr, fhirPath, isFollowedBy, helpers,
                                       webTemplate,
                                       flatJsonObject, slotContext, openehr, possibleRecursion);
            } else {
                final String OPENEHR_CONTENT_SUFFIX = "content/content";
                if (openehr.endsWith(OPENEHR_CONTENT_SUFFIX) && OPENEHR_TYPE_MEDIA.equals(rmType)) {
                    openehr = openehr.substring(0, openehr.length()
                            - 8); // remove the last /content part (8 chars), because the path is content/content which is not ok for openEhr—>fhir
                }
                boolean manuallyAddingOccurrence = openehr.contains(RECURRING_SYNTAX);
                if (manuallyAddingOccurrence) {
                    // for cases when you're manually adding recurring syntax to an openEHR path for whatever reason
                    // (but mostly due to context weird behavior when you have _participation)
                    openehr = openehr.replaceAll(RECURRING_SYNTAX_ESCAPED, "");
                }

                if (mapping.getSlotArchetype() != null) {
                    handleSlotMapping(mapping, resourceType, parentFollowedByFhir, theMapper, firstFlatPath,
                                      definedMappingWithOpenEhr,
                                      fhirPath, helpers, webTemplate, flatJsonObject, slotContext, openehr,
                                      possibleRecursion);
                } else {
                    // adds regex pattern to simplified path in a way that we can extract data from a given flat path
                    final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(openehr);

                    // get all entries from the flat path that match the simplified flat path with regex pattern
                    final List<String> matchingEntries = openFhirStringUtils.getAllEntriesThatMatch(withRegex,
                                                                                                    flatJsonObject);
                    final Map<String, List<String>> joinedEntries = joinValuesThatAreOne(matchingEntries);
                    handleRegularMapping(mapping, resourceType, parentFollowedByFhir,
                                         parentFollowedByOpenEhrWithOutAqlPath,
                                         theMapper,
                                         firstFlatPath, definedMappingWithOpenEhr, fhirPath, isFollowedBy, helpers,
                                         webTemplate,
                                         flatJsonObject, slotContext, openehr, joinedEntries, rmType, hardcodedValue,
                                         possibleRecursion);
                }
            }

        }
    }

    private void prepareOpenEhrCondition(final Condition openEhrCondition,
                                         final String firstFlatPath,
                                         final WebTemplate webTemplate) {
        if (openEhrCondition == null) {
            return;
        }
        final String originalTargetRoot = openEhrCondition.getTargetRoot();
        final String openEhrConditionTargetRootWithAqlPath = openFhirStringUtils.prepareOpenEhrSyntax(
                originalTargetRoot, firstFlatPath);


        final String openEhrConditionTargetRoot = getPathFromAqlPath(
                openFhirMapperUtils.removeAqlSuffix(openEhrConditionTargetRootWithAqlPath), webTemplate, null);
        final String formattedOpenEhrConditionTargetRoot = openEhrConditionTargetRoot.replace(RECURRING_SYNTAX, "")
                .replace(firstFlatPath, FhirConnectConst.OPENEHR_ARCHETYPE_FC);
        openEhrCondition.setTargetRoot(
                formattedOpenEhrConditionTargetRoot + openFhirMapperUtils.replaceAqlSuffixWithFlatSuffix(
                        openEhrConditionTargetRootWithAqlPath));

        if (openEhrCondition.getTargetAttributes() == null) {
            return;
        }
        final List<String> newAttributes = new ArrayList<>();
        for (final String targetAttribute : openEhrCondition.getTargetAttributes()) {
            final String openEhrConditionTargetAttributeWithAqlPath = openFhirStringUtils.prepareOpenEhrSyntax(
                    originalTargetRoot + "/" + targetAttribute, firstFlatPath);
            final String openEhrConditionTargetAttribute = getPathFromAqlPath(
                    openFhirMapperUtils.removeAqlSuffix(openEhrConditionTargetAttributeWithAqlPath), webTemplate, null);
            final String formattedOpenEhrConditionAttribute = openEhrConditionTargetAttribute.replace(RECURRING_SYNTAX,
                                                                                                      "")
                    .replace(firstFlatPath, FhirConnectConst.OPENEHR_ARCHETYPE_FC);

            final String newAttribute =
                    formattedOpenEhrConditionAttribute + openFhirMapperUtils.replaceAqlSuffixWithFlatSuffix(
                            openEhrConditionTargetAttributeWithAqlPath);
            if (newAttribute.startsWith(openEhrCondition.getTargetRoot())) {
                newAttributes.add(newAttribute.replace(openEhrCondition.getTargetRoot() + "/", ""));
            } else {
                newAttributes.add(newAttribute);
            }
        }
        openEhrCondition.setTargetAttributes(newAttributes);
    }

    public String getPathFromAqlPath(String openEhrPath, WebTemplate webTemplate, String rmType) {
        final FhirToOpenEhrHelper getTypeHelper = FhirToOpenEhrHelper.builder()
                .openEhrPath(openEhrPath)
                .openEhrType(rmType)
                .build();
        openEhrRmWorker.fixFlatWithOccurrences(Collections.singletonList(getTypeHelper), webTemplate);
        if (getTypeHelper.getOpenEhrType() != null && getTypeHelper.getOpenEhrType().equals(OPENEHR_TYPE_NONE)
                && openEhrPath.split("/").length != getTypeHelper.getOpenEhrPath().split("/").length) {
            return "INVALID_DATA_POINT";
        }
        return getTypeHelper.getOpenEhrPath();
    }

    /**
     * Handles regular mapping (no slot and no reference)
     */
    private void handleRegularMapping(final Mapping mapping, final String resourceType,
                                      final String parentFollowedByFhir,
                                      final String parentFollowedByOpenEhr,
                                      final OpenFhirFhirConnectModelMapper theMapper, final String firstFlatPath,
                                      final String definedMappingWithOpenEhr, final String fhirPath,
                                      final boolean isFollowedBy,
                                      final List<OpenEhrToFhirHelper> helpers, final WebTemplate webTemplate,
                                      final JsonObject flatJsonObject,
                                      final String slotContext, final String openehr,
                                      final Map<String, List<String>> joinedEntries,
                                      final String rmType, final String hardcodedValue,
                                      final boolean possibleRecursion) {
        String openEhrPath = null;
        List<OpenEhrToFhirHelper.DataWithIndex> values = extractValues(mapping, joinedEntries, rmType, flatJsonObject,
                                                                       hardcodedValue);
//        if(values != null && openehr.equals(parentFollowedByOpenEhr) && rmType!=null  ){
//           values.clear();
//        }
        if (!OPENEHR_TYPE_NONE.equals(mapping.getWith().getType())) {
            openEhrPath = openehr;
        } else if (mapping.getFhirCondition() != null) {
            openEhrPath = openFhirStringUtils.prepareOpenEhrSyntax(openehr, firstFlatPath);
        }

        if (openEhrPath != null) {
            OpenEhrToFhirHelper openEhrToFhirHelper = OpenEhrToFhirHelper.builder()
                    .mainArchetype(theMapper.getOpenEhrConfig().getArchetype())
                    .targetResource(resourceType)
                    .openEhrPath(openEhrPath)
                    .fhirPath(fhirPath)
                    .openEhrType(mapping.getWith().getType())
                    .data(values)
                    .isFollowedBy(isFollowedBy)
                    .parentFollowedByFhirPath(parentFollowedByFhir == null ? null
                                                      : parentFollowedByFhir.replace(FhirConnectConst.FHIR_RESOURCE_FC,
                                                                                     resourceType))
                    .parentFollowedByOpenEhr(parentFollowedByOpenEhr == null ? null : parentFollowedByOpenEhr.replace(
                            FhirConnectConst.OPENEHR_ARCHETYPE_FC, firstFlatPath))
                    .condition(mapping.getFhirCondition())
                    .openehrCondition(mapping.getOpenehrCondition())
                    .build();
            helpers.add(openEhrToFhirHelper);
        }

        if (mapping.getFollowedBy() != null) {
            final List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();

            openFhirMapperUtils.prepareFollowedByMappings(followedByMappings,
                                                          fhirPath,
                                                          definedMappingWithOpenEhr,
                                                          slotContext);

            prepareOpenEhrToFhirHelpers(theMapper,
                                        resourceType,
                                        firstFlatPath,
                                        followedByMappings,
                                        helpers,
                                        webTemplate,
                                        flatJsonObject,
                                        true,
                                        openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(),
                                                                                      mapping.getFhirCondition(),
                                                                                      resourceType,
                                                                                      parentFollowedByFhir),
                                        definedMappingWithOpenEhr == null ? firstFlatPath : definedMappingWithOpenEhr,
                                        slotContext,
                                        possibleRecursion);
        }
    }

    /**
     * Handles slot mapping, so when a mapping references a slot archetype
     */
    private void handleSlotMapping(final Mapping mapping, final String resourceType, final String parentFollowedByFhir,
                                   final OpenFhirFhirConnectModelMapper theMapper, final String firstFlatPath,
                                   final String definedMappingWithOpenEhr, final String fhirPath,
                                   final List<OpenEhrToFhirHelper> helpers, final WebTemplate webTemplate,
                                   final JsonObject flatJsonObject,
                                   final String slotContext, final String openehr, final boolean breakRecursion) {
        final String templateId = webTemplate.getTemplateId();

        final List<OpenFhirFhirConnectModelMapper> slotArchetypeMapperss = openFhirTemplateRepo.getMapperForArchetype(
                templateId, mapping.getSlotArchetype());
        if (slotArchetypeMapperss == null) {
            log.error("Couldn't find referenced slot archetype mapper {}. Referenced in {}", mapping.getSlotArchetype(),
                      mapping.getName());
            throw new IllegalArgumentException(
                    String.format("Couldn't find referenced slot archetype mapper %s. Referenced in %s",
                                  mapping.getSlotArchetype(),
                                  mapping.getName()));
        }
        for (final OpenFhirFhirConnectModelMapper slotArchetypeMappers : slotArchetypeMapperss) {
            boolean possibleRecursion = slotArchetypeMappers.getName().equals(theMapper.getName());
            if (breakRecursion && possibleRecursion) {
                log.warn("Breaking possible infinite recursion with mapping: {}", slotArchetypeMappers.getName());
                break;
            }

            openFhirMapperUtils.prepareForwardingSlotArchetypeMapper(slotArchetypeMappers, theMapper, fhirPath,
                                                                     getOpenEhrKey(definedMappingWithOpenEhr, null,
                                                                                   firstFlatPath));

            // recursively prepare all slot archetype mappers
            final String childWithParentFhirPath = openFhirStringUtils.setParentsWherePathToTheCorrectPlace(fhirPath,
                                                                                                            parentFollowedByFhir);
            prepareOpenEhrToFhirHelpers(slotArchetypeMappers, resourceType, firstFlatPath,
                                        slotArchetypeMappers.getMappings(),
                                        helpers, webTemplate, flatJsonObject, true, childWithParentFhirPath,
                                        definedMappingWithOpenEhr,
                                        definedMappingWithOpenEhr,
                                        possibleRecursion);

            // slot archetype can be followed by other mappers as well
            if (mapping.getFollowedBy() != null) {
                final List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();

                openFhirMapperUtils.prepareFollowedByMappings(followedByMappings,
                                                              fhirPath,
                                                              openehr,
                                                              firstFlatPath);

                prepareOpenEhrToFhirHelpers(theMapper, resourceType, firstFlatPath, followedByMappings, helpers,
                                            webTemplate,
                                            flatJsonObject, true,
                                            openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(),
                                                                                          mapping.getFhirCondition(),
                                                                                          resourceType,
                                                                                          parentFollowedByFhir),
                                            definedMappingWithOpenEhr, slotContext, possibleRecursion);
            }
        }
    }

    /**
     * Handles reference mappings (when type is $reference)
     */
    private void handleReferenceMapping(final Mapping mapping, final String resourceType,
                                        final String parentFollowedByFhir,
                                        final String parentFollowedByOpenEhr,
                                        final OpenFhirFhirConnectModelMapper theMapper, final String firstFlatPath,
                                        final String definedMappingWithOpenEhr, final String fhirPath,
                                        final boolean isFollowedBy,
                                        final List<OpenEhrToFhirHelper> helpers, final WebTemplate webTemplate,
                                        final JsonObject flatJsonObject,
                                        final String slotContext, final String openehr,
                                        final boolean possibleRecursion) {
        final List<Mapping> referencedMapping = mapping.getReference().getMappings();
        final String wConditions = openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(),
                                                                                 mapping.getFhirCondition(),
                                                                                 resourceType, parentFollowedByFhir);
        openFhirMapperUtils.prepareReferencedMappings(wConditions, openehr, referencedMapping, firstFlatPath);

        // now conditions
        if (mapping.getFhirCondition() != null) {
            // if condition of a $reference isn't null, we add it to generated helpers despite being a $reference only, since
            // condition itself needs to be evaluted and added to the generated FHIR
            addConditionInReferenceMapping(parentFollowedByOpenEhr, parentFollowedByFhir, theMapper, resourceType,
                                           firstFlatPath, definedMappingWithOpenEhr, fhirPath, mapping, isFollowedBy,
                                           helpers);
        }

        // narrow refeence mapping if openehr condition exists
        final Condition openEhrCondition = mapping.getReference().getMappings().get(0).getOpenehrCondition();
        if(openEhrCondition != null) {
            prepareOpenEhrCondition(openEhrCondition, firstFlatPath, webTemplate);

            final JsonObject newFlatJsonObject = openEhrConditionEvaluator.splitByOpenEhrCondition(flatJsonObject,
                                                                                                openEhrCondition,
                                                                                                parentFollowedByOpenEhr
                                                                                                        == null
                                                                                                        ? firstFlatPath
                                                                                                        : parentFollowedByOpenEhr);
            // newFlatJsonObject now has that one that is being referenced, but indexes will map to the wrong one
            // so now we fix indexes to point to all "other" ones that are having this one linked to it
            final HashSet<String> allOtherKeys = new HashSet<>(flatJsonObject.keySet());
            allOtherKeys.removeAll(newFlatJsonObject.keySet());

            final JsonObject modifiedObject = new JsonObject();
            for (final String key : allOtherKeys) {
                final Integer anotherIndex = openFhirStringUtils.getFirstIndex(key);
                if(anotherIndex == null) {
                    continue;
                }
                // now modify the newFlatJsonObject with this new index
                for (final Entry<String, JsonElement> stringJsonElementEntry : newFlatJsonObject.entrySet()) {
                    final String key1 = stringJsonElementEntry.getKey();
                    final JsonElement value1 = stringJsonElementEntry.getValue();
                    modifiedObject.add(openFhirStringUtils.replaceFirstIndex(key1, anotherIndex), value1);
                }
            }

            // recursive call so all $reference.mappings are handled
            prepareOpenEhrToFhirHelpers(theMapper,
                                        mapping.getReference().getResourceType(),
                                        firstFlatPath,
                                        mapping.getReference().getMappings(),
                                        helpers,
                                        webTemplate,
                                        modifiedObject,
                                        isFollowedBy,
                                        parentFollowedByFhir,
                                        parentFollowedByOpenEhr,
                                        slotContext,
                                        possibleRecursion);
        } else {
            // recursive call so all $reference.mappings are handled
            prepareOpenEhrToFhirHelpers(theMapper,
                                        mapping.getReference().getResourceType(),
                                        firstFlatPath,
                                        mapping.getReference().getMappings(),
                                        helpers,
                                        webTemplate,
                                        flatJsonObject,
                                        isFollowedBy,
                                        parentFollowedByFhir,
                                        parentFollowedByOpenEhr,
                                        slotContext,
                                        possibleRecursion);
        }

    }

    /**
     * Extracts values from joinedEntries and creates data points from that together with an index according
     * to flat path
     *
     * @param mapping mapping currently being evaluated
     * @param joinedEntries flat paths joined together
     * @param rmType type of the data point
     * @param flatJsonObject json object representing flat path format of a Composition
     * @param hardcodedValue if there is no mapping but rather a hardcoding, this will hold a value that needs
     *         to be
     *         hardcoded
     */
    private List<OpenEhrToFhirHelper.DataWithIndex> extractValues(final Mapping mapping,
                                                                  final Map<String, List<String>> joinedEntries,
                                                                  final String rmType,
                                                                  final JsonObject flatJsonObject,
                                                                  final String hardcodedValue) {
        List<OpenEhrToFhirHelper.DataWithIndex> values = null;
        if (!OPENEHR_TYPE_NONE.equals(mapping.getWith().getType())) {
            if (StringUtils.isNotEmpty(hardcodedValue) && !joinedEntries.isEmpty()) {
                values = new ArrayList<>();
                final Condition openehrCondition = mapping.getOpenehrCondition();
                final String fullOpenEhrPath;
                if (openehrCondition == null) {
                    fullOpenEhrPath = OPENEHR_ARCHETYPE_FC;
                } else {
                    final List<String> targetAttributes = mapping.getOpenehrCondition().getTargetAttributes();
                    final String targetRoot = mapping.getOpenehrCondition().getTargetRoot();
//                    final String rootWithAttrs = targetRoot + ((targetAttributes != null && !targetAttributes.isEmpty()) ? "" : ("/" + targetAttributes.get(0)));
                    final String piped = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(targetRoot);
                    final List<String> allEntriesThatMatch = openFhirStringUtils.getAllEntriesThatMatch(piped,
                                                                                                        flatJsonObject);
                    fullOpenEhrPath = allEntriesThatMatch.get(0);
                }
                int index = getHardcodedIndex(mapping, flatJsonObject);
                if (index == -1) {
                    // get outer most index of all indexes in flatJsonObject that is the same for all entries, because
                    // while -1 means it has to be for all entries, it more than that means it has to be for all entries
                    // on the currently evaluated items!
                    index = openFhirStringUtils.getLastMostCommonIndex(new ArrayList<>(flatJsonObject.keySet()));
                }
                values.add(new OpenEhrToFhirHelper.DataWithIndex(new StringType(hardcodedValue), index,
                                                                 fullOpenEhrPath));
            } else {
                values = joinedEntries.values().stream()
                        .map(strings -> valueToDataPoint(strings, rmType, flatJsonObject, true))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } else if (mapping.getFhirCondition() != null) {
            values = joinedEntries.values().stream()
                    .map(strings -> valueToDataPoint(strings, rmType, flatJsonObject, false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return values;
    }

    private int getHardcodedIndex(final Mapping mapping, final JsonObject flatJsonObject) {
        if (mapping.getOpenehrCondition() == null) {
            return -1; // goes for all anyway
        }
        final String conditionRoot = mapping.getOpenehrCondition().getTargetRoot();
        final String matchingKey = flatJsonObject.keySet().stream().filter(k -> k.startsWith(conditionRoot)).findFirst()
                .orElse(null); // should always be at least one! else we woudln't be here
        if (matchingKey == null) {
            return -1;
        }
        return openFhirStringUtils.getIndexOfElement(conditionRoot, matchingKey);
    }

    private String getRmType(final String openEhrPath,
                             final Mapping mapping,
                             final WebTemplate webTemplate) {
        if (mapping.getWith().getType() == null) {
            // if type is not explicitly defined in a fhir connect model mapper, it is taken from the template definition
            final FhirToOpenEhrHelper getTypeHelper = FhirToOpenEhrHelper.builder()
                    .openEhrPath(openEhrPath)
                    .build();
            openEhrRmWorker.fixFlatWithOccurrences(Collections.singletonList(getTypeHelper), webTemplate);
            return getTypeHelper.getOpenEhrType();
        } else {
            return mapping.getWith().getType();
        }
    }

    /**
     * Sorts datas by last index, else cache doesn't populate correctly
     */
    private void sortByLastIndex(final List<OpenEhrToFhirHelper.DataWithIndex> datas) {
        Collections.sort(datas, (o1, o2) -> {
            // Extract numbers from the strings
            int num1 = openFhirStringUtils.getLastIndex(o1.getFullOpenEhrPath());
            int num2 = openFhirStringUtils.getLastIndex(o2.getFullOpenEhrPath());

            // Compare the numbers
            return Integer.compare(num1, num2);
        });
    }

    /**
     * When something exists in 'removedPath', it means something was found within the cache but not he full path
     * and the remaining part of the path needs to be created (instantiated) and populated in the cache
     */
    private FhirInstanceCreator.InstantiateAndSetReturn handleRemovedPathInstantiation(
            final FindingOuterMost findingOuterMost,
            final OpenEhrToFhirHelper helper,
            final Resource instance,
            final String fhirPathWithConditions,
            final String generatingResource,
            final String fullOpenEhrPath,
            final Map<String, Object> instantiatedIntermediateElements) {
        final String whereInRemovedPath =
                findingOuterMost.getRemovedPath() != null ? openFhirStringUtils.extractWhereCondition(
                        findingOuterMost.getRemovedPath()) : null;
        boolean removedPathIsOnlyWhere = findingOuterMost.getRemovedPath() != null
                && whereInRemovedPath != null
                && (whereInRemovedPath.equals(findingOuterMost.getRemovedPath())
                || ("." + whereInRemovedPath).equals(findingOuterMost.getRemovedPath()));

        handleReturnedListWithWhereCondition(findingOuterMost);

        // instantiate an element defined in the findingOuterMost.getRemovedPath
        final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(
                findingOuterMost.getLastObject(),
                findingOuterMost.getLastObject().getClass(),
                removedPathIsOnlyWhere ? THIS : findingOuterMost.getRemovedPath(),
                openFhirMapperUtils.getFhirConnectTypeToFhir(helper.getOpenEhrType()),
                helper.getTargetResource());

        cacheReturnedItems(findingOuterMost,
                           hardcodedReturn,
                           instance,
                           fhirPathWithConditions,
                           generatingResource,
                           removedPathIsOnlyWhere,
                           fullOpenEhrPath,
                           instantiatedIntermediateElements,
                           helper);

        return hardcodedReturn;
    }

    /**
     * Post processing that handles hard-coding of Resource data based on FhirConfig conditions in the header
     * of each mapping
     *
     * @param createdResources all created resources
     * @param conditions conditions in the header of a mapping
     */
    private void postProcessMappingFromCoverConditions(final List<Resource> createdResources,
                                                       final List<Condition> conditions) {
        for (Resource createdResource : createdResources) {
            if (conditions == null || conditions.isEmpty()) {
                continue;
            }
            for (Condition condition : conditions) {
                if (condition.getCriteria() == null || CONDITION_OPERATOR_NOT_OF.equals(condition.getOperator())) {
                    continue;
                }

                final String conditionFhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(
                        condition.getTargetRoot(), condition, createdResource.fhirType(), null);

                // check if it exists
                final List<Base> alreadyExists = fhirPathR4.evaluate(createdResource, conditionFhirPathWithConditions,
                                                                     Base.class);
                if (alreadyExists != null && !alreadyExists.isEmpty()) {
                    // all good
                    log.debug("Cover condition already exists on the resource, doing nothing");
                } else {
                    final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(
                            createdResource,
                            createdResource.getClass(),
                            condition.getTargetRoot() + "." + condition.getTargetAttribute(),
                            null);

                    final Object toSetCriteriaOn = getLastReturn(hardcodedReturn).getReturning();
                    final Coding stringFromCriteria = openFhirStringUtils.getStringFromCriteria(
                            condition.getCriteria());
                    fhirInstancePopulator.populateElement(toSetCriteriaOn,
                                                          new StringType(stringFromCriteria.getCode()));
                }
            }
        }
    }

    private void handleMapping(final OpenEhrToFhirHelper.DataWithIndex data,
                               final Map<String, Resource> createdPerIndex,
                               final Resource instance,
                               final String fullOpenEhrPath,
                               final String generatingResource,
                               final OpenEhrToFhirHelper helper,
                               final Map<String, Object> instantiatedIntermediateElements,
                               final List<Resource> separatelyCreatedResources,
                               final String mapKey) {

        final String fhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(helper.getFhirPath(),
                                                                                            helper.getCondition(),
                                                                                            helper.getTargetResource(),
                                                                                            helper.getParentFollowedByFhirPath());
        log.debug("Processing data point from openEhr {}, value : {}, fhirPath: {}", data.getFullOpenEhrPath(),
                  data.getData().getClass(), fhirPathWithConditions);

        // based on the fhir path and openehr path (and conditions), we try to find an existing intermediary item
        // if none is found, we create one and add it to the cache for later mappings that may relate to this same
        // element we've created just now
        final FindingOuterMost findingOuterMost = getOrInstantiateIntermediateItem(instantiatedIntermediateElements,
                                                                                   instance,
                                                                                   fhirPathWithConditions,
                                                                                   helper.getOpenEhrType(),
                                                                                   helper.getTargetResource(),
                                                                                   fullOpenEhrPath,
                                                                                   helper.isFollowedBy(),
                                                                                   helper.getParentFollowedByFhirPath(),
                                                                                   helper.getParentFollowedByOpenEhr(),
                                                                                   separatelyCreatedResources);

        // it means that something needs to be created, because the full fhir path was not actually found
        // in the intermediary cache
        if (StringUtils.isNotEmpty(findingOuterMost.getRemovedPath())) {

            final FhirInstanceCreator.InstantiateAndSetReturn instantiatedFromRemovedPath = handleRemovedPathInstantiation(
                    findingOuterMost, helper, instance, fhirPathWithConditions, generatingResource,
                    fullOpenEhrPath, instantiatedIntermediateElements);

            // populate instantiated element with the data obtained from the flat path (now represented with 'data')
            fhirInstancePopulator.populateElement(getLastReturn(instantiatedFromRemovedPath).getReturning(), data);
        } else {
            fhirInstancePopulator.populateElement(findingOuterMost.getLastObject(), data);
        }


        // after the element has been instantiated and populated, handle conditions, meaning
        // handle hardcoded things in the mapping (i.e. of a condition states url of an extension needs to be
        // something, this is handled in the handleConditionMapping
        handleConditionMapping(helper.getCondition(),
                               instance,
                               fullOpenEhrPath,
                               instantiatedIntermediateElements,
                               helper.getTargetResource(),
                               helper.isFollowedBy(),
                               helper.getParentFollowedByFhirPath(),
                               helper.getParentFollowedByOpenEhr());

        if (createdPerIndex != null) {
            createdPerIndex.put(mapKey, instance);
        }
    }

    /**
     * Returns the inner-est element in the InstantiateAndSetReturn object, since that's the one we need to populate.
     * Method loops over inner elements recursively until it reaches the last one.
     */
    private FhirInstanceCreator.InstantiateAndSetReturn getLastReturn(
            final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn) {
        if (instantiateAndSetReturn.getInner() == null) {
            return instantiateAndSetReturn;
        }
        return getLastReturn(instantiateAndSetReturn.getInner());
    }

    /**
     * i.e.
     * growth_chart/body_weight/any_event:2/weight|unit
     * and
     * growth_chart/body_weight/any_event:2/weight|magnitude
     * will be joined together, as they are a single object
     */
    public Map<String, List<String>> joinValuesThatAreOne(final List<String> matchingEntries) {
        final Map<String, List<String>> matchings = new HashMap<>();
        for (String matchingEntry : matchingEntries) {
            final String[] split = matchingEntry.split("\\|");
            final String root = split[0];
            if (split.length == 1) {
                final List<String> list = new ArrayList<>();
                list.add(root);
                matchings.put(root, list);
            } else {
                if (!matchings.containsKey(root)) {
                    matchings.put(root, new ArrayList<>());
                }
                matchings.get(root).add(matchingEntry);
            }
        }
        return matchings;
    }

    /**
     * Creates datapoints from extracted values from the given flat path format
     *
     * @param joinedValues flat path values as extracted from the given Composition in flat path format
     * @param targetType target openEHR data type
     * @param valueHolder original json object containing all the data
     * @param canBeNull if returned data can be null, if false, a generic StringType will be added to the
     *         returned object
     * @return OpenEhrToFhirHelper.DataWithIndex with index and data populated
     */
    private OpenEhrToFhirHelper.DataWithIndex valueToDataPoint(final List<String> joinedValues,
                                                               final String targetType,
                                                               final JsonObject valueHolder,
                                                               final boolean canBeNull) {
        if (joinedValues == null || joinedValues.isEmpty()) {
            return null;
        }

        final String path = joinedValues.get(0);
        final Integer lastIndex = openFhirStringUtils.getLastIndex(path);

        // Fetching common values once
        final String value = fetchValue(joinedValues, "value");
        final String code = fetchValue(joinedValues, "code");
        final String terminology = fetchValue(joinedValues, "terminology");
        // iterate and find all mappings as well
            // fetchValue(joinedValues, "_mapping:i/target|terminology")
            // fetchValue(joinedValues, "_mapping:i/target|code")
            // fetchValue(joinedValues, "_mapping:i/target|preferred_term")
        final String id = fetchValue(joinedValues, "id");

        return switch (targetType) {
            case "PROPORTION" -> handleProportion(joinedValues, valueHolder, lastIndex, path);
            case "QUANTITY" -> handleQuantity(joinedValues, valueHolder, lastIndex, path, value, code);
            case "DATETIME" -> handleDateTime(valueHolder, lastIndex, path);
            case "TIME" -> handleTime(valueHolder, lastIndex, path);
            case "BOOL" -> handleBoolean(valueHolder, lastIndex, path);
            case "DATE" -> handleDate(valueHolder, lastIndex, path);
            case "CODEABLECONCEPT" -> handleCodeableConcept(valueHolder, lastIndex, path, value, terminology, code); //to add mappings);
            case "CODING" -> handleCoding(valueHolder, lastIndex, path, terminology, code, value);
            case "MEDIA" -> handleMedia(valueHolder, lastIndex, path);
            case "IDENTIFIER" -> handleIdentifier(valueHolder, lastIndex, path, id);
            default -> handleString(valueHolder, lastIndex, path, canBeNull);
        };
    }

    private String fetchValue(final List<String> joinedValues, final String suffix) {
        return joinedValues.stream()
                .filter(s -> s.endsWith(suffix))
                .findAny()
                .orElse(null);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleProportion(final List<String> joinedValues,
                                                               final JsonObject valueHolder,
                                                               final Integer lastIndex,
                                                               final String path) {
        final String proportionVal = joinedValues.get(0);
        final String numerator = proportionVal + "|numerator";
        final String denominator = proportionVal + "|denominator";

        final Quantity proportionQuantity = new Quantity();
        final String proportionValueHolder = getFromValueHolder(valueHolder, denominator);
        if ("100.0".equals(proportionValueHolder)) {
            proportionQuantity.setCode("%");
            proportionQuantity.setUnit("percent");
            proportionQuantity.setSystem("http://unitsofmeasure.org");
        }

        final Object valueToAdd = getDoubleOrLong(getFromValueHolder(valueHolder, numerator));
        if (valueToAdd instanceof Long) {
            proportionQuantity.setValue((Long) valueToAdd);
        } else if (valueToAdd instanceof Double) {
            proportionQuantity.setValue((Double) valueToAdd);
        }

        return new OpenEhrToFhirHelper.DataWithIndex(proportionQuantity, lastIndex, path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleQuantity(final List<String> joinedValues,
                                                             final JsonObject valueHolder,
                                                             final Integer lastIndex,
                                                             final String path,
                                                             final String value,
                                                             final String code) {
        final String magnitude = fetchValue(joinedValues, "magnitude");
        final String unit = fetchValue(joinedValues, "unit");
        final String ordinal = fetchValue(joinedValues, "ordinal");

        final Quantity quantity = new Quantity();
        setQuantityValue(valueHolder, quantity, magnitude, ordinal);

        if (unit != null) {
            quantity.setUnit(getFromValueHolder(valueHolder, unit));
        }
        if (value != null) {
            quantity.setUnit(getFromValueHolder(valueHolder, value));
        }
        if (code != null) {
            quantity.setCode(getFromValueHolder(valueHolder, code));
        }

        if (magnitude == null && ordinal == null && unit == null && value == null && code == null) {
            setQuantityFromPath(valueHolder, quantity, path);
        }

        return new OpenEhrToFhirHelper.DataWithIndex(quantity, lastIndex, path);
    }

    private void setQuantityValue(final JsonObject valueHolder, final Quantity quantity,
                                  final String magnitude, final String ordinal) {
        if (magnitude != null) {
            final Object magVal = getDoubleOrLong(getFromValueHolder(valueHolder, magnitude));
            if (magVal != null) {
                quantity.setValue(magVal instanceof Long ? (Long) magVal : (Double) magVal);
            }
        } else if (ordinal != null) {
            final Object ordVal = getDoubleOrLong(getFromValueHolder(valueHolder, ordinal));
            if (ordVal instanceof Long) {
                quantity.setValue((Long) ordVal);
            } else if (ordVal instanceof Double) {
                quantity.setValue((Double) ordVal);
            }
        }
    }

    private void setQuantityFromPath(final JsonObject valueHolder, final Quantity quantity,
                                     final String path) {
        final Object pathVal = getDoubleOrLong(getFromValueHolder(valueHolder, path));
        if (pathVal instanceof Long) {
            quantity.setValue((Long) pathVal);
        } else if (pathVal instanceof Double) {
            quantity.setValue((Double) pathVal);
        }
    }

    private OpenEhrToFhirHelper.DataWithIndex handleDateTime(final JsonObject valueHolder,
                                                             final Integer lastIndex,
                                                             final String path) {
        final DateTimeType dateTimeType = new DateTimeType();
        dateTimeType.setValue(openFhirMapperUtils.stringToDate(getFromValueHolder(valueHolder, path)));
        return new OpenEhrToFhirHelper.DataWithIndex(dateTimeType, lastIndex, path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleTime(final JsonObject valueHolder,
                                                         final Integer lastIndex,
                                                         final String path) {
        final TimeType timeType = new TimeType();
        timeType.setValue(getFromValueHolder(valueHolder, path));
        return new OpenEhrToFhirHelper.DataWithIndex(timeType, lastIndex, path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleBoolean(final JsonObject valueHolder,
                                                            final Integer lastIndex,
                                                            final String path) {
        final BooleanType booleanType = new BooleanType();
        booleanType.setValue(Boolean.valueOf(getFromValueHolder(valueHolder, path)));
        return new OpenEhrToFhirHelper.DataWithIndex(booleanType, lastIndex, path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleDate(final JsonObject valueHolder,
                                                         final Integer lastIndex,
                                                         final String path) {
        final DateType dateType = new DateType();
        dateType.setValue(openFhirMapperUtils.stringToDate(getFromValueHolder(valueHolder, path)));
        return new OpenEhrToFhirHelper.DataWithIndex(dateType, lastIndex, path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleCodeableConcept(final JsonObject valueHolder,
                                                                    final Integer lastIndex,
                                                                    final String path,
                                                                    final String value,
                                                                    final String terminology,
                                                                    final String code) {
        final CodeableConcept data = new CodeableConcept();
        
        // Get values using the full path
        final String textValue = getFromValueHolder(valueHolder, path + "|value");
        final String codeValue = getFromValueHolder(valueHolder, path + "|code");
        final String systemValue = getFromValueHolder(valueHolder, path + "|terminology");
        
        data.setText(textValue);
        
        // Add the primary coding
        if (codeValue != null || systemValue != null) {
            data.addCoding(new Coding(systemValue, codeValue, textValue));
        }
        
        // Process additional mappings
        processMappings(valueHolder, path, data);
        
        return new OpenEhrToFhirHelper.DataWithIndex(data, lastIndex, path);
    }

    /**
     * Process mappings for a given path and add them as codings to the CodeableConcept
     * 
     * @param valueHolder JSON object containing the values
     * @param path Base path to look for mappings
     * @param codeableConcept CodeableConcept to add mappings to
     */
    private void processMappings(final JsonObject valueHolder, final String path, final CodeableConcept codeableConcept) {
        int mappingIndex = 0;
        String mappingPrefix;
        
        // Iterate through all possible mappings
        while (true) {
            mappingPrefix = path + "/_mapping:" + mappingIndex;
            
            // Check if this mapping exists by looking for any related key
            boolean mappingExists = false;
            for (String key : valueHolder.keySet()) {
                if (key.startsWith(mappingPrefix)) {
                    mappingExists = true;
                    break;
                }
            }
            
            if (!mappingExists) {
                break;
            }
            
            String terminology = getFromValueHolder(valueHolder, mappingPrefix + "/target|terminology");
            String code = getFromValueHolder(valueHolder, mappingPrefix + "/target|code");
            String preferredTerm = getFromValueHolder(valueHolder, mappingPrefix + "/target|preferred_term");
            
            // Add mapping as a coding if we have at least system or code
            if (terminology != null || code != null) {
                codeableConcept.addCoding(new Coding(terminology, code, preferredTerm));
            }
            
            mappingIndex++;
        }
    }

    private OpenEhrToFhirHelper.DataWithIndex handleCoding(final JsonObject valueHolder,
                                                           final Integer lastIndex,
                                                           final String path,
                                                           final String terminology,
                                                           final String code,
                                                           final String value) {
        return new OpenEhrToFhirHelper.DataWithIndex(new Coding(getFromValueHolder(valueHolder, terminology),
                                                                getFromValueHolder(valueHolder, code),
                                                                getFromValueHolder(valueHolder, value)),
                                                     lastIndex,
                                                     path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleMedia(final JsonObject valueHolder,
                                                          final Integer lastIndex,
                                                          final String path) {
        final Attachment att = new Attachment();
        att.setContentType(getFromValueHolder(valueHolder, path + "|mediatype"));

        final String size = getFromValueHolder(valueHolder, path + "|size");
        if (size != null) {
            att.setSize(Integer.parseInt(size));
        }

        att.setUrl(getFromValueHolder(valueHolder, path + "|url"));

        final String dataBytes = getFromValueHolder(valueHolder, path + "|data");
        att.setData(dataBytes == null ? null : dataBytes.getBytes(StandardCharsets.UTF_8));

        return new OpenEhrToFhirHelper.DataWithIndex(att, lastIndex, path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleIdentifier(final JsonObject valueHolder,
                                                               final Integer lastIndex,
                                                               final String path,
                                                               final String id) {
        final Identifier identifier = new Identifier();
        identifier.setValue(getFromValueHolder(valueHolder, StringUtils.isEmpty(id) ? (path + "|id") : id));
        return new OpenEhrToFhirHelper.DataWithIndex(identifier, lastIndex, path);
    }

    private OpenEhrToFhirHelper.DataWithIndex handleString(final JsonObject valueHolder,
                                                           final Integer lastIndex,
                                                           final String path,
                                                           final boolean canBeNull) {
        final String fromValueHolder = getFromValueHolder(valueHolder, path);
        if (StringUtils.isNotEmpty(fromValueHolder)) {
            return new OpenEhrToFhirHelper.DataWithIndex(new StringType(fromValueHolder), lastIndex, path);
        } else if (canBeNull) {
            return null;
        } else {
            return new OpenEhrToFhirHelper.DataWithIndex(new StringType(), lastIndex, path);
        }
    }


    /**
     * if condition of a $reference isn't null, we add it to generated helpers despite being a $reference only, since
     * condition itself needs to be evaluted and added to the generated FHIR
     */
    private void addConditionInReferenceMapping(final String parentFollowedByOpenEhr, final String parentFollowedByFhir,
                                                final OpenFhirFhirConnectModelMapper theMapper,
                                                final String resourceType, final String firstFlatPath,
                                                final String definedMappingWithOpenEhr, final String fhirPath,
                                                final Mapping mapping, final boolean isFollowedBy,
                                                final List<OpenEhrToFhirHelper> helpers) {
        final String parentFollowedByFhirPath = parentFollowedByFhir == null ? null
                : parentFollowedByFhir.replace(FhirConnectConst.FHIR_RESOURCE_FC, resourceType);
        final String parentFollowedByOpenEhr1 = parentFollowedByOpenEhr == null ? null
                : parentFollowedByOpenEhr.replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, firstFlatPath);
        OpenEhrToFhirHelper openEhrToFhirHelper = OpenEhrToFhirHelper.builder()
                .mainArchetype(theMapper.getOpenEhrConfig().getArchetype())
                .targetResource(resourceType)
                .openEhrPath(definedMappingWithOpenEhr)
                .fhirPath(
                        fhirPath) // fhir path here should not have the full where yada yada, rather just the normal path to the data point, resource limiting is done in other places
                .openEhrType(mapping.getWith().getType())
                .data(new ArrayList<>())
                .isFollowedBy(isFollowedBy)
                .parentFollowedByFhirPath(parentFollowedByFhirPath)
                .parentFollowedByOpenEhr(parentFollowedByOpenEhr1)
                .condition(mapping.getFhirCondition())
                .openehrCondition(mapping.getOpenehrCondition())
                .build();
        helpers.add(openEhrToFhirHelper);
    }

    private String getOpenEhrKey(final String definedMappingWithOpenEhr,
                                 final String parentFollowedByOpenEhr,
                                 final String firstFlatPath) {
        if (StringUtils.isEmpty(definedMappingWithOpenEhr)) {
            return parentFollowedByOpenEhr == null ? firstFlatPath : parentFollowedByOpenEhr;
        } else {
            return openFhirStringUtils.prepareOpenEhrSyntax(definedMappingWithOpenEhr, firstFlatPath);
        }
    }

    private Object getDoubleOrLong(final String value) {
        if (StringUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (final Exception e) {
            return Double.parseDouble(value);
        }
    }

    private String getFromValueHolder(final JsonObject valueHolder, final String path) {
        if (valueHolder.has(path)) {
            return valueHolder.get(path).getAsString();
        }
        return null;
    }

    @AllArgsConstructor
    @Data
    public static class FindingOuterMost {

        public Object lastObject;
        public String removedPath;
    }
}
