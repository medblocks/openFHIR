package com.medblocks.openfhir.tofhir;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.model.Condition;
import com.medblocks.openfhir.fc.model.*;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import com.medblocks.openfhir.util.*;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.*;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.*;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RESOLVE;

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
    final private FhirPathR4 fhirPathR4;


    @Autowired
    public OpenEhrToFhir(final FlatJsonMarshaller flatJsonMarshaller,
                         final OpenFhirMappingContext openFhirTemplateRepo,
                         final OpenEhrCachedUtils openEhrApplicationScopedUtils,
                         final Gson gson, OpenFhirStringUtils openFhirStringUtils,
                         final OpenEhrRmWorker openEhrRmWorker,
                         final OpenFhirMapperUtils openFhirMapperUtils,
                         final FhirInstancePopulator fhirInstancePopulator,
                         final FhirInstanceCreator fhirInstanceCreator,
                         final FhirPathR4 fhirPathR4) {
        this.flatJsonMarshaller = flatJsonMarshaller;
        this.openFhirTemplateRepo = openFhirTemplateRepo;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.gson = gson;
        this.openFhirStringUtils = openFhirStringUtils;
        this.openEhrRmWorker = openEhrRmWorker;
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.fhirInstancePopulator = fhirInstancePopulator;
        this.fhirInstanceCreator = fhirInstanceCreator;
        this.fhirPathR4 = fhirPathR4;
    }

    /**
     * Main method that handles business logic of mapping incoming OpenEHR Composition to a FHIR Bundle
     *
     * @param context             fhir connect context mapper
     * @param composition         incoming Composition that needs to be mapped (this is serialized immediately to a
     *                            flat json format, meaning if it already comes like this to the openFHIR engine,
     *                            we're doing 2 de/serializations; rethink if it makes sense or not - right not, this is
     *                            also how we're implicitly validating incoming request, but that could be done smarter)
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
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(context.getOpenEHR().getTemplateId());
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
            final List<FhirConnectMapper> theMappers = openFhirTemplateRepo.getMapperForArchetype(templateId, archetypeNodeId);
            if (theMappers == null) {
                log.error("No mappers defined for archetype within this composition: {}. No mapping possible.", archetypeNodeId);
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
     * @param theMappers                       fhir connect mappers available for mapping
     * @param createdAndAdded                  set of string of already created Resources, so we don't do duplicates
     * @param isMultipleByResourceType         if certain mapping produces multiple resources
     * @param flatJsonObject                   Composition in a flat json format that needs to be mapped
     * @param webTemplate                      web template of the inbound Composition
     * @param instantiatedIntermediateElements elements instantiated throughout the mapping (FHIR dataelements instantiated,
     * @param intermediateCaches               cached intermediate caches per Resource type
     *                                         key'd by created object + fhir path + openehr path)
     * @param creatingBundle                   Bundle that is being created as part of the mappings
     * @param archetypesAlreadyProcessed       set of archetypes already processed
     * @param archetypesWithinContent          archetype within a Composition that is currently being mapped
     * @param archetypeNodeId                  archetype id within a Composition that is currently being mapped
     */
    private void handleMappings(final List<FhirConnectMapper> theMappers,
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
        for (final FhirConnectMapper theMapper : theMappers) {
            if (theMapper.getFhirConfig() == null) {
                // if fhir config is null, it means it's a slot mapper and it can't be a first-level Composition.content one
                continue;
            }
            final Boolean existingEntry = isMultipleByResourceType.getOrDefault(theMapper.getFhirConfig().getResource(), true);

            // fhirConfig.multiple signals if model mapper should return in multiple base FHIR Resources or a single one
            // if not multiple, then we need to get an existing already created FHIR Resource and use that one for the
            // following mappings
            final boolean shouldUseExisting = existingEntry && !theMapper.getFhirConfig().getMultiple();
            isMultipleByResourceType.put(theMapper.getFhirConfig().getResource(), shouldUseExisting);
            intermediateCaches.put(theMapper.getFhirConfig().getResource(), intermediateCaches.getOrDefault(theMapper.getFhirConfig().getResource(), instantiatedIntermediateElements));

            // helper POJOs that help for openEHR to FHIR mappings
            final List<OpenEhrToFhirHelper> helpers = new ArrayList<>();

            prepareOpenEhrToFhirHelpers(theMapper,
                    theMapper.getFhirConfig().getResource(),
                    webTemplate.getTree().getId(),
                    theMapper.getMappings(),
                    helpers,
                    webTemplate,
                    flatJsonObject,
                    false,
                    null,
                    null,
                    webTemplate.getTree().getId());

            // within helpers, you should have everything you need to create a FHIR Resource now
            final List<Resource> created = createResourceFromOpenEhrToFhirHelper(helpers,
                    theMapper.getFhirConfig(),
                    shouldUseExisting ? creatingBundle.getEntry()
                            .stream()
                            .map(Bundle.BundleEntryComponent::getResource)
                            .filter(en -> en.getResourceType().name().equals(theMapper.getFhirConfig().getResource()))
                            .findAny()
                            .orElse(null) : null,
                    shouldUseExisting ? intermediateCaches.getOrDefault(theMapper.getFhirConfig().getResource(), instantiatedIntermediateElements) : instantiatedIntermediateElements);

            log.info("Constructed {} resources for archetype {}.", created.size(), archetypesWithinContent.getArchetypeNodeId());

            addEntriesToBundle(creatingBundle, created, createdAndAdded);
            archetypesAlreadyProcessed.add(archetypeNodeId);
        }
    }

    /**
     * Prepares Bundle that is being created. This method should handle references between resources,
     * Bundle metadata, ....
     *
     * @return
     */
    private Bundle prepareBundle() {
        return new Bundle(); // todo: metadatas
    }

    /**
     * Utility method to add a Resource to a Bundle.entry
     *
     * @param bundle   that is being created
     * @param resource that needs to be added to the Bundle
     */
    private void addEntryToBundle(final Bundle bundle, final Resource resource) {
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(resource));
    }

    /**
     * Utility method to add a Resource to a Bundle.entry
     *
     * @param bundle          that is being created
     * @param resources       that need to be added to the Bundle
     * @param createdAndAdded hash code of resources that were already added to the Bundle, to avoid duplicated entries
     *                        being added
     */
    private void addEntriesToBundle(final Bundle bundle, final List<Resource> resources, final Set<String> createdAndAdded) {

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
     * @param helpers                          that present helpers for mapping
     * @param fhirConfig                       config of a specific fhir connect mapping
     * @param existingCreatingResource         resource that was already created as part of previous mappings (can be null)
     * @param instantiatedIntermediateElements elements instantiated with other preceding mappings
     * @return created Resources
     */
    private List<Resource> createResourceFromOpenEhrToFhirHelper(final List<OpenEhrToFhirHelper> helpers,
                                                                 final FhirConfig fhirConfig,
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

            final String conditionLog = helper.getCondition() == null ? "" : (helper.getCondition().getTargetRoot() + " where " + helper.getCondition().getTargetAttribute() + " " + helper.getCondition().getOperator() + " " + helper.getCondition().getCriteria());
            log.debug("Processing: archetpye '{}', targetResource '{}', fhirPath '{}', openEhrPath '{}', openEhrType '{}', data size '{}', condition '{}', parentFhirPath '{}', parentOpenEhrPath '{}'",
                    helper.getMainArchetype(), helper.getTargetResource(), helper.getFhirPath(), helper.getOpenEhrPath(), helper.getOpenEhrType(), helper.getData().size(), conditionLog, helper.getParentFollowedByFhirPath(), helper.getOpenEhrPath());

            final List<OpenEhrToFhirHelper.DataWithIndex> datas = helper.getData();
            if (datas.isEmpty()) {
                log.warn("No data has been parsed for path: {}", helper.getOpenEhrPath());
            }

            // datas need to be ordered by last index, else cache won't be populated correctly
            Collections.sort(datas, (o1, o2) -> {
                // Extract numbers from the strings
                int num1 = openFhirStringUtils.getLastIndex(o1.getFullOpenEhrPath());
                int num2 = openFhirStringUtils.getLastIndex(o2.getFullOpenEhrPath());

                // Compare the numbers
                return Integer.compare(num1, num2);
            });

            for (final OpenEhrToFhirHelper.DataWithIndex data : datas) {
                if (data.getIndex() == -1) {
                    // -1 means it's for all Resources, it's handled afterward, below
                    continue;
                }
                final String fullOpenEhrPath = data.getFullOpenEhrPath();

                // first index within the openehr flat path represents occurrence of the main resource being created
                final Integer index = fhirConfig.getMultiple() ? openFhirStringUtils.getFirstIndex(fullOpenEhrPath) : 0;
                final String mapKey = createKey(index, conditioningFhirPath);

                final Resource instance = getOrCreateResource(createdPerIndex, generatingResource, mapKey);

                // if openEhr type is NONE, we don't do any kind of mapping EXCEPT the condition one, if condition exists
                if (OPENEHR_TYPE_NONE.equals(helper.getOpenEhrType())) {
                    handleConditionMapping(helper.getCondition(),
                            instance,
                            fullOpenEhrPath,
                            instantiatedIntermediateElements,
                            helper.getTargetResource(),
                            helper.isFollowedBy(),
                            helper.getParentFollowedByFhirPath(),
                            helper.getParentFollowedByOpenEhr());

                    createdPerIndex.put(mapKey, instance);
                    continue;
                }


                final String fhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(helper.getFhirPath(), helper.getCondition(), helper.getTargetResource(), helper.getParentFollowedByFhirPath());
                log.debug("Processing data point from openEhr {}, value : {}, fhirPath: {}", data.getFullOpenEhrPath(), data.getData().getClass(), fhirPathWithConditions);

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

                    final String whereInRemovedPath = findingOuterMost.getRemovedPath() != null ? openFhirStringUtils.extractWhereCondition(findingOuterMost.getRemovedPath()) : null;
                    boolean removedPathIsOnlyWhere = findingOuterMost.getRemovedPath() != null
                            && whereInRemovedPath != null
                            && (whereInRemovedPath.equals(findingOuterMost.getRemovedPath())
                            || ("." + whereInRemovedPath).equals(findingOuterMost.getRemovedPath()));

                    handleReturnedListWithWhereCondition(findingOuterMost);

                    // instantiate an element defined in the findingOuterMost.getRemovedPath
                    final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(findingOuterMost.getLastObject(),
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

                    // populate instantiated element with the data obtained from the flat path (now represented with 'data')
                    fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(), data);
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

                createdPerIndex.put(mapKey, instance);
            }

            for (OpenEhrToFhirHelper.DataWithIndex dataForAllResources : datas.stream().filter(data -> data.getIndex() == -1).toList()) {
                final String fullOpenEhrPath = dataForAllResources.getFullOpenEhrPath();
                final ArrayList<Resource> resources = new ArrayList<>(createdPerIndex.values());
                if (resources.isEmpty()) {
                    final Resource nowInstantiated = fhirInstanceCreator.create(helper.getTargetResource());
                    createdPerIndex.put(createKey(0, conditioningFhirPath), nowInstantiated);
                    resources.add(nowInstantiated); //add at least one if none was created as part of the previous step
                }
                for (final Resource instance : resources) {


                    if (OPENEHR_TYPE_NONE.equals(helper.getOpenEhrType())) {
                        handleConditionMapping(helper.getCondition(),
                                instance,
                                fullOpenEhrPath,
                                instantiatedIntermediateElements,
                                helper.getTargetResource(),
                                helper.isFollowedBy(),
                                helper.getParentFollowedByFhirPath(),
                                helper.getParentFollowedByOpenEhr());
                        continue;
                    }

                    final String fhirPathWithoutConditions = openFhirStringUtils.getFhirPathWithConditions(helper.getFhirPath(), helper.getCondition(), helper.getTargetResource(), helper.getParentFollowedByFhirPath());

                    log.debug("Processing data point from openEhr {}, value : {}, fhirPath: {}", fullOpenEhrPath, dataForAllResources.getData().getClass(), fhirPathWithoutConditions);

                    final FindingOuterMost findingOuterMost = getOrInstantiateIntermediateItem(instantiatedIntermediateElements,
                            instance,
                            fhirPathWithoutConditions,
                            helper.getOpenEhrType(),
                            helper.getTargetResource(),
                            fullOpenEhrPath,
                            helper.isFollowedBy(),
                            helper.getParentFollowedByFhirPath(),
                            helper.getParentFollowedByOpenEhr(),
                            separatelyCreatedResources);

                    final String whereInRemoved = findingOuterMost.getRemovedPath() != null ? openFhirStringUtils.extractWhereCondition(findingOuterMost.getRemovedPath()) : null;
                    boolean removedPathIsOnlyWhere = findingOuterMost.getRemovedPath() != null
                            && whereInRemoved != null
                            && (whereInRemoved.equals(findingOuterMost.getRemovedPath())
                            || ("." + whereInRemoved).equals(findingOuterMost.getRemovedPath()));

                    if (StringUtils.isNotEmpty(findingOuterMost.getRemovedPath())) {

                        handleReturnedListWithWhereCondition(findingOuterMost);

                        final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(findingOuterMost.getLastObject(),
                                findingOuterMost.getLastObject().getClass(),
                                removedPathIsOnlyWhere ? THIS : findingOuterMost.getRemovedPath(),
                                openFhirMapperUtils.getFhirConnectTypeToFhir(helper.getOpenEhrType()));

                        cacheReturnedItems(findingOuterMost,
                                hardcodedReturn,
                                instance,
                                fhirPathWithoutConditions,
                                generatingResource,
                                removedPathIsOnlyWhere,
                                fullOpenEhrPath,
                                instantiatedIntermediateElements,
                                helper);

                        fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(), dataForAllResources);
                    } else {
                        fhirInstancePopulator.populateElement(findingOuterMost.getLastObject(), dataForAllResources);
                    }

                    // now handle "hardcoded" things within conditions on inner elements of the mapping/resource
                    handleConditionMapping(helper.getCondition(),
                            instance,
                            fullOpenEhrPath,
                            instantiatedIntermediateElements,
                            helper.getTargetResource(),
                            helper.isFollowedBy(),
                            helper.getParentFollowedByFhirPath(),
                            helper.getParentFollowedByOpenEhr());
                }
            }
        }

        final List<Resource> createdResources = new ArrayList<>(createdPerIndex.values());
        // now handle "hardcoded" things within conditions
        for (Resource createdResource : createdResources) {
            if (conditions != null && !conditions.isEmpty()) {
                for (Condition condition : conditions) {
                    if (condition.getCriteria() == null) {
                        continue;
                    }

                    final String conditionFhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, createdResource.fhirType(), null);

                    // check if it exists
                    final List<Base> alreadyExists = fhirPathR4.evaluate(createdResource, conditionFhirPathWithConditions, Base.class);
                    if (alreadyExists != null && !alreadyExists.isEmpty()) {
                        // all good
                        log.debug("Cover condition already exists on the resource, doing nothing");
                    } else {
                        final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(createdResource,
                                createdResource.getClass(),
                                condition.getTargetRoot() + "." + condition.getTargetAttribute(),
                                null);

                        final Object toSetCriteriaOn = getLastReturn(hardcodedReturn).getReturning();
                        final Coding stringFromCriteria = openFhirStringUtils.getStringFromCriteria(condition.getCriteria());
                        fhirInstancePopulator.populateElement(toSetCriteriaOn, new StringType(stringFromCriteria.getCode()));
                    }
                }
            }
        }

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
        /**
         * Needs to have full path to this item that will be added to the cache
         */
        final String preparedFullFhirPathForCachePopulation;
        if (findingOuterMost.getRemovedPath().startsWith(".as(")) {
            // was casting
            final String removedPath = findingOuterMost.getRemovedPath();
            final String[] splitRemovedPathByDot = removedPath.split("\\.");
            final String castString = splitRemovedPathByDot[1];
            final String actualRemovedAndResolvedPartString = splitRemovedPathByDot.length > 2 ? ("." + splitRemovedPathByDot[2]) : null;
            preparedFullFhirPathForCachePopulation = fhirPath
                    .replace(generatingResource + ".", "")
                    .replace(removedPath, "") + "." + castString + actualRemovedAndResolvedPartString;


        } else {
            if (removedPathIsOnlyWhere) {
                preparedFullFhirPathForCachePopulation = fhirPath;
            } else {
                String removedPath = findingOuterMost.getRemovedPath();
                final boolean startsWithWhere = removedPath.startsWith(".where(");
                if (startsWithWhere) {
                    removedPath = removedPath.replace("." + openFhirStringUtils.extractWhereCondition(removedPath), "");
                }
                final List<String> splitByDots = Arrays.stream(removedPath.split("\\.")).filter(StringUtils::isNotBlank).toList();
                final String suffix = splitByDots.get(0);
                final String where = splitByDots.size() > 1 && splitByDots.get(1).startsWith("where") ? ("." + openFhirStringUtils.extractWhereCondition(removedPath)) : "";
                final String cast = splitByDots.size() > 1 && splitByDots.get(1).startsWith("as") ? ("." + splitByDots.get(1)) : "";

                preparedFullFhirPathForCachePopulation = fhirPath
                        .replace(generatingResource + ".", "")
                        .replace(removedPath, "")
                        + "."
                        + suffix + where + cast;
            }
        }
        hardcodedReturn.setPath(preparedFullFhirPathForCachePopulation);


        populateIntermediateCache(hardcodedReturn,
                instance.toString(),
                instantiatedIntermediateElements,
                instance.getResourceType().name(),
                fullOpenEhrPath,
                helper.getParentFollowedByFhirPath(),
                helper.getParentFollowedByOpenEhr());
    }

    /**
     * When from instantiated cache we get a list of some elements and removedPath is a where, we need to check if
     * something within that list actually matches the where or not. If it does - good - removedPath is applied on that element,
     * but if it doesn't, setting removedPath objects on that element would overwrite what was already set there. So in this case,
     * (when no elements within the list match the where), we rather instantiate a new such element, add it to the list and
     * let the fhirInstanceCreator.instantiateAndSetElement be processed on that one.
     * <p>
     * Example of such a thing is where you get a list of extensions from the intermediate cache, but your fhirPath
     * defines only a very specific extension (i.e. the one with url=123).
     *
     * @param findingOuterMost
     */
    private void handleReturnedListWithWhereCondition(final FindingOuterMost findingOuterMost) {
        if (findingOuterMost.getLastObject() instanceof List<?> && (findingOuterMost.getRemovedPath().startsWith(".where") || findingOuterMost.getRemovedPath().startsWith("where"))) {
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
                    final Object newInstanceOfThisObject = ((List) findingOuterMost.getLastObject()).get(0).getClass().getDeclaredConstructor().newInstance();
                    ((List) findingOuterMost.getLastObject()).add(newInstanceOfThisObject);
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
     * @param condition                        as it's defined in the fhir connect model mapper
     * @param instance                         fhir resource being generated
     * @param fullOpenEhrPath                  openehr path of the model mapper
     * @param instantiatedIntermediateElements intermediate cache where we'll try to find element that's just been
     *                                         instantiated
     * @param targetResource                   target resource if it's a resolve() mapping
     */
    private void handleConditionMapping(final Condition condition,
                                        final Resource instance,
                                        final String fullOpenEhrPath,
                                        final Map<String, Object> instantiatedIntermediateElements,
                                        final String targetResource,
                                        final boolean isFollowedBy,
                                        final String parentFhirEhr,
                                        final String parentOpenEhr) {
        if (condition == null) {
            return;
        }
        final String stringFromCriteria = openFhirStringUtils.getStringFromCriteria(condition.getCriteria()).getCode();

        final String fhirPathSuffix = ("." + condition.getTargetAttribute());

        final String conditionFhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, targetResource, parentFhirEhr) + fhirPathSuffix;
        final FindingOuterMost existing = findTheOuterMostThatExistsWithinCache(instantiatedIntermediateElements,
                instance,
                conditionFhirPathWithConditions,
                fullOpenEhrPath,
                "",
                isFollowedBy,
                parentFhirEhr,
                parentOpenEhr);

        if (existing.getLastObject() != null) {
            final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(existing.getLastObject(),
                    existing.getLastObject().getClass(),
                    existing.getRemovedPath(),
                    null);
            fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(), new StringType(stringFromCriteria));
        } else {
            // here you have to instantiate the actual where items, so prepare fhir path as such
            final String fhirPathWithoutConditions = openFhirStringUtils.getFhirPathWithoutConditions(condition.getTargetRoot(), condition, targetResource);
            final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(instance,
                    instance.getClass(),
                    fhirPathWithoutConditions,
                    null);
            hardcodedReturn.setPath(openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, targetResource, parentFhirEhr).replace(targetResource + ".", "")); // this may not be entirely correct, should probably replace differently....depending on whether targetRoot is fhirResource or not
            populateIntermediateCache(hardcodedReturn,
                    instance.toString(),
                    instantiatedIntermediateElements,
                    instance.getResourceType().name(),
                    fullOpenEhrPath,
                    parentFhirEhr,
                    parentOpenEhr);

            fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(), new StringType(stringFromCriteria));
        }
    }

    /**
     * Find outer most element that exists within cache. Outer most reflects the first element where fhir path matches, i.e.
     * if cache contains  'patient.identifier.value' and 'patient.identifier' and 'patient' and we will be searching for
     * 'patient.identifier.value.extension.system', then the outerMost is the one that has the longer fhir path matching, in this
     * example this would be the 'patient.identifier.value'. Whereas the remaining/missing path (extension.system) will be part of the
     * FindingOuterMost.removedPath
     *
     * @param instantiatedIntermediateElements existing cache of the already created items
     * @param coverInstance                    main FHIR Resource that is being created/populated
     * @param fhirPath                         fhir path that is being looked for from the cache (or rather fhir path is one of the cache keys)
     * @param fullOpenEhrPath                  openEhr path that is being looked for from the cache (or rather openehr path is one of the cache keys)
     * @param removedPath                      this is passed over in recursive invocations and removedPath is being appended with
     *                                         every segment of the fhir path that is not found
     * @param isFollowedBy                     if mapper is a followed by from another (parent) one
     * @param parentFollowedByMapping          parent's fhir path
     * @param parentFollowedByOpenEhr          parent's openehr path
     * @return FindingOuterMost that is constructed from found elements, where removedPath is the fhir path that was removed from the
     * cache key as part of the iteration of finding it, and lastObject is the found object that existed within the cache
     */
    private FindingOuterMost findTheOuterMostThatExistsWithinCache(final Map<String, Object> instantiatedIntermediateElements,
                                                                   final Resource coverInstance,
                                                                   final String fhirPath,
                                                                   final String fullOpenEhrPath,
                                                                   final String removedPath,
                                                                   final boolean isFollowedBy,
                                                                   final String parentFollowedByMapping,
                                                                   final String parentFollowedByOpenEhr) {
        final String keyForIntermediateElements = createKeyForIntermediateElements(coverInstance.toString(), fhirPath, fullOpenEhrPath);
        if (isFollowedBy && parentFollowedByOpenEhr != null) {
            // we need to ignore the openehr in the key because followed by means we need to find one that has already been created!
            final String preparedParentPath = openFhirStringUtils.prepareParentOpenEhrPath(parentFollowedByOpenEhr,
                    fullOpenEhrPath);
            String keyIgnoringOpenEhrPath;
            if (fullOpenEhrPath.contains(preparedParentPath)) {
                // means that child openehr path is a sub-path of the followed by parent
                keyIgnoringOpenEhrPath = createKeyForIntermediateElements(coverInstance.toString(), fhirPath, preparedParentPath);
            } else {
                keyIgnoringOpenEhrPath = createKeyForIntermediateElements(coverInstance.toString(), fhirPath, "");
            }

            // if path ends with an index, we need to remove that because we really want that list

            final String keyToCheckFor = keyIgnoringOpenEhrPath;
            // if key to check for ends with an index, we first try to find an entry without and if it's array, use that

            final List<String> elementsMatching = instantiatedIntermediateElements.keySet().stream()
                    .filter(key -> {
                        if (key.startsWith(keyToCheckFor)) {
                            return true;
                        } else if (keyToCheckFor.startsWith(key)) {
                            // this is also fine, as long as the digit isn't the only difference.
                            final String isThisDigitOnly = keyToCheckFor.replace(key, "").replace(":", "");
                            if (Character.isDigit(isThisDigitOnly.charAt(0)) && !isThisDigitOnly.contains("|")) {
                                return false;
                            }
                            try {
                                Integer.parseInt(isThisDigitOnly);
                                // digit was the only thing that was different, this condition not ok
                                return false;
                            } catch (final NumberFormatException e) {
                                // difference wasn't the digit only, more was different so it's ok
                                return true;
                            }
                        } else {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            elementsMatching.sort(Comparator.comparingInt(String::length));
            final String elementMatching = elementsMatching.isEmpty() ? null : elementsMatching.get(0);
            if (elementMatching != null) {
                return new FindingOuterMost(instantiatedIntermediateElements.get(elementMatching), removedPath);
            }
        }
        if (instantiatedIntermediateElements.containsKey(keyForIntermediateElements)) {
            return new FindingOuterMost(instantiatedIntermediateElements.get(keyForIntermediateElements), removedPath);
        }
        if (!fhirPath.contains(".")) {
            // we've reached the end, apparently there's nothing in the cache that would match this at all
            return new FindingOuterMost(null, removedPath);
        }

        String nextPath = fhirPath.substring(0, fhirPath.lastIndexOf("."));
        String removingPath = fhirPath.replace(nextPath, "") + removedPath;

        // if we'd be removing only a cast syntax (as(DateTimeType)), we actually need to remove more than that
        if (Arrays.stream(removingPath.split("\\.")).filter(StringUtils::isNotBlank).allMatch(e -> e.startsWith(".as(") || e.startsWith("as("))) {
            final String[] splitNextPath = nextPath.split("\\.");
            removingPath = "." + splitNextPath[splitNextPath.length - 1] + removingPath;
            nextPath = nextPath.substring(0, nextPath.lastIndexOf("."));
        }

        return findTheOuterMostThatExistsWithinCache(instantiatedIntermediateElements,
                coverInstance,
                nextPath,
                fullOpenEhrPath,
                removingPath,
                isFollowedBy,
                parentFollowedByMapping,
                parentFollowedByOpenEhr);
    }

    /**
     * Method that tries to find an existing element within intermediate cache. If none is found, a new one is
     * created and added to the cache.
     *
     * @param instantiatedIntermediateElements cache of already created FHIR elements
     * @param coverInstance                    main instance that is being populated/created (FHIR Resource)
     * @param fhirPath                         fhir path of the given mapping
     * @param type                             type as defined in fhir connect model mapping (and/or as found within the WebTemplate)
     * @param resolveResourceType              if fhir path is a resolve(), then this needs to contain resource type of the resolved
     *                                         element
     * @param fullOpenEhrPath                  full openEhr flat path
     * @param isFollowedBy                     if mapping is a followed by mapping of another one (also true if it's slot mapping)
     * @param parentFollowedByMapping          if it's followed by, this will contain parent's fhir path
     * @param parentFollowedByOpenEhr          if it's followed by, this will contain parent's openehr path
     * @param separatelyCreatedResources       resources that were separately created as part of the mapping (separately
     *                                         created means they were not directly created as part of the mapping but
     *                                         as part of the resolve() procedure)
     * @return FindingOuterMost that presents an object with the found cache item
     */
    private FindingOuterMost getOrInstantiateIntermediateItem(final Map<String, Object> instantiatedIntermediateElements,
                                                              final Resource coverInstance,
                                                              final String fhirPath,
                                                              final String type,
                                                              final String resolveResourceType,
                                                              final String fullOpenEhrPath,
                                                              final boolean isFollowedBy,
                                                              final String parentFollowedByMapping,
                                                              final String parentFollowedByOpenEhr,
                                                              final List<Resource> separatelyCreatedResources) {
        final FindingOuterMost existing = findTheOuterMostThatExistsWithinCache(instantiatedIntermediateElements,
                coverInstance,
                fhirPath,
                fullOpenEhrPath,
                "",
                isFollowedBy,
                parentFollowedByMapping,
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
            final Resource newCoverInstance = fhirInstanceCreator.create(resType);
            separatelyCreatedResources.add(newCoverInstance);
            hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(newCoverInstance,
                    newCoverInstance.getClass(),
                    fhirPath.substring(fhirPath.indexOf(".") + 1),
                    openFhirMapperUtils.getFhirConnectTypeToFhir(type),
                    resType);
            hardcodedReturn.setPath(resType + "." + hardcodedReturn.getPath());
            // populate with the new cover instance as well
            final FhirInstanceCreator.InstantiateAndSetReturn newCoverInstanceForCache = new FhirInstanceCreator.InstantiateAndSetReturn(newCoverInstance,
                    false,
                    null,
                    resType);

            populateIntermediateCache(newCoverInstanceForCache,
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
                    openFhirMapperUtils.getFhirConnectTypeToFhir(type),
                    resolveResourceType);
        }


        final FhirInstanceCreator.InstantiateAndSetReturn lastReturn = getLastReturn(hardcodedReturn);
        final Object toSetCriteriaOn = lastReturn.getReturning();

        populateIntermediateCache(hardcodedReturn,
                coverInstance.toString(),
                instantiatedIntermediateElements,
                coverInstance.getResourceType().name(),
                fullOpenEhrPath,
                parentFollowedByMapping,
                parentFollowedByOpenEhr);

        return new FindingOuterMost(toSetCriteriaOn, null);
    }

    /**
     * Populates intermediate cache with the element that was instantiated
     *
     * @param hardcodedReturn                  element that was instantiated and needs to be added to cache
     * @param objectRef                        hash of the cover instance being created so it's part of the cache key
     * @param instantiatedIntermediateElements already existing cache
     * @param path                             fhir path of the instantiated element
     * @param fullOpenEhrPath                  openehr path of the instantiated element
     */
    void populateIntermediateCache(final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn,
                                   final String objectRef,
                                   final Map<String, Object> instantiatedIntermediateElements,
                                   final String path,
                                   final String fullOpenEhrPath,
                                   final String followedByParentFhir,
                                   final String followedByParentOpenEhr) {

        if (hardcodedReturn.getReturning() == null) {
            return;
        }
        if (hardcodedReturn.getPath().equals(RESOLVE)) {
            // has already been handled
            populateIntermediateCache(hardcodedReturn.getInner(),
                    objectRef,
                    instantiatedIntermediateElements,
                    path + "." + hardcodedReturn.getPath(),
                    fullOpenEhrPath,
                    followedByParentFhir,
                    followedByParentOpenEhr);
            return;
        }
        if (hardcodedReturn.isList() && followedByParentOpenEhr != null) {
            // store the whole list under the one without any index
            final String preparedParentOpenEhrPath = Character.isDigit(fullOpenEhrPath.charAt(fullOpenEhrPath.length() - 1)) ? fullOpenEhrPath : openFhirStringUtils.prepareParentOpenEhrPath(followedByParentOpenEhr, fullOpenEhrPath);
            final Integer lastIndex = openFhirStringUtils.getLastIndex(preparedParentOpenEhrPath);
            if (lastIndex != -1) {
                boolean lastIsDigit = Character.isDigit(preparedParentOpenEhrPath.charAt(preparedParentOpenEhrPath.length() - 1));
                final String openEhrPath = lastIsDigit ? preparedParentOpenEhrPath.substring(0, preparedParentOpenEhrPath.lastIndexOf(":")) : preparedParentOpenEhrPath;
                // because its a list, we don't want where's (the last one) in there
                final String originalPath = path + "." + hardcodedReturn.getPath();
                final String lastWhere = openFhirStringUtils.extractWhereCondition(originalPath, true);
                final String fhirPath = lastWhere != null ? originalPath.replace("." + lastWhere, "") : originalPath;
                instantiatedIntermediateElements.put(createKeyForIntermediateElements(objectRef, fhirPath,
                                openEhrPath),
                        hardcodedReturn.getReturning());

                final List returningList = (List) hardcodedReturn.getReturning();
                final Object toAddToCache = returningList.get(returningList.size() - 1); // take last one
                instantiatedIntermediateElements.put(createKeyForIntermediateElements(objectRef, path + "." + hardcodedReturn.getPath(),
                                preparedParentOpenEhrPath),
                        toAddToCache);

            } else {
                // parent is apparently non-repeating; still add the list to parent path just in case
                instantiatedIntermediateElements.put(createKeyForIntermediateElements(objectRef, path + "." + hardcodedReturn.getPath(),
                                openFhirStringUtils.prepareParentOpenEhrPath(followedByParentOpenEhr, fullOpenEhrPath)),
                        hardcodedReturn.getReturning());
            }

            if (hardcodedReturn.getInner() != null) {
                populateIntermediateCache(hardcodedReturn.getInner(),
                        objectRef,
                        instantiatedIntermediateElements,
                        path + "." + hardcodedReturn.getPath(),
                        fullOpenEhrPath,
                        followedByParentFhir,
                        followedByParentOpenEhr);
            }

        } else if (followedByParentFhir != null) {
            final String preparedParentOpenEhrPath = openFhirStringUtils.prepareParentOpenEhrPath(followedByParentOpenEhr, fullOpenEhrPath);
            Object returning = hardcodedReturn.getReturning();
            if (returning instanceof Reference) {
                returning = ((Reference) returning).getResource();
            }

            final String fhirPath = path.equals(hardcodedReturn.getPath()) ? path : (path + "." + hardcodedReturn.getPath());

            instantiatedIntermediateElements.put(createKeyForIntermediateElements(objectRef, fhirPath,
                            preparedParentOpenEhrPath),
                    returning);

            if (hardcodedReturn.getInner() != null) {
                populateIntermediateCache(hardcodedReturn.getInner(),
                        objectRef,
                        instantiatedIntermediateElements,
                        path + "." + hardcodedReturn.getPath(),
                        fullOpenEhrPath,
                        null,
                        null);
            }

        } else {
            if (hardcodedReturn.isList()) {
                boolean lastIsDigit = Character.isDigit(fullOpenEhrPath.charAt(fullOpenEhrPath.length() - 1));
                final String openEhrPath = lastIsDigit ? fullOpenEhrPath.substring(0, fullOpenEhrPath.lastIndexOf(":")) : fullOpenEhrPath;

                // puts in the list
                instantiatedIntermediateElements.put(createKeyForIntermediateElements(objectRef, path + "." + hardcodedReturn.getPath(), openEhrPath),
                        hardcodedReturn.getReturning());

                final String preparedParentOpenEhrPath = fullOpenEhrPath;
                final Integer lastIndex = openFhirStringUtils.getLastIndex(preparedParentOpenEhrPath);
                final List returningList = (List) hardcodedReturn.getReturning();
                if (lastIsDigit && lastIndex < returningList.size()) {
                    final Object toAddToCache = returningList.get(returningList.size() - 1); // todo: always takes the last one, is this ok?
                    instantiatedIntermediateElements.put(createKeyForIntermediateElements(objectRef, path + "." + hardcodedReturn.getPath(),
                                    preparedParentOpenEhrPath),
                            toAddToCache);
                }
            } else {
                instantiatedIntermediateElements.put(createKeyForIntermediateElements(objectRef, path + "." + hardcodedReturn.getPath(), fullOpenEhrPath),
                        hardcodedReturn.getReturning());
            }


            if (hardcodedReturn.getInner() != null) {
                populateIntermediateCache(hardcodedReturn.getInner(),
                        objectRef,
                        instantiatedIntermediateElements,
                        path + "." + hardcodedReturn.getPath(),
                        fullOpenEhrPath,
                        null,
                        followedByParentOpenEhr);
            }
        }

    }

    /**
     * Creates key for the cache population; key is created based on the reference of the object (toString on a Resource),
     * fhir path and openehr path. This is the key used to cache intermediate elements as they are created throughout
     * the mapping engine
     */
    private String createKeyForIntermediateElements(final String objectRef, final String fhirPath, final String fullOpenEhrPath) {
        // from full openEhrPath, only indexes should be part of the key. And even that, all indexes BUT the first one (because the first one is a Resource and that's the objectRef one)
        final String fixedFhirPath = fhirPath
                .replace("." + RESOLVE, "")
                .replace("." + FHIR_ROOT_FC, "")
                .replace(FHIR_ROOT_FC, "");
        return new StringJoiner("_").add(objectRef).add(fixedFhirPath).add(fullOpenEhrPath.replace("[n]", "")).toString();
    }

    /**
     * Creates key for the main Resource creation cache, constructed from an integer the represents occurrence from flat path
     * and limitingResourceCriteria that's a fhir path constructed from Conditions
     */
    private String createKey(final Integer index, final String limitingResourceCriteria) {
        return String.format("%s_%s", index, limitingResourceCriteria);
    }

    /**
     * Find a Resource within createdPerIndex or create one if it doesn't exist already
     *
     * @param createdPerIndex cache where this method will be searching in
     * @param targetResource  Resource type that we're looking for (for the purpose of creating it)
     * @param key             that should point to a Resource within the cache
     * @return Resource from the cache (or created one if there was none)
     */
    private Resource getOrCreateResource(final Map<String, Resource> createdPerIndex,
                                         final String targetResource,
                                         final String key) {
        if (createdPerIndex.containsKey(key)) {
            return createdPerIndex.get(key);
        }
        log.info("Create a new instance of a resource {}", targetResource);
        return fhirInstanceCreator.create(targetResource);
    }

    /**
     * Prepare helper objects for openEHR to FHIR mappings. Helper objects in a friendly and easily accessible way store
     * see @javadoc of OpenEhrToFhirHelper.class
     *
     * @param theMapper               fhir connect mapper
     * @param resourceType            fhir resource type being mapped to
     * @param firstFlatPath           first flat path - in most cases template id
     * @param mappings                model mapper mappings
     * @param helpers                 a list of helpers being constructed
     * @param webTemplate             openEHR web template object
     * @param flatJsonObject          flat json we're constructing FHIR from
     * @param isFollowedBy            if a mapper is a followed by mapper (true if followedBy or slotArchetype)
     * @param parentFollowedByFhir    if followed by, this is parent's fhir path
     * @param parentFollowedByOpenEhr if followed by, this is parent's openehr path
     * @param slotContext             if slot context mapper, this is the base flat path you use as a root for context mappings
     */
    void prepareOpenEhrToFhirHelpers(final FhirConnectMapper theMapper,
                                     final String resourceType,
                                     final String firstFlatPath,
                                     final List<Mapping> mappings,
                                     final List<OpenEhrToFhirHelper> helpers,
                                     final WebTemplate webTemplate,
                                     final JsonObject flatJsonObject,
                                     boolean isFollowedBy,
                                     final String parentFollowedByFhir,
                                     final String parentFollowedByOpenEhr,
                                     final String slotContext) {
        for (final Mapping mapping : mappings) {
            final String hardcodedValue = mapping.getWith().getValue();
            if (mapping.getWith().getFhir() == null) {
                // it means it's hardcoding to openEHR, we can therefore skip it when mapping to FHIR
                continue;
            }
            if (mapping.getWith().getOpenehr() == null && hardcodedValue != null) {
                // hardcoding to FHIR
                mapping.getWith().setOpenehr(OPENEHR_ARCHETYPE_FC);
            }


            String openehr;

            // openEHR path for a specific mapping; if a certain model mapper has no openEHR path defined, take parent's one
            final String definedMappingWithOpenEhr = mapping.getWith().getOpenehr();
            if (StringUtils.isEmpty(definedMappingWithOpenEhr)) {
                openehr = parentFollowedByOpenEhr;
            } else {
                openehr = openFhirStringUtils.prepareOpenEhrSyntax(definedMappingWithOpenEhr, firstFlatPath);
            }

            final String rmType = getRmType(openehr, mapping, webTemplate);

            // get fhir path with conditions included in the fhir path itself
            final String fhirPath = openFhirStringUtils.amendFhirPath(mapping.getWith().getFhir(),
                    null, // should condition be added here?
                    theMapper.getFhirConfig().getResource());

            /*
              handling of $reference mappings as defined in the fhir connect spec
             */
            if (definedMappingWithOpenEhr != null
                    && definedMappingWithOpenEhr.startsWith(FhirConnectConst.REFERENCE)
                    && mapping.getReference() != null) {
                final List<Mapping> referencedMapping = mapping.getReference().getMappings();
                final String wConditions = openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(), mapping.getCondition(), resourceType, parentFollowedByFhir);
                openFhirMapperUtils.prepareReferencedMappings(wConditions, openehr, referencedMapping);

                // now conditions
                if (mapping.getCondition() != null) {
                    // if condition of a $reference isn't null, we add it to generated helpers despite being a $reference only, since
                    // condition itself needs to be evaluted and added to the generated FHIR
                    final String parentFollowedByFhirPath = parentFollowedByFhir == null ? null : parentFollowedByFhir.replace(FhirConnectConst.FHIR_RESOURCE_FC, resourceType);
                    final String parentFollowedByOpenEhr1 = parentFollowedByOpenEhr == null ? null : parentFollowedByOpenEhr.replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, firstFlatPath);
                    OpenEhrToFhirHelper openEhrToFhirHelper = OpenEhrToFhirHelper.builder()
                            .mainArchetype(theMapper.getOpenEhrConfig().getArchetype())
                            .targetResource(resourceType)
                            .openEhrPath(definedMappingWithOpenEhr)
                            .fhirPath(fhirPath) // fhir path here should not have the full where yada yada, rather just the normal path to the data point, resource limiting is done in other places
                            .openEhrType(mapping.getWith().getType())
                            .data(new ArrayList<>())
                            .isFollowedBy(isFollowedBy)
                            .parentFollowedByFhirPath(parentFollowedByFhirPath)
                            .parentFollowedByOpenEhr(parentFollowedByOpenEhr1)
                            .condition(mapping.getCondition())
                            .build();
                    helpers.add(openEhrToFhirHelper);
                }

                // recursive call so all $reference.mappings are handled
                prepareOpenEhrToFhirHelpers(theMapper,
                        mapping.getReference().getResourceType(),
                        firstFlatPath,
                        mapping.getReference().getMappings(),
                        helpers,
                        webTemplate,
                        flatJsonObject,
                        false, // is this ok? feels like it should be true, as mappings are followed by a $reference mapping
                        parentFollowedByFhir,
                        parentFollowedByOpenEhr,
                        slotContext);

            } else {
                if (openehr.endsWith("content/content") && OPENEHR_TYPE_MEDIA.equals(rmType)) {
                    openehr = openehr.substring(0, openehr.length() - "/content".length()); // remove the last /content part, because the path is content/content which is not ok for openEhr—>fhir
                }
                boolean manuallyAddingOccurrence = openehr.contains("[n]");
                if (manuallyAddingOccurrence) {
                    // for cases when you're manually adding recurring syntax to an openEHR path for whatever reason
                    // (but mostly due to context weird behavior when you have _participation)
                    openehr = openehr.replaceAll("\\[n]", "");
                }
                // adds regex pattern to simplified path in a way that we can extract data from a given flat path
                final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(openehr);

                // get all entries from the flat path that match the simplified flat path with regex pattern
                final List<String> matchingEntries = getAllEntriesThatMatch(withRegex, flatJsonObject);
                final Map<String, List<String>> joinedEntries = joinValuesThatAreOne(matchingEntries);

                if (mapping.getSlotArchetype() != null) {
                    final String templateId = webTemplate.getTemplateId();

                    final List<FhirConnectMapper> slotArchetypeMapperss = openFhirTemplateRepo.getSlotMapperForArchetype(templateId, mapping.getSlotArchetype());
                    for (FhirConnectMapper slotArchetypeMappers : slotArchetypeMapperss) {
                        openFhirMapperUtils.prepareForwardingSlotArchetypeMapper(slotArchetypeMappers, theMapper, fhirPath, openehr);

                        // recursively prepare all slot archetype mappers
                        prepareOpenEhrToFhirHelpers(slotArchetypeMappers,
                                resourceType,
                                firstFlatPath,
                                slotArchetypeMappers.getMappings(),
                                helpers,
                                webTemplate,
                                flatJsonObject,
                                true,
                                fhirPath,
                                openehr,
                                openehr);

                        // slot archetype can be followed by other mappers as well
                        if (mapping.getFollowedBy() != null) {
                            final List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();

                            openFhirMapperUtils.prepareFollowedByMappings(followedByMappings,
                                    fhirPath,
                                    openehr,
                                    firstFlatPath);

                            prepareOpenEhrToFhirHelpers(theMapper,
                                    resourceType,
                                    firstFlatPath,
                                    followedByMappings,
                                    helpers,
                                    webTemplate,
                                    flatJsonObject,
                                    true,
                                    openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(), mapping.getCondition(), resourceType, parentFollowedByFhir),
                                    definedMappingWithOpenEhr,
                                    slotContext);
                        }
                    }
                } else {
                    String openEhrPath = null;
                    List<OpenEhrToFhirHelper.DataWithIndex> values = extractValues(mapping, joinedEntries, rmType, flatJsonObject, hardcodedValue);
                    if (!OPENEHR_TYPE_NONE.equals(mapping.getWith().getType())) {
                        openEhrPath = openehr;
                    } else if (mapping.getCondition() != null) {
                        openEhrPath = openFhirStringUtils.prepareOpenEhrSyntax(definedMappingWithOpenEhr, firstFlatPath);
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
                                .parentFollowedByFhirPath(parentFollowedByFhir == null ? null : parentFollowedByFhir.replace(FhirConnectConst.FHIR_RESOURCE_FC, resourceType))
                                .parentFollowedByOpenEhr(parentFollowedByOpenEhr == null ? null : parentFollowedByOpenEhr.replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, firstFlatPath))
                                .condition(mapping.getCondition())
                                .build();
                        helpers.add(openEhrToFhirHelper);
                    }

                    if (mapping.getFollowedBy() != null) {
                        final List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();

                        openFhirMapperUtils.prepareFollowedByMappings(followedByMappings,
                                fhirPath,
                                openehr,
                                slotContext);

                        prepareOpenEhrToFhirHelpers(theMapper,
                                resourceType,
                                firstFlatPath,
                                followedByMappings,
                                helpers,
                                webTemplate,
                                flatJsonObject,
                                true,
                                openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(), mapping.getCondition(), resourceType, parentFollowedByFhir),
                                definedMappingWithOpenEhr == null ? firstFlatPath : definedMappingWithOpenEhr,
                                slotContext);
                    }

                }
            }

        }
    }

    /**
     * Extracts values from joinedEntries and creates data points from that together with an index according
     * to flat path
     *
     * @param mapping        mapping currently being evaluated
     * @param joinedEntries  flat paths joined together
     * @param rmType         type of the data point
     * @param flatJsonObject json object representing flat path format of a Composition
     * @param hardcodedValue if there is no mapping but rather a hardcoding, this will hold a value that needs to be
     *                       hardcoded
     */
    private List<OpenEhrToFhirHelper.DataWithIndex> extractValues(final Mapping mapping,
                                                                  final Map<String, List<String>> joinedEntries,
                                                                  final String rmType,
                                                                  final JsonObject flatJsonObject,
                                                                  final String hardcodedValue) {
        List<OpenEhrToFhirHelper.DataWithIndex> values = null;
        if (!OPENEHR_TYPE_NONE.equals(mapping.getWith().getType())) {
            if (StringUtils.isNotEmpty(hardcodedValue)) {
                values = new ArrayList<>();
                values.add(new OpenEhrToFhirHelper.DataWithIndex(new StringType(hardcodedValue), -1, OPENEHR_ARCHETYPE_FC));
            } else {
                values = joinedEntries.values().stream()
                        .map(strings -> valueToDataPoint(strings, rmType, flatJsonObject, true))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        } else if (mapping.getCondition() != null) {
            values = joinedEntries.values().stream()
                    .map(strings -> valueToDataPoint(strings, rmType, flatJsonObject, false))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return values;
    }

    private String getRmType(final String openEhrPath,
                             final Mapping mapping,
                             final WebTemplate webTemplate) {
        if (mapping.getWith().getType() == null) {
            // if type is not explicitly defined in a fhir connect model mapper, it is taken from the template definition
            final FhirToOpenEhrHelper getTypeHelper = FhirToOpenEhrHelper.builder()
                    .openEhrPath(openEhrPath)
                    .build();
            openEhrRmWorker.fixFlatWithOccurrences(Arrays.asList(getTypeHelper), webTemplate);
            return getTypeHelper.getOpenEhrType();
        } else {
            return mapping.getWith().getType();
        }
    }

    /**
     * Returns the inner-est element in the InstantiateAndSetReturn object, since that's the one we need to populate.
     * Method loops over inner elements recursively until it reaches the last one.
     */
    private FhirInstanceCreator.InstantiateAndSetReturn getLastReturn(final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn) {
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
    Map<String, List<String>> joinValuesThatAreOne(final List<String> matchingEntries) {
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
     * @param targetType   target openEHR data type
     * @param valueHolder  original json object containing all the data
     * @param canBeNull    if returned data can be null, if false, a generic StringType will be added to the returned object
     * @return OpenEhrToFhirHelper.DataWithIndex with index and data populated
     */
    private OpenEhrToFhirHelper.DataWithIndex valueToDataPoint(final List<String> joinedValues,
                                                               final String targetType,
                                                               final JsonObject valueHolder,
                                                               boolean canBeNull) {
        if (joinedValues == null || joinedValues.isEmpty()) {
            return null;
        }
        // we get the zeroth one as all same ones have been joined together, so joinedValues actually represents
        // all same paths (with differences in suffixes, i.e. |value, |code, ..)
        final String path = joinedValues.get(0);
        // last index represents index relevant for this specific data point
        final Integer lastIndex = openFhirStringUtils.getLastIndex(path);
        final String value = joinedValues.stream().filter(s -> s.endsWith("value")).findAny().orElse(null);
        final String code = joinedValues.stream().filter(s -> s.endsWith("code")).findAny().orElse(null);
        final String terminology = joinedValues.stream().filter(s -> s.endsWith("terminology")).findAny().orElse(null);
        final String id = joinedValues.stream().filter(s -> s.endsWith("id")).findAny().orElse(null);
        switch (targetType) {
            case "PROPORTION":
                // proportion is the only one with more business logic, as there is no such type in FHIR. Therefore,
                // to translate proportion in openEHR you need to calculate the actual Double/Long value to set it to a FHIR Resource
                final String proportionVal = joinedValues.get(0);
                final String numerator = proportionVal + "|numerator";
                final String denominator = proportionVal + "|denominator";
                final Quantity proportionQuantity = new Quantity();
                final String proportionValueHolder = getFromValueHolder(valueHolder, denominator);
                if (proportionValueHolder != null && proportionValueHolder.equals("100.0")) {
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
            case "QUANTITY":
                final String magnitude = joinedValues.stream().filter(s -> s.endsWith("magnitude")).findAny().orElse(null);
                final String unit = joinedValues.stream().filter(s -> s.endsWith("unit")).findAny().orElse(null);
                final String ordinal = joinedValues.stream().filter(s -> s.endsWith("ordinal")).findAny().orElse(null);


                final Quantity quantity = new Quantity();
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
                if (unit != null) {
                    quantity.setUnit(getFromValueHolder(valueHolder, unit));
                }
                if (value != null) {
                    quantity.setUnit(getFromValueHolder(valueHolder, value)); // whatever is filled, can't be both so second won't override the first
                }
                if (code != null) {
                    quantity.setCode(getFromValueHolder(valueHolder, code));
                }
                if (magnitude == null
                        && ordinal == null
                        && unit == null
                        && value == null
                        && code == null
                        && getFromValueHolder(valueHolder, path) != null) {
                    final Object pathVal = getDoubleOrLong(getFromValueHolder(valueHolder, path));
                    if (pathVal instanceof Long) {
                        quantity.setValue((Long) pathVal);
                    } else if (pathVal instanceof Double) {
                        quantity.setValue((Double) pathVal);
                    }
                }
                return new OpenEhrToFhirHelper.DataWithIndex(quantity, lastIndex, path);
            case "DATETIME":
                final DateTimeType dateTimeType = new DateTimeType();
                dateTimeType.setValue(openFhirMapperUtils.stringToDate(getFromValueHolder(valueHolder, path)));
                return new OpenEhrToFhirHelper.DataWithIndex(dateTimeType, lastIndex, path);
            case "TIME":
                final TimeType timeType = new TimeType();
                timeType.setValue(getFromValueHolder(valueHolder, path));
                return new OpenEhrToFhirHelper.DataWithIndex(timeType, lastIndex, path);
            case "BOOL":
                final BooleanType booleanType = new BooleanType();
                booleanType.setValue(Boolean.valueOf(getFromValueHolder(valueHolder, path)));
                return new OpenEhrToFhirHelper.DataWithIndex(booleanType, lastIndex, path);
            case "DATE":
                final DateType dateType = new DateType();
                dateType.setValue(openFhirMapperUtils.stringToDate(getFromValueHolder(valueHolder, path)));
                return new OpenEhrToFhirHelper.DataWithIndex(dateType, lastIndex, path);
            case "CODEABLECONCEPT":
                final CodeableConcept data = new CodeableConcept();
                final String text = getFromValueHolder(valueHolder, value);
                data.setText(text);
                data.addCoding(new Coding(getFromValueHolder(valueHolder, terminology),
                        getFromValueHolder(valueHolder, code), text));
                return new OpenEhrToFhirHelper.DataWithIndex(data, lastIndex, path);
            case "CODING":
                return new OpenEhrToFhirHelper.DataWithIndex(new Coding(getFromValueHolder(valueHolder, terminology),
                        getFromValueHolder(valueHolder, code), getFromValueHolder(valueHolder, value)),
                        lastIndex,
                        path);
            case "MEDIA":
                Attachment att = new Attachment();
                att.setContentType(getFromValueHolder(valueHolder, path + "|mediatype"));
                final String size = getFromValueHolder(valueHolder, path + "|size");
                if (size != null) {
                    att.setSize(Integer.parseInt(size));
                }
                att.setUrl(getFromValueHolder(valueHolder, path + "|url"));
                final String dataBytes = getFromValueHolder(valueHolder, path + "|data");
                att.setData(dataBytes == null ? null : dataBytes.getBytes(StandardCharsets.UTF_8));
                return new OpenEhrToFhirHelper.DataWithIndex(att, lastIndex, path);
            case "IDENTIFIER":
                Identifier identifier = new Identifier();
                identifier.setValue(getFromValueHolder(valueHolder, StringUtils.isEmpty(id) ? (path + "|id") : id));
                return new OpenEhrToFhirHelper.DataWithIndex(identifier, lastIndex, path);
            default:
            case "STRING":
                final String fromValueHolder = getFromValueHolder(valueHolder, path);
                if (StringUtils.isNotEmpty(fromValueHolder)) {
                    return new OpenEhrToFhirHelper.DataWithIndex(new StringType(fromValueHolder),
                            lastIndex,
                            path);
                } else {
                    if (canBeNull) {
                        return null;
                    } else {
                        return new OpenEhrToFhirHelper.DataWithIndex(new StringType(),
                                lastIndex,
                                path);

                    }
                }
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

    /**
     * Gets all entries from the flat path that match simplified openehr path with regex pattern
     *
     * @param withRegex           simplified openehr path with regex pattern
     * @param compositionFlatPath composition in a flat path format
     * @return a list of Strings that match the given flat path with regex pattern
     */
    List<String> getAllEntriesThatMatch(final String withRegex, final JsonObject compositionFlatPath) {
        Pattern compiledPattern = Pattern.compile(withRegex);
        final List<String> match = new ArrayList<>();
        for (Map.Entry<String, JsonElement> flatEntry : compositionFlatPath.entrySet()) {
            final Matcher matcher = compiledPattern.matcher(flatEntry.getKey());

            final List<String> matches = new ArrayList<>();

            while (matcher.find()) {
                matches.add(matcher.group());
            }
            if (matches.isEmpty()) {
                continue;
            }
            match.addAll(matches);
        }
        return match;
    }

    @AllArgsConstructor
    @Data
    public static class FindingOuterMost {
        public Object lastObject;
        public String removedPath;
    }
}
