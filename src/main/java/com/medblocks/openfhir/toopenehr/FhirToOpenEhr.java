package com.medblocks.openfhir.toopenehr;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.model.Condition;
import com.medblocks.openfhir.fc.model.*;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenEhrPopulator;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datatypes.CodePhrase;
import com.nedap.archie.rm.generic.PartySelf;
import com.nedap.archie.rm.support.identification.TerminologyId;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.*;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.*;

@Slf4j
@Component
public class FhirToOpenEhr {


    final private FhirPathR4 fhirPathR4;
    final private OpenFhirStringUtils stringUtils;
    final private FlatJsonUnmarshaller flatJsonUnmarshaller;
    final private Gson gson;
    final private OpenEhrRmWorker openEhrRmWorker;
    final private OpenFhirStringUtils openFhirStringUtils;
    final private OpenFhirMappingContext openFhirTemplateRepo;
    final private OpenEhrCachedUtils openEhrApplicationScopedUtils;
    final private OpenFhirMapperUtils openFhirMapperUtils;
    final private OpenEhrPopulator openEhrPopulator;

    @Autowired
    public FhirToOpenEhr(final FhirPathR4 fhirPathR4,
                         final OpenFhirStringUtils stringUtils,
                         final FlatJsonUnmarshaller flatJsonUnmarshaller,
                         final Gson gson,
                         final OpenEhrRmWorker openEhrRmWorker,
                         final OpenFhirStringUtils openFhirStringUtils,
                         final OpenFhirMappingContext openFhirTemplateRepo,
                         final OpenEhrCachedUtils openEhrApplicationScopedUtils,
                         final OpenFhirMapperUtils openFhirMapperUtils,
                         final OpenEhrPopulator openEhrPopulator) {
        this.fhirPathR4 = fhirPathR4;
        this.stringUtils = stringUtils;
        this.flatJsonUnmarshaller = flatJsonUnmarshaller;
        this.gson = gson;
        this.openEhrRmWorker = openEhrRmWorker;
        this.openFhirStringUtils = openFhirStringUtils;
        this.openFhirTemplateRepo = openFhirTemplateRepo;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.openEhrPopulator = openEhrPopulator;
    }

    /**
     * Main method that takes care of mapping from FHIR to openEHR. Mapping is always done to a flat path that can
     * later on be converted to a canonical JSON format.
     *
     * @param context             fhir connect context mapper
     * @param resource            that needs to be mapped to openEHR, can be a Bundle or a single Resource
     * @param operationaltemplate that is linked to the context mapper
     * @return JsonObject representing a flat path structure/format of the mapped openEHR Composition
     */
    public JsonObject fhirToFlatJsonObject(final FhirConnectContext context, final Resource resource, final OPERATIONALTEMPLATE operationaltemplate) {
        final boolean bundle = ResourceType.Bundle.name().equals(context.getFhir().getResourceType());

        // mapping is always done on a Bundle, therefore if the incoming Resource is not a Bundle, we first wrap it to one
        final Bundle toRunEngineOn = prepareBundle(resource);

        final WebTemplate webTemplate = openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(context.getOpenEHR().getTemplateId());

        // helper objects for mapping to openEHR, where 'helpers' are regular ones constructed as part of the
        // mapping and 'coverHelpers' are those that don't directly reference the FHIR Resource but another one
        // for example a Condition that is part of the Patient's death reason
        final List<FhirToOpenEhrHelper> helpers = new ArrayList<>();
        final List<FhirToOpenEhrHelper> coverHelpers = new ArrayList<>();

        // create helpers
        createHelpers(templateId, toRunEngineOn, null, helpers, coverHelpers, bundle);

        // join all helpers together
        helpers.addAll(coverHelpers);

        openFhirStringUtils.fixEscapedDotsInOpenEhrPaths(helpers);

        // modify flat path with correct openEHR path containing occurrences and proper types
        openEhrRmWorker.fixFlatWithOccurrences(helpers, webTemplate);

        // do the actual mapping (evaluate fhir paths and create json flat structure from it, based on helpers)
        return resolveFhirPaths(helpers, toRunEngineOn);
    }

