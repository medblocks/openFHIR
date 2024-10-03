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

import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
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

    public Bundle compositionToFhir(final FhirConnectContext context, final Composition composition, final OPERATIONALTEMPLATE operationaltemplate) {
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

        for (ContentItem archetypesWithinContent : composition.getContent()) {
            final Map<String, Object> instantiatedIntermediateElements = new HashMap<>();

            final String archetypeNodeId = archetypesWithinContent.getArchetypeNodeId();
            if (archetypesAlreadyProcessed.contains(archetypeNodeId)) {
                continue;
            }
            final List<FhirConnectMapper> theMappers = openFhirTemplateRepo.getMapperForArchetype(templateId, archetypeNodeId);
            if (theMappers == null || theMappers.isEmpty()) {
                log.error("No mappers defined for archetype within this composition: {}. No mapping possible.", archetypeNodeId);
                continue;
            }
            for (FhirConnectMapper theMapper : theMappers) {
                if (theMapper.getFhirConfig() == null) {
                    continue;
                }
                final Boolean existingEntry = isMultipleByResourceType.getOrDefault(theMapper.getFhirConfig().getResource(), true);
                final boolean shouldUseExisting = existingEntry && !theMapper.getFhirConfig().getMultiple();
                isMultipleByResourceType.put(theMapper.getFhirConfig().getResource(), shouldUseExisting);
                intermediateCaches.put(theMapper.getFhirConfig().getResource(), intermediateCaches.getOrDefault(theMapper.getFhirConfig().getResource(), instantiatedIntermediateElements));
                final List<OpenEhrToFhirHelper> helpers = new ArrayList<>();
                mapAndGetAllOpenEhrToFhirHelpers(theMapper,
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
                        shouldUseExisting ? intermediateCaches.getOrDefault(theMapper.getFhirConfig().getResource(), instantiatedIntermediateElements) : instantiatedIntermediateElements,
                        webTemplate.getTree().getId());

                log.info("Constructed {} resources for archetype {}.", created.size(), archetypesWithinContent.getArchetypeNodeId());

                addEntriesToBundle(creatingBundle, created, createdAndAdded);
                archetypesAlreadyProcessed.add(archetypeNodeId);
            }

        }

        return creatingBundle;
    }

    private Bundle prepareBundle() {
        return new Bundle(); // todo: metadatas
    }

    private void addEntryToBundle(final Bundle bundle, final Resource resource) {
        bundle.addEntry(new Bundle.BundleEntryComponent().setResource(resource));
    }

    private void addEntriesToBundle(final Bundle bundle, final List<Resource> resource, final Set<String> createdAndAdded) {

        resource.forEach(res -> {
            if (createdAndAdded.contains(String.valueOf(res.hashCode()))) {
                return;
            }
            createdAndAdded.add(String.valueOf(res.hashCode()));
            addEntryToBundle(bundle, res);
        });
    }

    private List<Resource> createResourceFromOpenEhrToFhirHelper(final List<OpenEhrToFhirHelper> helpers,
                                                                 final FhirConfig fhirConfig,
                                                                 final Resource existingCreatingResource,
                                                                 final Map<String, Object> instantiatedIntermediateElements,
                                                                 final String firstFlatPath) {
        // all helpers (a list) is already per archetype, so always linked to a specific (or linked withReference) resourceType

        // first do the 'helper' with shortest openEhrPath to immediately see if you need to create more than one Resource?
        // or.. left most index represents recurring Resource?

        final String generatingResource = fhirConfig.getResource();
        final List<Condition> conditions = fhirConfig.getCondition();

        final List<Resource> separatelyCreatedResources = new ArrayList<>();
        final Map<String, Resource> createdPerIndex = new HashMap<>();
        if (existingCreatingResource != null) {
            createdPerIndex.put(createKey(0, fhirConfig.getResource()), existingCreatingResource);
        }
        final String conditioningFhirPath = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                conditions,
                generatingResource);
        for (OpenEhrToFhirHelper helper : helpers) {

            final String conditionLog = helper.getCondition() == null ? "" : (helper.getCondition().getTargetRoot() + " where " + helper.getCondition().getTargetAttribute() + " " + helper.getCondition().getOperator() + " " + helper.getCondition().getCriteria());
            log.debug("Processing: archetpye '{}', targetResource '{}', targetResourceCondition '{}', fhirPath '{}', openEhrPath '{}', openEhrType '{}', data size '{}', condition '{}', parentFhirPath '{}', parentOpenEhrPath '{}'",
                    helper.getMainArchetype(), helper.getTargetResource(), helper.getTargetResourceCondition(), helper.getFhirPath(), helper.getOpenEhrPath(), helper.getOpenEhrType(), helper.getData().size(), conditionLog, helper.getParentFollowedByFhirPath(), helper.getOpenEhrPath());

            // now set value
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

            for (OpenEhrToFhirHelper.DataWithIndex data : datas) {
                if (data.getIndex() == -1) {
                    // -1 means it's for all Resources, it's handled afterward, below
                    continue;
                }
                final String fullOpenEhrPath = data.getFullOpenEhrPath();

                final Integer index = fhirConfig.getMultiple() ? openFhirStringUtils.getFirstIndex(fullOpenEhrPath) : 0;
                final String mapKey = createKey(index, conditioningFhirPath);

                final Resource instance = getOrCreateResource(createdPerIndex, generatingResource, mapKey);

                if (OPENEHR_TYPE_NONE.equals(helper.getOpenEhrType())) {
                    handleConditionMapping(helper.getCondition(),
                            instance,
                            fullOpenEhrPath,
                            instantiatedIntermediateElements,
                            helper.getTargetResource(),
                            helper.isFollowedBy(),
                            helper.getParentFollowedByFhirPath(),
                            helper.getParentFollowedByOpenEhr(),
                            helper.getFhirPath());

                    createdPerIndex.put(mapKey, instance);
                    continue;
                }


                final String fhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(helper.getFhirPath(), helper.getCondition(), helper.getTargetResource(), helper.getParentFollowedByFhirPath());
                log.debug("Processing data point from openEhr {}, value : {}, fhirPath: {}", data.getFullOpenEhrPath(), data.getData().getClass(), fhirPathWithConditions);
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


                final String whereInRemovedPath = findingOuterMost.getRemovedPath() != null ? openFhirStringUtils.extractWhereCondition(findingOuterMost.getRemovedPath()) : null;
                boolean removedPathIsOnlyWhere = findingOuterMost.getRemovedPath() != null
                        && whereInRemovedPath != null
                        && (whereInRemovedPath.equals(findingOuterMost.getRemovedPath())
                        || ("." + whereInRemovedPath).equals(findingOuterMost.getRemovedPath()));
                if (StringUtils.isNotEmpty(findingOuterMost.getRemovedPath())) {
                    final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(findingOuterMost.getLastObject(),
                            findingOuterMost.getLastObject().getClass(),
                            removedPathIsOnlyWhere ? "$this" : findingOuterMost.getRemovedPath(),
                            openFhirMapperUtils.getFhirConnectTypeToFhir(helper.getOpenEhrType()),
                            helper.getTargetResource());

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
                        preparedFullFhirPathForCachePopulation = fhirPathWithConditions
                                .replace(generatingResource + ".", "")
                                .replace(removedPath, "") + "." + castString + actualRemovedAndResolvedPartString;

                    } else {
                        final String removedPath = findingOuterMost.getRemovedPath();
                        final List<String> splitByDots = Arrays.stream(removedPath.split("\\.")).filter(e -> StringUtils.isNotBlank(e)).collect(Collectors.toList());
                        final String suffix = splitByDots.get(0);
                        final String where = splitByDots.size() > 1 && splitByDots.get(1).startsWith("where") ? ("." + openFhirStringUtils.extractWhereCondition(findingOuterMost.getRemovedPath())) : "";
                        final String cast = splitByDots.size() > 1 && splitByDots.get(1).startsWith("as") ? ("." + splitByDots.get(1)) : "";
                        if (removedPathIsOnlyWhere) {
                            preparedFullFhirPathForCachePopulation = fhirPathWithConditions;
                        } else {
                            preparedFullFhirPathForCachePopulation = fhirPathWithConditions
                                    .replace(generatingResource + ".", "")
                                    .replace(findingOuterMost.getRemovedPath(), "")
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
                            helper.isFollowedBy(),
                            helper.getParentFollowedByFhirPath(),
                            helper.getParentFollowedByOpenEhr());
                    fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(), data);
                } else {
                    fhirInstancePopulator.populateElement(findingOuterMost.getLastObject(), data);
                }


                handleConditionMapping(helper.getCondition(),
                        instance,
                        fullOpenEhrPath,
                        instantiatedIntermediateElements,
                        helper.getTargetResource(),
                        helper.isFollowedBy(),
                        helper.getParentFollowedByFhirPath(),
                        helper.getParentFollowedByOpenEhr(),
                        helper.getFhirPath());

                createdPerIndex.put(mapKey, instance);
            }

            for (OpenEhrToFhirHelper.DataWithIndex dataForAllResources : datas.stream().filter(data -> data.getIndex() == -1).collect(Collectors.toList())) {
                final String fullOpenEhrPath = dataForAllResources.getFullOpenEhrPath();
                final ArrayList<Resource> resources = new ArrayList<>(createdPerIndex.values());
                if (resources.isEmpty()) {
                    final Resource nowInstantiated = fhirInstanceCreator.create(helper.getTargetResource());
                    createdPerIndex.put(createKey(0, conditioningFhirPath), nowInstantiated);
                    resources.add(nowInstantiated); //add at least one if none was created as part of the previous step
                }
                for (Resource instance : resources) {


                    if ("NONE".equals(helper.getOpenEhrType())) {
                        handleConditionMapping(helper.getCondition(),
                                instance,
                                fullOpenEhrPath,
                                instantiatedIntermediateElements,
                                helper.getTargetResource(),
                                helper.isFollowedBy(),
                                helper.getParentFollowedByFhirPath(),
                                helper.getParentFollowedByOpenEhr(),
                                helper.getFhirPath());
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
                        final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(findingOuterMost.getLastObject(),
                                findingOuterMost.getLastObject().getClass(),
                                removedPathIsOnlyWhere ? "$this" : findingOuterMost.getRemovedPath(),
                                openFhirMapperUtils.getFhirConnectTypeToFhir(helper.getOpenEhrType()));

                        /**
                         * Needs to have full path to this item that will be added to the cache
                         */
                        String preparedFullFhirPathForCachePopulation = fhirPathWithoutConditions
                                .replace(conditioningFhirPath + ".", "")
                                .replace(findingOuterMost.getRemovedPath(), "") + "." + (findingOuterMost.getRemovedPath().startsWith(".") ? findingOuterMost.getRemovedPath().split("\\.")[1] : findingOuterMost.getRemovedPath().split("\\.")[0]);

                        // if the first item in removedPath after the 0th is 'where', add that as well
                        boolean followedByWhere = false;
                        if (findingOuterMost.getRemovedPath().startsWith(".")) {
                            final String[] split = findingOuterMost.getRemovedPath().split("\\.");
                            if (split.length > 2) {
                                followedByWhere = split[2].startsWith("where");
                            }
                        } else {
                            final String[] split = findingOuterMost.getRemovedPath().split("\\.");
                            if (split.length > 1) {
                                followedByWhere = split[1].startsWith("where");
                            }
                        }

                        if (followedByWhere) {
                            preparedFullFhirPathForCachePopulation += ("." + openFhirStringUtils.extractWhereCondition(findingOuterMost.getRemovedPath()));
                        }

                        hardcodedReturn.setPath(preparedFullFhirPathForCachePopulation);


                        populateIntermediateCache(hardcodedReturn,
                                instance.toString(),
                                instantiatedIntermediateElements,
                                instance.getResourceType().name(),
                                fullOpenEhrPath,
                                helper.isFollowedBy(),
                                helper.getParentFollowedByFhirPath(),
                                helper.getParentFollowedByOpenEhr());
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
                            helper.getParentFollowedByOpenEhr(),
                            helper.getFhirPath());
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

    private void handleConditionMapping(final Condition condition,
                                        final Resource instance,
                                        final String fullOpenEhrPath,
                                        final Map<String, Object> instantiatedIntermediateElements,
                                        final String targetResource,
                                        final boolean isFollowedBy,
                                        final String parentFhirEhr,
                                        final String parentOpenEhr,
                                        final String fhirPath) {
        if (condition == null) {
            return;
        }
//        condition.setTargetRoot(condition.getTargetRoot().replace(FhirConnectConst.FHIR_RESOURCE_FC, targetResource));
        final String stringFromCriteria = openFhirStringUtils.getStringFromCriteria(condition.getCriteria()).getCode();

        final String commonPath = openFhirStringUtils.getCommonPaths(condition.getTargetRoot(), fhirPath);
        final String conditionFhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, targetResource, parentFhirEhr);
        final FindingOuterMost existing = findTheOuterMostThatExistsWithinCache(instantiatedIntermediateElements,
                instance,
                conditionFhirPathWithConditions,
                fullOpenEhrPath,
                "",
                isFollowedBy,
                parentFhirEhr,
                parentOpenEhr);
        existing.setRemovedPath(existing.getRemovedPath() + "." + condition.getTargetAttribute());

//        final String conditionPathWithoutParentsCondition = openFhirStringUtils.getFhirPathWithConditions(condition.getTargetRoot(), condition, targetResource, parentFhirEhr);
//        existing.setRemovedPath(conditionPathWithoutParentsCondition.replace(commonPath, ""));
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
            hardcodedReturn.setPath(conditionFhirPathWithConditions.replace(targetResource + ".", "")); // this may not be entirely correct, should probably replace differently....depending on whether targetRoot is fhirResource or not
            populateIntermediateCache(hardcodedReturn,
                    instance.toString(),
                    instantiatedIntermediateElements,
                    instance.getResourceType().name(),
                    fullOpenEhrPath,
                    isFollowedBy,
                    parentFhirEhr,
                    parentOpenEhr);

            fhirInstancePopulator.populateElement(getLastReturn(hardcodedReturn).getReturning(), new StringType(stringFromCriteria));
        }
    }

    private FindingOuterMost findTheOuterMostThatExistsWithinCache(final Map<String, Object> instantiatedIntermediateElements,
                                                                   final Resource coverInstance,
                                                                   final String fhirPathWithoutConditions,
                                                                   final String fullOpenEhrPath,
                                                                   final String removedPath,
                                                                   final boolean isFollowedBy,
                                                                   final String parentFollowedByMapping,
                                                                   final String parentFollowedByOpenEhr) {
        final String keyForIntermediateElements = createKeyForIntermediateElements(coverInstance.toString(), fhirPathWithoutConditions, fullOpenEhrPath);
        if (isFollowedBy && parentFollowedByOpenEhr != null) {
            // we need to ignore the openehr in the key because followed by means we need to find one that has already been created!
            final String preparedParentPath = openFhirStringUtils.prepareParentOpenEhrPath(parentFollowedByOpenEhr,
                    fullOpenEhrPath);
            String keyIgnoringOpenEhrPath = null;
            if (fullOpenEhrPath.contains(preparedParentPath)) {
                // means that child openehr path is a sub-path of the followed by parent
                keyIgnoringOpenEhrPath = createKeyForIntermediateElements(coverInstance.toString(), fhirPathWithoutConditions, preparedParentPath);
            } else {
                keyIgnoringOpenEhrPath = createKeyForIntermediateElements(coverInstance.toString(), fhirPathWithoutConditions, "");
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
        if (!fhirPathWithoutConditions.contains(".")) {
            return new FindingOuterMost(null, removedPath);
        }

        String nextPath = fhirPathWithoutConditions.substring(0, fhirPathWithoutConditions.lastIndexOf("."));
        String removingPath = fhirPathWithoutConditions.replace(nextPath, "") + removedPath;

        // if we'd be removing only a cast synatch (as(DateTimeType)), we actually need to remove more than that
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

    private FindingOuterMost getOrInstantiateIntermediateItem(final Map<String, Object> instantiatedIntermediateElements,
                                                              final Resource coverInstance,
                                                              final String fhirPathWithoutConditions,
                                                              final String type,
                                                              final String resolveResourceType,
                                                              final String fullOpenEhrPath,
                                                              final boolean isFollowedBy,
                                                              final String parentFollowedByMapping,
                                                              final String parentFollowedByOpenEhr,
                                                              final List<Resource> separatelyCreatedResources) {
        final FindingOuterMost existing = findTheOuterMostThatExistsWithinCache(instantiatedIntermediateElements,
                coverInstance,
                fhirPathWithoutConditions,
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

        final String[] splitFhirPath = fhirPathWithoutConditions.split("\\.");
        final String resType = splitFhirPath[0];
        if (StringUtils.isNotEmpty(resType) && !coverInstance.getResourceType().name().equals(resType)) {
            final Resource newCoverInstance = fhirInstanceCreator.create(resType);
            separatelyCreatedResources.add(newCoverInstance);
            hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(newCoverInstance,
                    newCoverInstance.getClass(),
                    fhirPathWithoutConditions.substring(fhirPathWithoutConditions.indexOf(".") + 1),
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
                    isFollowedBy,
                    parentFollowedByMapping,
                    parentFollowedByOpenEhr,
                    true);
        } else {
            hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(coverInstance,
                    coverInstance.getClass(),
                    fhirPathWithoutConditions,
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
                isFollowedBy,
                parentFollowedByMapping,
                parentFollowedByOpenEhr);

        return new FindingOuterMost(toSetCriteriaOn, null);
    }

    void populateIntermediateCache(final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn,
                                   final String objectRef,
                                   final Map<String, Object> instantiatedIntermediateElements,
                                   final String path,
                                   final String fullOpenEhrPath,
                                   final boolean followedBy,
                                   final String followedByParentFhir,
                                   final String followedByParentOpenEhr) {
        populateIntermediateCache(hardcodedReturn,
                objectRef,
                instantiatedIntermediateElements,
                path,
                fullOpenEhrPath,
                followedBy,
                followedByParentFhir,
                followedByParentOpenEhr,
                false);
    }

    void populateIntermediateCache(final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn,
                                   final String objectRef,
                                   final Map<String, Object> instantiatedIntermediateElements,
                                   final String path,
                                   final String fullOpenEhrPath,
                                   final boolean followedBy,
                                   final String followedByParentFhir,
                                   final String followedByParentOpenEhr,
                                   final boolean addingNewSeparateInstance) {

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
                    followedBy,
                    followedByParentFhir,
                    followedByParentOpenEhr);
            return;
        }
        if (followedBy && hardcodedReturn.isList() && followedByParentOpenEhr != null) {
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
                        followedBy,
                        followedByParentFhir,
                        followedByParentOpenEhr);
            }

//        } else if (followedByParentFhir != null && followedByParentFhir.replace("." + FHIR_ROOT_FC, "").endsWith(RESOLVE) || addingNewSeparateInstance) {
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
                        followedBy,
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
                        followedBy,
                        followedByParentFhir,
                        followedByParentOpenEhr);
            }
        }

    }

    private String createKeyForIntermediateElements(final String objectRef, final String fhirPath, final String fullOpenEhrPath) {
        // from full openEhrPath, only indexes should be part of the key. And even that, all indexes BUT the first one (because the first one is a Resource and that's the objectRef one)
        final String fixedFhirPath = fhirPath
                .replace("." + RESOLVE, "")
                .replace("." + FHIR_ROOT_FC, "")
                .replace(FHIR_ROOT_FC, "");
        return new StringJoiner("_").add(objectRef).add(fixedFhirPath).add(fullOpenEhrPath.replace("[n]", "")).toString();
    }

    private String createKey(final Integer index, final String limitingResourceCriteria) {
        return String.format("%s_%s", index, limitingResourceCriteria);
    }

    private Resource getOrCreateResource(final Map<String, Resource> createdPerIndex,
                                         final String targetResource,
                                         final String key) {
        if (createdPerIndex.containsKey(key)) {
            return createdPerIndex.get(key);
        }
        log.info("Create a new instance of a resource {}", targetResource);
        return fhirInstanceCreator.create(targetResource);
    }

    void mapAndGetAllOpenEhrToFhirHelpers(final FhirConnectMapper theMapper,
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
        for (Mapping mapping : mappings) {
            String openehr;
            if (StringUtils.isEmpty(mapping.getWith().getOpenehr())) {
                openehr = parentFollowedByOpenEhr;
            } else {
                openehr = openFhirStringUtils.prepareOpenEhrSyntax(mapping.getWith().getOpenehr(), firstFlatPath);
            }

            final String rmType;
            if (mapping.getWith().getType() == null) {
                final FhirToOpenEhrHelper getTypeHelper = FhirToOpenEhrHelper.builder()
                        .openEhrPath(openehr)
                        .build();
                openEhrRmWorker.fixFlatWithOccurrences(Arrays.asList(getTypeHelper), webTemplate);
                rmType = getTypeHelper.getOpenEhrType();
            } else {
                rmType = mapping.getWith().getType();
            }

            final String fhirPath = openFhirStringUtils.amendFhirPath(mapping.getWith().getFhir(),
                    null, // should condition be added here?
                    theMapper.getFhirConfig().getResource());

            if (mapping.getWith().getOpenehr() != null
                    && mapping.getWith().getOpenehr().startsWith(FhirConnectConst.REFERENCE) && mapping.getReference() != null) {
                // a reference mapping
                // prepare 'reference' mappings
                final List<Mapping> referencedMapping = mapping.getReference().getMappings();
                final String wConditions = openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(), mapping.getCondition(), resourceType, parentFollowedByFhir);
                openFhirMapperUtils.prepareReferencedMappings(wConditions, openehr, referencedMapping);

                // now conditions
                if (mapping.getCondition() != null) {
                    // add it because in this case mapping actually needs to do something
                    final String parentFollowedByFhirPath = parentFollowedByFhir == null ? null : parentFollowedByFhir.replace(FhirConnectConst.FHIR_RESOURCE_FC, resourceType);
                    final String parentFollowedByOpenEhr1 = parentFollowedByOpenEhr == null ? null : parentFollowedByOpenEhr.replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, firstFlatPath);
                    OpenEhrToFhirHelper openEhrToFhirHelper = OpenEhrToFhirHelper.builder()
                            .mainArchetype(theMapper.getOpenEhrConfig().getArchetype())
                            .targetResource(resourceType)
                            .openEhrPath(mapping.getWith().getOpenehr())
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

                mapAndGetAllOpenEhrToFhirHelpers(theMapper,
                        mapping.getReference().getResourceType(),
                        firstFlatPath,
                        mapping.getReference().getMappings(),
                        helpers,
                        webTemplate,
                        flatJsonObject,
                        false,
                        parentFollowedByFhir,
                        parentFollowedByOpenEhr,
                        slotContext);

            } else {
                if (openehr.endsWith("content/content") && "MEDIA".equals(rmType)) {
                    openehr = openehr.substring(0, openehr.length() - "/content".length()); // remove the last /content part, because the path is content/content which is not ok for openEhr—>fhir
                }
                boolean manuallyAddingOccurrence = openehr.contains("[n]");
                if (manuallyAddingOccurrence) {
                    // for cases when you're manually adding recurring syntach to an openEHR path for whatever reason
                    // (but mostly due to context weird behavior when you have _participation)
                    openehr = openehr.replaceAll("\\[n\\]", "");
                }
                final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(openehr);
                final List<String> matchingEntries = getAllEntriesThatMatch(withRegex, flatJsonObject);
                final Map<String, List<String>> joinedEntries = joinValuesThatAreOne(matchingEntries);

                if (mapping.getSlotArchetype() != null) {
                    final String templateId = webTemplate.getTemplateId();
                    // todo!! remove after revisiting what acp poc has wrong with template ids
                    final String acpPocLogic = templateId.equals("ACP_POC") ? firstFlatPath : templateId;
                    // -- end of
                    final List<FhirConnectMapper> slotArchetypeMapperss = openFhirTemplateRepo.getSlotMapperForArchetype(acpPocLogic, mapping.getSlotArchetype());
                    for (FhirConnectMapper slotArchetypeMappers : slotArchetypeMapperss) {
                        openFhirMapperUtils.prepareForwardingSlotArchetypeMapper(slotArchetypeMappers, theMapper, fhirPath, openehr, firstFlatPath);
                        mapAndGetAllOpenEhrToFhirHelpers(slotArchetypeMappers,
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

                        if (mapping.getFollowedBy() != null) {
                            final List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();

                            openFhirMapperUtils.prepareFollowedByMappings(followedByMappings,
                                    fhirPath,
                                    openehr,
                                    firstFlatPath);

                            mapAndGetAllOpenEhrToFhirHelpers(theMapper,
                                    resourceType,
                                    firstFlatPath,
                                    followedByMappings,
                                    helpers,
                                    webTemplate,
                                    flatJsonObject,
                                    true,
                                    openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(), mapping.getCondition(), resourceType, parentFollowedByFhir),
                                    mapping.getWith().getOpenehr(),
                                    slotContext);
                        }
                    }
                } else {
                    if (!OPENEHR_TYPE_NONE.equals(mapping.getWith().getType())
                            && !FhirConnectConst.OPENEHR_TYPE_DOSAGE.equals(mapping.getWith().getType())) {
                        final List<OpenEhrToFhirHelper.DataWithIndex> values = joinedEntries.entrySet().stream()
                                .map((entry) -> valueToDataPoint(entry.getValue(), rmType, flatJsonObject, true))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                        OpenEhrToFhirHelper openEhrToFhirHelper = OpenEhrToFhirHelper.builder()
                                .mainArchetype(theMapper.getOpenEhrConfig().getArchetype())
                                .targetResource(resourceType)
                                .openEhrPath(mapping.getWith().getOpenehr())
                                .fhirPath(fhirPath) // fhir path here should not have the full, rather just the normal path to the data point, resource limiting is done in other places
                                .openEhrType(mapping.getWith().getType())
                                .data(values)
                                .isFollowedBy(isFollowedBy)
                                .parentFollowedByFhirPath(parentFollowedByFhir == null ? null : parentFollowedByFhir.replace(FhirConnectConst.FHIR_RESOURCE_FC, resourceType))
                                .parentFollowedByOpenEhr(parentFollowedByOpenEhr == null ? null : parentFollowedByOpenEhr.replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, firstFlatPath))
                                .condition(mapping.getCondition())
                                .build();
                        helpers.add(openEhrToFhirHelper);
                    }
                    if (OPENEHR_TYPE_NONE.equals(mapping.getWith().getType()) && mapping.getCondition() != null) {
                        final List<OpenEhrToFhirHelper.DataWithIndex> values = joinedEntries.entrySet().stream()
                                .map((entry) -> valueToDataPoint(entry.getValue(), rmType, flatJsonObject, false))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                        final String openEhrPath = openFhirStringUtils.prepareOpenEhrSyntax(mapping.getWith().getOpenehr(), firstFlatPath);
                        OpenEhrToFhirHelper openEhrToFhirHelper = OpenEhrToFhirHelper.builder()
                                .mainArchetype(theMapper.getOpenEhrConfig().getArchetype())
                                .targetResource(resourceType)
                                .openEhrPath(openEhrPath)
                                .fhirPath(fhirPath) // fhir path here should not have the full, rather just the normal path to the data point, resource limiting is done in other places
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

                        mapAndGetAllOpenEhrToFhirHelpers(theMapper,
                                resourceType,
                                firstFlatPath,
                                followedByMappings,
                                helpers,
                                webTemplate,
                                flatJsonObject,
                                true,
                                openFhirStringUtils.getFhirPathWithConditions(mapping.getWith().getFhir(), mapping.getCondition(), resourceType, parentFollowedByFhir),
                                mapping.getWith().getOpenehr() == null ? firstFlatPath : mapping.getWith().getOpenehr(),
                                slotContext);
                    }

                }
            }

        }
    }

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
     *
     * @return
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

    private OpenEhrToFhirHelper.DataWithIndex valueToDataPoint(final List<String> joinedValues,
                                                               final String targetType,
                                                               final JsonObject valueHolder,
                                                               boolean canBeNull) {
        if (joinedValues == null || joinedValues.isEmpty()) {
            return null;
        }
        final String path = joinedValues.get(0);
        final Integer lastIndex = openFhirStringUtils.getLastIndex(path);
        final String value = joinedValues.stream().filter(s -> s.endsWith("value")).findAny().orElse(null);
        final String code = joinedValues.stream().filter(s -> s.endsWith("code")).findAny().orElse(null);
        final String terminology = joinedValues.stream().filter(s -> s.endsWith("terminology")).findAny().orElse(null);
        final String id = joinedValues.stream().filter(s -> s.endsWith("id")).findAny().orElse(null);
        switch (targetType) {
            case "PROPORTION":
                final String proportionVal = joinedValues.get(0);
                final String numerator = proportionVal + "|numerator";
                final String denominator = proportionVal + "|denominator";
                final Quantity proportionQuantity = new Quantity();
                if (getFromValueHolder(valueHolder, denominator) != null && getFromValueHolder(valueHolder, denominator).equals("100.0")) {
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
                    quantity.setValue(magVal == null ? null : (magVal instanceof Long ? (Long) magVal : (Double) magVal));
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
                att.setSize(size == null ? null : Integer.valueOf(size));
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

    List<String> getAllEntriesThatMatch(final String withRegex, final JsonObject flatted) {
        Pattern compiledPattern = Pattern.compile(withRegex);
        final List<String> match = new ArrayList<>();
        for (Map.Entry<String, JsonElement> flatEntry : flatted.entrySet()) {
            Matcher matcher = compiledPattern.matcher(flatEntry.getKey());

            List<String> matches = new ArrayList<>();

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