    /**
     * If resource is not already a bundle, it will wrap it to a Bundle
     * additional business logic can be done here to make sure references between resources within a Bundle
     * are properly done and prepared for the mapping engine
     */
    private Bundle prepareBundle(final Resource resource) {
        final Bundle toRunEngineOn;
        if (!(resource instanceof Bundle)) {
            toRunEngineOn = new Bundle();
            toRunEngineOn.addEntry(new Bundle.BundleEntryComponent().setResource(resource));
        } else {
            toRunEngineOn = (Bundle) resource;

            // fix referenced resources?
        }
        return toRunEngineOn;
    }

    /**
     * Mapping to a canonical format of a Composition. This method invokes the fhirToFlatJsonObject and then serializes
     * flat json path output to a canonical format.
     *
     * @param context             fhir connect context being used for the mappings
     * @param resource            FHIR Resource that's being mapped to openEHR
     * @param operationaltemplate openEHR template being referenced by this context mapper
     * @return Composition that's been created based on the FHIR input and fhir connect mappers
     */
    public Composition fhirToCompositionRm(final FhirConnectContext context, final Resource resource, final OPERATIONALTEMPLATE operationaltemplate) {
        final WebTemplate webTemplate = openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);

        // invoke the actual mapping logic
        final JsonObject flattenedWithValues = fhirToFlatJsonObject(context, resource, operationaltemplate);

        // unmarshall flat path to a canonical json format
        final Composition composition = flatJsonUnmarshaller.unmarshal(gson.toJson(flattenedWithValues), webTemplate);

        enrichComposition(composition);

        return composition;
    }

    /**
     * Method that adds all required metadata to a Composition, but only if this was not already set as part
     * of the mapping logic itself.
     *
     * @param composition enriched with metadata that wasn't mapped
     */
    public void enrichComposition(final Composition composition) {
        // default values; set if not already set by mappings
        if (composition.getLanguage() == null) {
            composition.setLanguage(new CodePhrase(new TerminologyId("ISO_639-1"), "en"));
        }
        if (composition.getTerritory() == null) {
            composition.setTerritory(new CodePhrase(new TerminologyId("ISO_3166-1"), "DE"));
        }
        if (composition.getComposer() == null) {
            composition.setComposer(new PartySelf());
        }
    }

    /**
     * Given FhirToOpenEhrHelpers, this method creates the actual json flat structure based on them. It evaluates
     * fhir paths from the incoming Resource and sets them in a JsonObject with the right openEHR flat path.
     *
     * @param helpers  used to do the mapping
     * @param resource to be mapped to openEHR
     * @return JsonObject representing a flat path structure/format of the openEHR Composition
     */
    private JsonObject resolveFhirPaths(final List<FhirToOpenEhrHelper> helpers, final Resource resource) {
        final JsonObject finalFlat = new JsonObject();

        final Map<String, List<FhirToOpenEhrHelper>> byMainArtifact = mapperByMainArtifact(helpers);
        for (Map.Entry<String, List<FhirToOpenEhrHelper>> artifactMapper : byMainArtifact.entrySet()) {
            final List<FhirToOpenEhrHelper> artifactHelpers = artifactMapper.getValue();

            // distinct by limiting criteria to avoid duplicated mappings
            artifactHelpers.stream().map(FhirToOpenEhrHelper::getLimitingCriteria).distinct().forEach(lim -> {

                if (resource instanceof Bundle) {
                    handleBundleExtraction((Bundle) resource, lim, artifactHelpers, finalFlat);
                } else {
                    for (FhirToOpenEhrHelper fhirToOpenEhrHelper : artifactHelpers) {
                        final List<Base> result = fhirPathR4.evaluate(resource, fhirToOpenEhrHelper.getFhirPath(), Base.class);

                        handleOccurrenceResults(fhirToOpenEhrHelper.getOpenEhrPath(), fhirToOpenEhrHelper.getOpenEhrType(), result, finalFlat);
                    }

                }
            });

        }

        return finalFlat;
    }

    /**
     * Resolve fhir paths from a Bundle
     */
    private void handleBundleExtraction(final Bundle resource, final String lim,
                                        final List<FhirToOpenEhrHelper> artifactHelpers, final JsonObject finalFlat) {
        // apply limiting factor
        final List<Base> relevantResources = fhirPathR4.evaluate(resource, lim, Base.class);

        if (relevantResources.isEmpty()) {
            log.warn("No relevant resources found for {}", lim);
        } else {
            log.info("Evaluation of {} returned {} entries that will be used for mapping.", lim, relevantResources.size());
        }

        String mainMultiple = null;

        int i = 0;
        for (final Base relevantResource : relevantResources) {
            boolean somethingWasAdded = false;
            for (final FhirToOpenEhrHelper fhirToOpenEhrHelper : artifactHelpers) {
                final FhirToOpenEhrHelper cloned = fhirToOpenEhrHelper.doClone();
                if (fhirToOpenEhrHelper.getMultiple() && (mainMultiple == null || fhirToOpenEhrHelper.getOpenEhrPath().startsWith(mainMultiple))) {
                    mainMultiple = fhirToOpenEhrHelper.getOpenEhrPath().split(RECURRING_SYNTAX_ESCAPED)[0];
                    cloned.setOpenEhrPath(fhirToOpenEhrHelper.getOpenEhrPath().replaceFirst(RECURRING_SYNTAX_ESCAPED, ":" + i));

                    fixAllChildrenRecurringElements(cloned, fhirToOpenEhrHelper.getOpenEhrPath(), cloned.getOpenEhrPath());
                }
                int previousFinalFlatSize = finalFlat.size();
                addDataPoints(cloned, finalFlat, relevantResource);

                somethingWasAdded = somethingWasAdded || previousFinalFlatSize < finalFlat.size();
            }
            if (somethingWasAdded) {
                i++;
            } else {
                log.warn("Even though a Resource matched criteria, nothing was added to the openEHR composition from it: {}", relevantResource.getIdBase());
            }
        }
    }

    /**
     * Joins all helpers of the same archetype
     *
     * @return a Map where key is the artifactId and value is a list of all corresponding Helpers
     */
    private Map<String, List<FhirToOpenEhrHelper>> mapperByMainArtifact(final List<FhirToOpenEhrHelper> flattened) {
        return flattened.stream().collect(Collectors.groupingBy(FhirToOpenEhrHelper::getArchetype));
    }

    JsonObject handleOccurrenceResults(final String openEhrPath, final String openEhrType, final List<Base> fhirPathResults, final JsonObject finalFlat) {
        if (fhirPathResults == null || fhirPathResults.isEmpty()) {
            return finalFlat;
        }
        final boolean noMoreRecurringOptions = !openEhrPath.contains(RECURRING_SYNTAX);
        final String openEhrWithAllReplacedToZeroth = openEhrPath.replaceAll(RECURRING_SYNTAX_ESCAPED, ":0");
        if (fhirPathResults.size() == 1) {
            // it's a single find, so replace all those multiple-occurrences with zeroth index
            openEhrPopulator.setFhirPathValue(openEhrWithAllReplacedToZeroth, fhirPathResults.get(0), openEhrType, finalFlat);
        } else {
            // many results, set all but the last one to :0.. the last one index according to amount of results in fhirPathResults
            if (noMoreRecurringOptions) {
                log.warn("Found more than one result, yet there's no more recurring options! Only adding the first result to openEhr flat.");
                openEhrPopulator.setFhirPathValue(openEhrWithAllReplacedToZeroth, fhirPathResults.get(0), openEhrType, finalFlat);
            } else {
                for (int i = 0; i < fhirPathResults.size(); i++) {
                    final Base fhirPathResult = fhirPathResults.get(i);
                    final String finalOpenEhrPath = openFhirStringUtils.replaceLastIndexOf(openEhrWithAllReplacedToZeroth, ":0", ":" + i);

                    openEhrPopulator.setFhirPathValue(finalOpenEhrPath, fhirPathResult, openEhrType, finalFlat);
                }
            }
        }
        return null;
    }


    /**
     * Iterates over Helpers (and inner helpers) and evaluates FHIR path throughout the recursion while adding
     * fhir path evaluation results to the flat json structure
     *
     * @param helper          helper that is being evaluated at each iteration of the recursion
     * @param flatComposition json object representing composition in a flat format that is being created throughout
     *                        the recursion
     * @param toResolveOn     FHIR object where we're evaluating fhir path on
     */
    void addDataPoints(final FhirToOpenEhrHelper helper, final JsonObject flatComposition, final Base toResolveOn) {
        List<Base> results;
        if (StringUtils.isEmpty(helper.getFhirPath()) || FHIR_ROOT_FC.equals(helper.getFhirPath())) {
            // just take the one roResolveOn
            log.debug("Taking Base itself as fhirPath is {}", helper.getFhirPath());
            results = Collections.singletonList(toResolveOn);
        } else {
            results = fhirPathR4.evaluate(toResolveOn, openFhirStringUtils.fixFhirPathCasting(helper.getFhirPath()), Base.class);
            if (helper.getFhirPath().endsWith(RESOLVE) && results.isEmpty()) {
                final List<Base> reference = fhirPathR4.evaluate(toResolveOn, helper.getFhirPath().replace("." + RESOLVE, ""), Base.class);
                if (!reference.isEmpty()) {
                    results = reference.stream()
                            .filter(ref -> ref instanceof Reference)
                            .map(ref -> (Base) ((Reference) ref).getResource())
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }
            }
        }
        if (results == null || results.isEmpty()) {
            // todo: here within helpers we should have an info whether something is required in openEHR template or not
            // if it is required, add nullflavors
            log.warn("No results found for FHIRPath {}, evaluating on type: {}", helper.getFhirPath(), toResolveOn.getClass());
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            Base result = results.get(i);
            final boolean noMoreRecurringOptions = !helper.getOpenEhrPath().contains(RECURRING_SYNTAX);
            final String thePath = noMoreRecurringOptions ? helper.getOpenEhrPath() : openFhirStringUtils.replaceLastIndexOf(helper.getOpenEhrPath(), RECURRING_SYNTAX, ":" + i);
            log.debug("Setting value taken with fhirPath {} from object type {}", helper.getFhirPath(), toResolveOn.getClass());

            if (StringUtils.isNotEmpty(helper.getHardcodingValue())) {
                log.debug("Hardcoding value {} to path: {}", helper.getHardcodingValue(), thePath);
                // is it ok we use string type here? could it be something else? probably it could be..
                openEhrPopulator.setFhirPathValue(thePath, new StringType(helper.getHardcodingValue()), helper.getOpenEhrType(), flatComposition);
            } else {
                openEhrPopulator.setFhirPathValue(thePath, result, helper.getOpenEhrType(), flatComposition);
            }


            if (helper.getFhirToOpenEhrHelpers() != null) {
                // iterate over inner elements
                for (FhirToOpenEhrHelper fhirToOpenEhrHelper : helper.getFhirToOpenEhrHelpers()) {
                    FhirToOpenEhrHelper copy = fhirToOpenEhrHelper.doClone();

                    if (copy.getOpenEhrPath().startsWith(helper.getOpenEhrPath())) {
                        final String newOne = copy.getOpenEhrPath().replace(helper.getOpenEhrPath(), thePath);

                        fixAllChildrenRecurringElements(copy, helper.getOpenEhrPath(), newOne);

                        addDataPoints(copy, flatComposition, result);
                    } else {
                        addDataPoints(copy, flatComposition, result);
                    }
                }
            }

            if (noMoreRecurringOptions) {
                break;
            }

        }
    }

    /**
     * Adds proper recurring index to all child elements if parent is the recurring one
     */
    void fixAllChildrenRecurringElements(final FhirToOpenEhrHelper helper, final String parent, final String newOne) {
        if (stringUtils.childStartsWithParent(helper.getOpenEhrPath(), parent)) {
            final String replaced = stringUtils.replacePattern(helper.getOpenEhrPath(), newOne);
            helper.setOpenEhrPath(replaced);
        }
        if (helper.getFhirToOpenEhrHelpers() == null) {
            return;
        }
        for (FhirToOpenEhrHelper fhirToOpenEhrHelper : helper.getFhirToOpenEhrHelpers()) {
            fixAllChildrenRecurringElements(fhirToOpenEhrHelper, parent, newOne);
        }
    }


    /**
     * Creates helpers for each individual model mapper defined for the incoming FHIR Resource
     */
    void createHelpers(final String templateId, final Resource resource, final Condition parentCondition, final List<FhirToOpenEhrHelper> helpers, final List<FhirToOpenEhrHelper> coverHelpers, final boolean bundle) {
        ((Bundle) resource).getEntry().forEach(entry -> {
            final List<FhirConnectMapper> mapperForResources = openFhirTemplateRepo.getMapperForResource(entry.getResource());
            if (mapperForResources == null || mapperForResources.isEmpty()) {
                return;
            }
            for (FhirConnectMapper mapperForResource : mapperForResources) {
                final String mainArchetype = mapperForResource.getOpenEhrConfig().getArchetype();
                createHelpers(mainArchetype, mapperForResource, templateId, templateId, mapperForResource.getMappings(), parentCondition, helpers, coverHelpers, bundle, mapperForResource.getFhirConfig().getMultiple());
            }
        });
    }

    /**
     * Creates a list of helpers for FHIR to openEHR mappings based on fhir connect model mappers. While creating
     * helpers, it modifies FHIR path and openEHR paths according to fhir connect rules (slot mapping, followed by mapping,
     * reference, ..) and makes sure all child mappings are added to helpers in inner structure (FhirToOpenEhrHelper
     * has a reference to more related FhirToOpenEhrHelpers).
     *
     * @param mainArtifact      main archetype this helper is being created for
     * @param fhirConnectMapper fhir connect mapper
     * @param templateId        openehr template id
     * @param mainOpenEhrPath   main open ehr path (first item in a flat path structure), usually a template ID,
     *                          but not necessarily - if we have slot mappings or followed by mappings, this would be the
     *                          base flat path being used for context for further child mappings
     * @param mappings          mappings being handled in this iteration of the recursion
     * @param parentCondition   condition of a parent's element
     * @param helpers           a list of helpers being created
     * @param coverHelpers      a list of cover helpers being created
     * @param bundle            whether a fhir context mapper is expecting a Bundle
     * @param multiple          if a specific model mapper should create multiple Resources instead of a single one
     */
    void createHelpers(final String mainArtifact,
                       final FhirConnectMapper fhirConnectMapper,
                       final String templateId,
                       final String mainOpenEhrPath,
                       final List<Mapping> mappings,
                       final Condition parentCondition,
                       final List<FhirToOpenEhrHelper> helpers,
                       final List<FhirToOpenEhrHelper> coverHelpers,
                       final boolean bundle,
                       final boolean multiple) {
        for (final Mapping mapping : mappings) {
            final With with = mapping.getWith();
            if (with.getOpenehr() == null && StringUtils.isNotEmpty(with.getValue())) {
                // this is hardcoding to FHIR, nothing to do here which is mapping to openEHR
                continue;
            }
            if (with.getUnidirectional() != null && FhirConnectConst.UNIDIRECTIONAL_TOFHIR.equals(with.getUnidirectional())) {
                // this is unidirectional mapping toFhir only, ignore
                continue;
            }
            final FhirToOpenEhrHelper initialHelper = createHelper(mainArtifact, fhirConnectMapper, bundle);
            if (with.getOpenehr() != null && with.getOpenehr().startsWith(FhirConnectConst.OPENEHR_CONTEXT_FC)) {
                continue;
            }

            hardcodingToFhir(mapping, fhirConnectMapper, initialHelper);

            final Condition condition = parentCondition != null ? parentCondition : mapping.getCondition();

            final String fhirPath = openFhirStringUtils.getFhirPathWithConditions(with.getFhir(),
                                                                                  condition,
                                                                                  fhirConnectMapper.getFhirConfig().getResource(),
                                                                                  null);

            // because it references Resources not directly tied to a Resource itself, i.e. Condition as a cause of death for a Patient
            final boolean needsToBeAddedToParentHelpers = StringUtils.isNotEmpty(fhirPath)
                    && Character.isUpperCase(fhirPath.charAt(0))
                    && !fhirPath.startsWith(fhirConnectMapper.getFhirConfig().getResource());


            if (with.getOpenehr().contains(FhirConnectConst.REFERENCE) && mapping.getReference() != null) {
                createReferenceMapping(mapping, fhirPath, mainArtifact, fhirConnectMapper, templateId, mainOpenEhrPath,
                        parentCondition, helpers, coverHelpers, bundle, multiple);
            } else {
                final String openehr = createMainMapping(mapping, fhirConnectMapper, initialHelper, mainOpenEhrPath, fhirPath, multiple,
                        needsToBeAddedToParentHelpers, helpers, coverHelpers);

                // inner helpers are those that follow a parent one (when mapping is followedBy or slot archetype)
                final List<FhirToOpenEhrHelper> innerHelpers = new ArrayList<>();
                if (mapping.getFollowedBy() != null) {
                    createFollowedByMappings(initialHelper, mapping, openehr, mainOpenEhrPath, fhirPath, multiple, innerHelpers,
                            fhirConnectMapper, mainArtifact, templateId, coverHelpers, bundle, needsToBeAddedToParentHelpers,
                            helpers);
                }
                if (mapping.getSlotArchetype() != null) {
                    createSlotMappings(initialHelper, mapping, openehr, mainOpenEhrPath, fhirPath, multiple, innerHelpers,
                            fhirConnectMapper, mainArtifact, templateId, coverHelpers, bundle, needsToBeAddedToParentHelpers,
                            helpers);
                }
            }
        }
    }

    private void createFollowedByMappings(final Mapping mapping, final String openehr, final String openEhrPath) {
        for (final Mapping followedByMapping : mapping.getFollowedBy().getMappings()) {
            if (!followedByMapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                final String followedByOpenEhrPath = followedByMapping.getWith().getOpenehr();
                final String delimeter = followedByOpenEhrPath.startsWith("|") ? "" : "/";
                followedByMapping.getWith().setOpenehr(openehr.replace(FhirConnectConst.REFERENCE + "/", "") + delimeter + followedByOpenEhrPath
                        .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + ".", "")
                        .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, ""));
            } else {
                if (followedByMapping.getWith().getOpenehr().equals(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                    // if you did $openEhrArchetype, then we replace with parent's path
                    followedByMapping.getWith().setOpenehr(openehr);
                } else {
                    // if you prefixed it with $openEhrArchetype, it means you know what you're setting yourself
                    followedByMapping.getWith().setOpenehr(followedByMapping.getWith().getOpenehr()
                            .replace(FhirConnectConst.REFERENCE + ".", "")
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + ".", openEhrPath + ".")
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, openEhrPath));
                }
            }

        }
    }

    private void createFollowedByMappings(final FhirToOpenEhrHelper initialHelper, final Mapping mapping,
                                          final String openehr, final String mainOpenEhrPath, final String fhirPath,
                                          final boolean multiple, final List<FhirToOpenEhrHelper> innerHelpers,
                                          final FhirConnectMapper fhirConnectMapper, final String mainArtifact, final String templateId,
                                          final List<FhirToOpenEhrHelper> coverHelpers, final boolean bundle, final boolean needsToBeAddedToParentHelpers,
                                          final List<FhirToOpenEhrHelper> helpers) {
        final List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();

        createFollowedByMappings(mapping, openehr, mainOpenEhrPath);

        initialHelper.setOpenEhrPath(openFhirStringUtils.fixOpenEhrPath(openehr, mainOpenEhrPath));
        initialHelper.setFhirPath(openFhirStringUtils.fixFhirPath(fhirPath));

        initialHelper.setMultiple(multiple);

        initialHelper.setFhirToOpenEhrHelpers(innerHelpers);

        fixLimitingCriteriaForInnerCreatedResources(fhirConnectMapper.getFhirConfig().getResource(), initialHelper);

        // recursive call to create helpers for the followedByMappings
        createHelpers(mainArtifact,
                fhirConnectMapper,
                templateId,
                mainOpenEhrPath,
                followedByMappings,
                null,
                innerHelpers,
                coverHelpers,
                bundle,
                multiple);

        if (needsToBeAddedToParentHelpers) {
            coverHelpers.add(initialHelper);
        } else {
            if (!helpers.contains(initialHelper)) {
                helpers.add(initialHelper);
            }
        }
    }

    private void createSlotMappings(final FhirToOpenEhrHelper initialHelper, final Mapping mapping,
                                    final String openehr, final String mainOpenEhrPath, final String fhirPath,
                                    final boolean multiple, final List<FhirToOpenEhrHelper> innerHelpers,
                                    final FhirConnectMapper fhirConnectMapper, final String mainArtifact, final String templateId,
                                    final List<FhirToOpenEhrHelper> coverHelpers, final boolean bundle, final boolean needsToBeAddedToParentHelpers,
                                    final List<FhirToOpenEhrHelper> helpers) {
        final List<FhirConnectMapper> slotArchetypeMapperss = openFhirTemplateRepo.getSlotMapperForArchetype(templateId, mapping.getSlotArchetype());

        for (FhirConnectMapper slotArchetypeMappers : slotArchetypeMapperss) {
            final String openEhrFixed = openehr.replace("/" + FhirConnectConst.REFERENCE, "");

            openFhirMapperUtils.prepareForwardingSlotArchetypeMapperNoFhirPrefix(slotArchetypeMappers, fhirConnectMapper, fhirPath, openEhrFixed);

            initialHelper.setFhirToOpenEhrHelpers(innerHelpers);

            initialHelper.setOpenEhrPath(openFhirStringUtils.fixOpenEhrPath(openehr, mainOpenEhrPath));
            initialHelper.setFhirPath(openFhirStringUtils.fixFhirPath(fhirPath));

            fixLimitingCriteriaForInnerCreatedResources(fhirConnectMapper.getFhirConfig().getResource(), initialHelper);

            // recursive call to create helpers for the slot archetype mappings
            createHelpers(mainArtifact,
                    slotArchetypeMappers,
                    templateId, // templateId
                    openEhrFixed, // templateId
                    slotArchetypeMappers.getMappings(),
                    null,
                    innerHelpers,
                    coverHelpers,
                    bundle,
                    multiple);
            if (needsToBeAddedToParentHelpers) {
                coverHelpers.add(initialHelper);
            } else {
                if (!helpers.contains(initialHelper)) {
                    helpers.add(initialHelper);
                }
            }
        }
    }

    private void hardcodingToFhir(final Mapping mapping, final FhirConnectMapper fhirConnectMapper, final FhirToOpenEhrHelper initialHelper) {
        if (mapping.getWith().getFhir() == null) {
            // is hardcoding
            mapping.getWith().setFhir(fhirConnectMapper.getFhirConfig().getResource());
            initialHelper.setHardcodingValue(mapping.getWith().getValue());
        }
    }

    private String createMainMapping(final Mapping mapping, final FhirConnectMapper fhirConnectMapper, final FhirToOpenEhrHelper initialHelper,
                                     final String mainOpenEhrPath, final String fhirPath, final boolean multiple,
                                     final boolean needsToBeAddedToParentHelpers,
                                     final List<FhirToOpenEhrHelper> helpers, final List<FhirToOpenEhrHelper> coverHelpers) {
        String openehr = stringUtils.prepareOpenEhrSyntax(mapping.getWith().getOpenehr(), mainOpenEhrPath);
        if (mapping.getWith().getType() == null) {
            // when type is not explicitly defined in the fhir connect model mapper, we assume a string
            if (openFhirStringUtils.endsWithOpenEhrType(openehr) != null) {
                openehr = stringUtils.replaceLastIndexOf(openehr, "/", "|");
            }
        } else {
            initialHelper.setOpenEhrType(mapping.getWith().getType());
        }

        if (!OPENEHR_TYPE_NONE.equals(mapping.getWith().getType())) {
            initialHelper.setOpenEhrPath(openehr.replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + "/", mainOpenEhrPath + "/"));
            initialHelper.setFhirPath(fhirPath.replace("." + FHIR_ROOT_FC, ""));
            initialHelper.setMultiple(multiple);
            fixLimitingCriteriaForInnerCreatedResources(fhirConnectMapper.getFhirConfig().getResource(), initialHelper);
            if (needsToBeAddedToParentHelpers) {
                coverHelpers.add(initialHelper);
            } else {
                helpers.add(initialHelper);
            }
        }
        return openehr;
    }

    private void createReferenceMapping(final Mapping mapping, final String fhirPath, final String mainArtifact, final FhirConnectMapper fhirConnectMapper,
                                        final String templateId, final String mainOpenEhrPath, final Condition parentCondition,
                                        final List<FhirToOpenEhrHelper> helpers, final List<FhirToOpenEhrHelper> coverHelpers,
                                        final boolean bundle, final boolean multiple) {
        // a reference mapping; prepare 'reference' mappings
        final List<Mapping> referencedMapping = mapping.getReference().getMappings();
        openFhirMapperUtils.prepareReferencedMappings(fhirPath, mapping.getWith().getOpenehr(), referencedMapping);

        // recursively call createHelpers after reference mappings have been prepared
        createHelpers(mainArtifact, fhirConnectMapper, templateId, mainOpenEhrPath, referencedMapping, parentCondition, helpers, coverHelpers, bundle, multiple);
    }

    /**
     * InnerCreatedResources are those that are not directly being mapped by a model mapper but indirectly because
     * the main Resource is pointing to them.
     * <p>
     * When fhirPath doesn't start with the main target Resource, limitingCriteria needs to be adjusted as well.
     * For example, when you're mapping Condition that's referenced by the main Resource (Patient), limiting criteria
     * is pointing to a patient but would have to be pointing to a Condition
     *
     * @param targetResource main Resource being mapped
     * @param helperToFix    helper where limiting criteria needs to be adjusted
     */
    private void fixLimitingCriteriaForInnerCreatedResources(final String targetResource,
                                                             final FhirToOpenEhrHelper helperToFix) {
        final String fhirPath = helperToFix.getFhirPath();
        if (StringUtils.isEmpty(fhirPath)) {
            return;
        }
        if (!fhirPath.startsWith(targetResource) && Character.isUpperCase(fhirPath.charAt(0))) {
            helperToFix.setLimitingCriteria(helperToFix.getLimitingCriteria().replace(targetResource, fhirPath.split("\\.")[0]));
        }
    }

    /**
     * Creates base FhirToOpenEhrHelper POJO that's later on adjusted in the further processing of these helpers. This method
     * only instantiates it and adds generic things to it, such as 'archetype' and 'limitingCriteria' that's constructed
     * based on the header condition of a model mapper.
     *
     * @param mainArtifact      main artifact being mapped
     * @param mapperForResource fhir connect model mapper
     * @param bundle            if we're mapping to a Bundle (if FhirConnect.context.resourceType is Bundle)
     * @return instantiated FhirToOpenEhrHelper
     */
    private FhirToOpenEhrHelper createHelper(final String mainArtifact, final FhirConnectMapper mapperForResource, final boolean bundle) {
        final FhirConfig fhirConfig = mapperForResource.getFhirConfig();

        return FhirToOpenEhrHelper.builder()
                .archetype(mainArtifact)
                .limitingCriteria(getLimitingCriteria(fhirConfig, bundle))
                .build();
    }

    /**
     * Creates limiting criteria based on the FhirConnect FhirConfig Condition element.
     */
    private String getLimitingCriteria(final FhirConfig fhirConfig, final boolean bundle) {
        if (fhirConfig == null) {
            return null;
        }
        final String limitingCriteria;
        if (fhirConfig.getCondition() != null) {
            final String existingFhirPath = stringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC, fhirConfig.getCondition(), fhirConfig.getResource());
            if (bundle && existingFhirPath.startsWith(fhirConfig.getResource())) {
                final String withoutResourceType = existingFhirPath.replace(fhirConfig.getResource() + ".", "");
                limitingCriteria = String.format("Bundle.entry.resource.ofType(%s).where(%s)", fhirConfig.getResource(), withoutResourceType);
            } else {
                limitingCriteria = stringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC, fhirConfig.getCondition(), fhirConfig.getResource());
            }
        } else {
            limitingCriteria = String.format("Bundle.entry.resource.ofType(%s)", fhirConfig.getResource());
        }
        return limitingCriteria;
    }
}
