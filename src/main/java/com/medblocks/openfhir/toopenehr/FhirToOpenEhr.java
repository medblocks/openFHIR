package com.medblocks.openfhir.toopenehr;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datatypes.CodePhrase;
import com.nedap.archie.rm.generic.PartySelf;
import com.nedap.archie.rm.support.identification.TerminologyId;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.model.Condition;
import com.medblocks.openfhir.fc.model.*;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.*;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RESOLVE;

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

    @Autowired
    public FhirToOpenEhr(final FhirPathR4 fhirPathR4,
                         final OpenFhirStringUtils stringUtils,
                         final FlatJsonUnmarshaller flatJsonUnmarshaller,
                         final Gson gson,
                         final OpenEhrRmWorker openEhrRmWorker,
                         final OpenFhirStringUtils openFhirStringUtils,
                         final OpenFhirMappingContext openFhirTemplateRepo,
                         final OpenEhrCachedUtils openEhrApplicationScopedUtils,
                         OpenFhirMapperUtils openFhirMapperUtils) {
        this.fhirPathR4 = fhirPathR4;
        this.stringUtils = stringUtils;
        this.flatJsonUnmarshaller = flatJsonUnmarshaller;
        this.gson = gson;
        this.openEhrRmWorker = openEhrRmWorker;
        this.openFhirStringUtils = openFhirStringUtils;
        this.openFhirTemplateRepo = openFhirTemplateRepo;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.openFhirMapperUtils = openFhirMapperUtils;
    }

    public JsonObject fhirToFlatJsonObject(final FhirConnectContext context, final Resource resource, final OPERATIONALTEMPLATE operationaltemplate) {
        final boolean bundle = ResourceType.Bundle.name().equals(context.getFhir().getResourceType());
        final Bundle toRunEngineOn = prepareBundle(resource);

        final WebTemplate webTemplate = openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(context.getOpenEHR().getTemplateId());

        final List<FhirToOpenEhrHelper> flattened = new ArrayList<>();
        final List<FhirToOpenEhrHelper> coverHelpers = new ArrayList<>();
        createFlat(templateId, toRunEngineOn, null, flattened, coverHelpers, bundle);

        flattened.addAll(coverHelpers);

        openEhrRmWorker.fixFlatWithOccurrences(flattened, webTemplate);
        return resolveFhirPaths(flattened, toRunEngineOn, bundle);
    }

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

    public Composition fhirToCompositionRm(final FhirConnectContext context, final Resource resource, final OPERATIONALTEMPLATE operationaltemplate) {
        final WebTemplate webTemplate = openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
        final JsonObject flattenedWithValues = fhirToFlatJsonObject(context, resource, operationaltemplate);
        final Composition composition = flatJsonUnmarshaller.unmarshal(gson.toJson(flattenedWithValues), webTemplate);

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

        return composition;
    }

    private JsonObject resolveFhirPaths(final List<FhirToOpenEhrHelper> flattenedHelpers, final Resource resource, final boolean bundle) {
        final JsonObject finalFlat = new JsonObject();

        final Map<String, List<FhirToOpenEhrHelper>> byMainArtifact = mapperByMainArtifact(flattenedHelpers);
        for (Map.Entry<String, List<FhirToOpenEhrHelper>> artifactMapper : byMainArtifact.entrySet()) {
            final List<FhirToOpenEhrHelper> flattened = artifactMapper.getValue();

            flattened.stream().map(FhirToOpenEhrHelper::getLimitingCriteria).distinct().forEach(lim -> {
                String mainMultiple = null;
                if (resource instanceof Bundle) {
                    // apply limiting factor
                    final List<Base> relevantResources = fhirPathR4.evaluate(resource, lim, Base.class);

                    if(relevantResources.isEmpty()) {
                        log.warn("No relevant resources found for {}", lim);
                    } else {
                        log.info("Evaluation of {} returned {} entries that will be used for mapping.", lim, relevantResources.size());
                    }

                    int i = 0;
                    for (final Base relevantResource : relevantResources) {
                        boolean somethingWasAdded = false;
                        for (FhirToOpenEhrHelper fhirToOpenEhrHelper : flattened) {
                            final FhirToOpenEhrHelper cloned = fhirToOpenEhrHelper.clone();
                            if (fhirToOpenEhrHelper.getMultiple() && (mainMultiple == null || fhirToOpenEhrHelper.getOpenEhrPath().startsWith(mainMultiple))) {
                                mainMultiple = fhirToOpenEhrHelper.getOpenEhrPath().split("\\[n\\]")[0];
                                cloned.setOpenEhrPath(fhirToOpenEhrHelper.getOpenEhrPath().replaceFirst("\\[n\\]", ":" + i));

                                fixAllChildrenRecurringElements(cloned, fhirToOpenEhrHelper.getOpenEhrPath(), cloned.getOpenEhrPath());
                            }
                            int previousFinalFlatSize = finalFlat.size();
                            flatten(cloned, finalFlat, relevantResource);

                            somethingWasAdded = somethingWasAdded || previousFinalFlatSize < finalFlat.size();
                        }
                        if (somethingWasAdded) {
                            i++;
                        } else {
                            log.warn("Even though a Resource matched criteria, nothing was added to the openEHR composition from it: {}", relevantResource.getIdBase());
                        }
                    }

                } else {
                    for (FhirToOpenEhrHelper fhirToOpenEhrHelper : flattened) {
                        final List<Base> result = fhirPathR4.evaluate(resource, fhirToOpenEhrHelper.getFhirPath(), Base.class);

                        handleOccurrenceResults(fhirToOpenEhrHelper.getOpenEhrPath(), fhirToOpenEhrHelper.getOpenEhrType(), result, finalFlat);
                    }

                }
            });

        }


        return finalFlat;
    }


    private void getResultsByFhirPath(final String fhirPath, final Base relevantResource, Map<String, List<Base>> extracted) {
        if (!fhirPath.contains(".")) {
            // found all already
            return;
        }
        final List<Base> result = fhirPathR4.evaluate(relevantResource, fhirPath, Base.class);
        if (result == null || result.isEmpty()) {
            return;
        }
        extracted.put(fhirPath, result);

        getResultsByFhirPath(fhirPath.substring(0, fhirPath.lastIndexOf(".")), relevantResource, extracted);
    }

    private Map<String, List<FhirToOpenEhrHelper>> mapperByMainArtifact(final List<FhirToOpenEhrHelper> flattened) {
        return flattened.stream().collect(Collectors.groupingBy(FhirToOpenEhrHelper::getArchetype));
    }

    JsonObject handleOccurrenceResults(final String openEhrPath, final String openEhrType, final List<Base> fhirPathResults, final JsonObject finalFlat) {
        if (fhirPathResults == null || fhirPathResults.isEmpty()) {
            return finalFlat;
        }
        final boolean noMoreRecurringOptions = !openEhrPath.contains("[n]");
        final String openEhrWithAllReplacedToZeroth = openEhrPath.replaceAll("\\[n\\]", ":0");
        if (fhirPathResults.size() == 1) {
            // it's a single find, so replace all those multiple-occurrences with zeroth index
            setFhirPathValue(openEhrWithAllReplacedToZeroth, fhirPathResults.get(0), openEhrType, finalFlat);
        } else {
            // many results, set all but the last one to :0.. the last one index according to amount of results in fhirPathResults
            if (noMoreRecurringOptions) {
                log.warn("Found more than one result, yet there's no more recurring options! Only adding the first result to openEhr flat.");
                setFhirPathValue(openEhrWithAllReplacedToZeroth, fhirPathResults.get(0), openEhrType, finalFlat);
            } else {
                for (int i = 0; i < fhirPathResults.size(); i++) {
                    final Base fhirPathResult = fhirPathResults.get(i);
                    final String finalOpenEhrPath = openFhirStringUtils.replaceLastIndexOf(openEhrWithAllReplacedToZeroth, ":0", ":" + i);

                    setFhirPathValue(finalOpenEhrPath, fhirPathResult, openEhrType, finalFlat);
                }
            }
        }
        return null;
    }


    void flatten(final FhirToOpenEhrHelper helper, final JsonObject flatten, final Base toResolveOn) {
        final boolean resolving = !helper.getOpenEhrType().equals(OPENEHR_TYPE_NONE);
        List<Base> results;
        if (StringUtils.isEmpty(helper.getFhirPath()) || FHIR_ROOT_FC.equals(helper.getFhirPath())) {
            // just take the one roResolveOn
            log.debug("Taking Base itself as fhirPath is {}", helper.getFhirPath());
            results = Arrays.asList(toResolveOn);
        } else {
            results = fhirPathR4.evaluate(toResolveOn, openFhirStringUtils.fixFhirPathCasting(helper.getFhirPath()), Base.class);
            if (helper.getFhirPath().endsWith(RESOLVE) && results.isEmpty()) {
                final List<Base> reference = fhirPathR4.evaluate(toResolveOn, helper.getFhirPath().replace("."+RESOLVE, ""), Base.class);
                if (!reference.isEmpty()) {
                    results = reference.stream()
                            .filter(ref -> ref instanceof Reference)
                            .map(ref -> (Base) ((Reference) ref).getResource())
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }
            }
        }
        if(results == null || results.isEmpty()) {
            log.warn("No results found for FHIRPath {}, evaluating on type: {}", helper.getFhirPath(), toResolveOn.getClass());
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            Base result = results.get(i);
            final boolean noMoreRecurringOptions = !helper.getOpenEhrPath().contains("[n]");
            final String thePath = noMoreRecurringOptions ? helper.getOpenEhrPath() : openFhirStringUtils.replaceLastIndexOf(helper.getOpenEhrPath(), "[n]", ":" + i);
            log.debug("Setting value taken with fhirPath {} from object type {}", helper.getFhirPath(), toResolveOn.getClass());
            setFhirPathValue(thePath, result, helper.getOpenEhrType(), flatten);

            if (helper.getFhirToOpenEhrHelpers() != null) {
                for (FhirToOpenEhrHelper fhirToOpenEhrHelper : helper.getFhirToOpenEhrHelpers()) {
                    FhirToOpenEhrHelper copy = fhirToOpenEhrHelper.clone();

                    if (copy.getOpenEhrPath().startsWith(helper.getOpenEhrPath())) {
                        final String newOne = copy.getOpenEhrPath().replace(helper.getOpenEhrPath(), thePath);

                        fixAllChildrenRecurringElements(copy, helper.getOpenEhrPath(), newOne);

                        flatten(copy, flatten, result);
                    } else {
                        flatten(copy, flatten, result);
                    }
                }
            }

            if (noMoreRecurringOptions) {
                break;
            }

        }
    }

    void fixAllChildrenRecurringElements(FhirToOpenEhrHelper copy, final String parent, final String newOne) {
        if (stringUtils.childStartsWithParent(copy.getOpenEhrPath(), parent)) {
            final String replaced = replacePattern(copy.getOpenEhrPath(), newOne);
            copy.setOpenEhrPath(replaced);
        }
        if (copy.getFhirToOpenEhrHelpers() == null) {
            return;
        }
        for (FhirToOpenEhrHelper fhirToOpenEhrHelper : copy.getFhirToOpenEhrHelpers()) {
            fixAllChildrenRecurringElements(fhirToOpenEhrHelper, parent, newOne);
        }
    }

    String replacePattern(String original, String replacement) {
        // Split the original and replacement strings into parts based on "/"
        String[] originalParts = original.split("/");
        String[] replacementParts = replacement.split("/");

        StringBuilder result = new StringBuilder();

        // Iterate through the parts and replace the parts from the original with the replacement, when needed
        for (int i = 0; i < originalParts.length; i++) {
            if (i < replacementParts.length && replacementParts[i].matches(".*:\\d+")) {
                // If replacement part has a numeric suffix, use it
                result.append(replacementParts[i]);
            } else if (i < replacementParts.length && replacementParts[i].matches(".*\\[\\d*\\]")) {
                // If the replacement part has a [n] suffix, use the original structure
                result.append(originalParts[i]);
            } else if (i < replacementParts.length) {
                // Use the original part
                final String orig = originalParts[i].contains("[n]") ? openFhirStringUtils.replaceLastIndexOf(originalParts[i], "[n]", "") : originalParts[i];
                final String repl = replacementParts[i].contains(":") ? replacementParts[i].replace(":", "").replace(String.valueOf(openFhirStringUtils.getLastIndex(replacementParts[i])), "") : replacementParts[i];
                if (!orig.startsWith(repl)) { // means it's a completely different one, need to take original
                    result.append(originalParts[i]);
                } else {
                    result.append(replacementParts[i]);
                }
            } else {
                // If no matching replacement, use the original part
                result.append(originalParts[i]);
            }

            // Add the separator
            if (i < originalParts.length - 1) {
                result.append("/");
            }
        }
        return result.toString();
    }

    void setFhirPathValue(final String openEhrPath, final Base extractedValue, final String openEhrType, final JsonObject constructingFlat) {
        if (openEhrType == null) {
            addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);
            return;
        }
        if (OPENEHR_TYPE_NONE.equals(openEhrType)) {
            log.warn("Adding nothing on path {} as type is marked as NONE", openEhrPath);
            return;
        }
        if(extractedValue == null) {
            log.warn("Extracted value is null");
            return;
        }
        switch (openEhrType) {
            case FhirConnectConst.DV_MULTIMEDIA:
                if (extractedValue instanceof Attachment) {
                    final Attachment extractedAtt = (Attachment) extractedValue;
                    int size = extractedAtt.getSize();
                    if (size == 0 && extractedAtt.getData() != null) {
                        size = extractedAtt.getData().length;
                    }
                    addToConstructingFlat(openEhrPath + "|size", String.valueOf(size), constructingFlat);
                    addToConstructingFlat(openEhrPath + "|mediatype", extractedAtt.getContentType(), constructingFlat);
                    if (StringUtils.isNotEmpty(extractedAtt.getUrl())) {
                        addToConstructingFlat(openEhrPath + "|url", extractedAtt.getUrl(), constructingFlat); // todo? what if url?
                    } else {
                        addToConstructingFlat(openEhrPath + "|data", Base64.getEncoder().encodeToString(extractedAtt.getData()), constructingFlat); // todo? what if url?
                    }
                    return;
                } else {
                    log.warn("openEhrType is MULTIMEDIA but extracted value is not Attachment; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_QUANTITY:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlatDouble(openEhrPath + "|magnitude", extractedQuantity.getValue().doubleValue(), constructingFlat);
                    }
                    addToConstructingFlat(openEhrPath + "|unit", extractedQuantity.getUnit(), constructingFlat);
                    return;
                } else if (extractedValue instanceof Ratio) {
                    final Ratio extractedRatio = (Ratio) extractedValue;
                    setFhirPathValue(openEhrPath, extractedRatio.getNumerator(), openEhrType, constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_QUANTITY but extracted value is not Quantity and not Ratio; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_ORDINAL:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlat(openEhrPath + "|ordinal", extractedQuantity.getValue().toPlainString(), constructingFlat);
                    }
                    addToConstructingFlat(openEhrPath + "|value", extractedQuantity.getUnit(), constructingFlat);
                    addToConstructingFlat(openEhrPath + "|code", extractedQuantity.getCode(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_ORDINAL but extracted value is not Quantity; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_PROPORTION:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if ("%".equals(extractedQuantity.getCode())) {
                        // then denominator is 100 and value is as it is
                        addToConstructingFlatDouble(openEhrPath + "|denominator", 100.0, constructingFlat);
                    }
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlatDouble(openEhrPath + "|numerator", extractedQuantity.getValue().doubleValue(), constructingFlat);
                    }
                    // todo: denominator? and type? hardcoded?
                    addToConstructingFlat(openEhrPath + "|type", "2", constructingFlat); // hardcoded???

                    return;
                } else {
                    log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_COUNT:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlatInteger(openEhrPath, extractedQuantity.getValue().intValueExact(), constructingFlat);
                    }
                    return;
                } else if (extractedValue instanceof IntegerType) {
                    final IntegerType extractedInteger = (IntegerType) extractedValue;
                    if (extractedInteger.getValue() != null) {
                        addToConstructingFlatInteger(openEhrPath, extractedInteger.getValue(), constructingFlat);
                    }
                    return;
                } else {
                    log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity and not IntegerType; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_DATE_TIME:
                if (extractedValue instanceof DateTimeType) {
                    log.info("openEhrType is DATE_TIME and extracted value is DateTimeType");
                    final DateTimeType extractedDateTime = (DateTimeType) extractedValue;
                    if (extractedDateTime.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateTimeToString(extractedDateTime.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof DateType) {
                    log.info("openEhrType is DATE_TIME and extracted value is DateType");
                    final DateType extractedDate = (DateType) extractedValue;
                    if (extractedDate.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateToString(extractedDate.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof TimeType) {
                    log.info("openEhrType is DATE_TIME and extracted value is TimeType");
                    final TimeType extractedTime = (TimeType) extractedValue;
                    if (extractedTime.getValue() != null) {
                        addToConstructingFlat(openEhrPath, extractedTime.getValue(), constructingFlat);
                    }
                    return;
                }
            case FhirConnectConst.DV_DATE:
                if (extractedValue instanceof DateTimeType) {
                    log.info("openEhrType is DV_DATE and extracted value is DateTimeType");
                    final DateTimeType extractedDateTime = (DateTimeType) extractedValue;
                    if (extractedDateTime.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateToString(extractedDateTime.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof DateType) {
                    log.info("openEhrType is DV_DATE and extracted value is DateType");
                    final DateType extractedDate = (DateType) extractedValue;
                    if (extractedDate.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateToString(extractedDate.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
            case FhirConnectConst.DV_TIME:
                if (extractedValue instanceof DateTimeType) {
                    log.info("openEhrType is DV_TIME and extracted value is DateTimeType");
                    final DateTimeType extractedDateTime = (DateTimeType) extractedValue;
                    if (extractedDateTime.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.timeToString(extractedDateTime.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof DateType) {
                    log.info("openEhrType is DV_TIME and extracted value is DateType");
                    final DateType extractedDate = (DateType) extractedValue;
                    if (extractedDate.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.timeToString(extractedDate.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof TimeType) {
                    log.info("openEhrType is DV_TIME and extracted value is TimeType");
                    final TimeType extractedTime = (TimeType) extractedValue;
                    if (extractedTime.getValue() != null) {
                        addToConstructingFlat(openEhrPath, extractedTime.getValue(), constructingFlat);
                    }
                    return;
                }
            case FhirConnectConst.DV_CODED_TEXT:
                if (extractedValue instanceof CodeableConcept) {
                    final CodeableConcept extractedCodebleConcept = (CodeableConcept) extractedValue;
                    final List<Coding> codings = extractedCodebleConcept.getCoding();
                    if (!codings.isEmpty()) {
                        final Coding coding = codings.get(0);
                        addToConstructingFlat(openEhrPath + "|code", coding.getCode(), constructingFlat);
                        addToConstructingFlat(openEhrPath + "|terminology", coding.getSystem(), constructingFlat);
                    }
                    addToConstructingFlat(openEhrPath + "|value", extractedCodebleConcept.getText(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_CODED_TEXT but extracted value is not CodeableConcept; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.IDENTIFIER:
                if (extractedValue instanceof Identifier) {
                    final Identifier extractedIdentifier = (Identifier) extractedValue;
                    addToConstructingFlat(openEhrPath + "|id", extractedIdentifier.getValue(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is IDENTIFIER but extracted value is not Identifier; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.CODE_PHRASE:

                if (extractedValue instanceof Coding) {
                    final Coding extractedCoding = (Coding) extractedValue;
                    addToConstructingFlat(openEhrPath + "|code", extractedCoding.getCode(), constructingFlat);
                    addToConstructingFlat(openEhrPath + "|terminology", extractedCoding.getSystem(), constructingFlat);
                    return;
                } else if (extractedValue instanceof Extension extractedExtension) {
                    setFhirPathValue(openEhrPath, extractedExtension.getValue(), openEhrType, constructingFlat);
                    return;
                } else if (extractedValue instanceof CodeableConcept extractedCodeable) {
                    setFhirPathValue(openEhrPath, extractedCodeable.getCodingFirstRep(), openEhrType, constructingFlat);
                    return;
                } else if (extractedValue instanceof Enumeration extractedEnum) {
                    addToConstructingFlat(openEhrPath + "|code", extractedEnum.getValueAsString(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is CODE_PHRASE but extracted value is not Coding, not CodeableConcept and not Enumeration; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_TEXT:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);
                return;
            case FhirConnectConst.DV_BOOL:
                final Base extractedValue1 = extractedValue;
                if (extractedValue1 instanceof StringType && ((StringType) extractedValue1).getValue() != null) {
                    addToConstructingBoolean(openEhrPath, Boolean.valueOf(((StringType) extractedValue1).getValue()), constructingFlat);
                    return;
                } else if (extractedValue1 instanceof BooleanType && ((BooleanType) extractedValue1).getValue() != null) {
                    addToConstructingBoolean(openEhrPath, ((BooleanType) extractedValue1).getValue(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_BOOL but extracted value is not StringType and not BooleanType; is {}", extractedValue1.getClass());
                }
                return;
            default:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);

        }
    }

    private void addValuePerFhirType(final Base fhirValue, final String openEhrPath, final JsonObject constructingFlat) {
        if (fhirValue instanceof Quantity) {
            final Quantity extractedQuantity = (Quantity) fhirValue;
            if (extractedQuantity.getValue() != null) {
                addToConstructingFlat(openEhrPath, extractedQuantity.getValue().toPlainString(), constructingFlat);
            }
        } else if (fhirValue instanceof Coding) {
            final Coding extractedQuantity = (Coding) fhirValue;
            addToConstructingFlat(openEhrPath, extractedQuantity.getCode(), constructingFlat);
        } else if (fhirValue instanceof DateTimeType) {
            final DateTimeType extractedQuantity = (DateTimeType) fhirValue;
            addToConstructingFlat(openEhrPath, extractedQuantity.getValueAsString(), constructingFlat);
        } else if (fhirValue instanceof Annotation) {
            final Annotation extracted = (Annotation) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof Address) {
            final Address extracted = (Address) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof HumanName) {
            final HumanName extracted = (HumanName) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getNameAsSingleString(), constructingFlat);
        } else if (fhirValue instanceof Extension) {
            final Extension extracted = (Extension) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getValue().hasPrimitiveValue() ? extracted.getValue().primitiveValue() : null, constructingFlat);
        } else if (fhirValue.hasPrimitiveValue()) {
            addToConstructingFlat(openEhrPath, fhirValue.primitiveValue(), constructingFlat);
        } else {
            log.error("Unsupported fhir value toString!: {}", fhirValue.toString());
        }
    }

    final void addToConstructingFlat(final String key, final String value, final JsonObject constructingFlat) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        log.debug("Setting value {} on path {}", value, key);
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingBoolean(final String key, final Boolean value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.addProperty(key, (Boolean) value);
    }

    final void addToConstructingFlatDouble(final String key, final Double value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingFlatInteger(final String key, final Integer value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    void createFlat(final String templateId, final Resource resource, final Condition parentCondition, final List<FhirToOpenEhrHelper> helpers, final List<FhirToOpenEhrHelper> coverHelpers, final boolean bundle) {
        ((Bundle) resource).getEntry().forEach(entry -> {
            final List<FhirConnectMapper> mapperForResources = openFhirTemplateRepo.getMapperForResource(entry.getResource());
            if (mapperForResources == null || mapperForResources.isEmpty()) {
                return;
            }
            for (FhirConnectMapper mapperForResource : mapperForResources) {
                final String mainArchetype = mapperForResource.getOpenEhrConfig().getArchetype();
                createFlat(mainArchetype, mapperForResource, templateId, templateId, mapperForResource.getMappings(), parentCondition, helpers, coverHelpers, bundle, mapperForResource.getFhirConfig().getMultiple());
            }
        });
    }

    private FhirToOpenEhrHelper createHelper(final String mainArtifact, final FhirConnectMapper mapperForResource, final boolean bundle) {
        final FhirConfig fhirConfig = mapperForResource.getFhirConfig();
        final String limitingCriteria;
        if (fhirConfig != null && fhirConfig.getCondition() != null) {
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

        return FhirToOpenEhrHelper.builder()
                .archetype(mainArtifact)
                .limitingCriteria(limitingCriteria)
                .build();
    }

    List<FhirToOpenEhrHelper> createFlat(final String mainArtifact,
                                         final FhirConnectMapper modelMappingTemplate,
                                         final String templateId,
                                         final String openEhrPath,
                                         final List<Mapping> mappings,
                                         final Condition parentCondition,
                                         final List<FhirToOpenEhrHelper> helpers,
                                         final List<FhirToOpenEhrHelper> coverHelpers,
                                         final boolean bundle,
                                         final boolean multiple) {
        for (Mapping mapping : mappings) {
            final FhirToOpenEhrHelper initialHelper = createHelper(mainArtifact, modelMappingTemplate, bundle);
            if (mapping.getWith().getOpenehr() != null && mapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_CONTEXT_FC)) {
                continue;
            }
            final Condition condition = parentCondition != null ? parentCondition : mapping.getCondition();
            final String fhirPath = openFhirStringUtils.amendFhirPathForToOpenEhr(mapping.getWith().getFhir(),
                    Collections.singletonList(condition),
                    modelMappingTemplate.getFhirConfig().getResource());

            // because it references Resources not directly tied to a Resource itself, i.e. Condition as a cause of death for a Patient
            final boolean needsToBeAddedToParentHelpers = StringUtils.isNotEmpty(fhirPath)
                    && Character.isUpperCase(fhirPath.charAt(0))
                    && !fhirPath.startsWith(modelMappingTemplate.getFhirConfig().getResource());


            if (mapping.getWith().getOpenehr().contains(FhirConnectConst.REFERENCE) && mapping.getReference() != null) {
                // a reference mapping
                // prepare 'reference' mappings
                final List<Mapping> referencedMapping = mapping.getReference().getMappings();
                openFhirMapperUtils.prepareReferencedMappings(fhirPath, mapping.getWith().getOpenehr(), referencedMapping);
                createFlat(mainArtifact, modelMappingTemplate, templateId, openEhrPath, referencedMapping, parentCondition, helpers, coverHelpers, bundle, multiple);
            } else {
                String openehr = stringUtils.prepareOpenEhrSyntax(mapping.getWith().getOpenehr(), openEhrPath);
                if (mapping.getWith().getType() == null) {
                    // means a string
                    if (openFhirStringUtils.endsWithOpenEhrType(openehr) != null) {
                        openehr = stringUtils.replaceLastIndexOf(openehr, "/", "|");
                    }
                } else {
                    initialHelper.setOpenEhrType(mapping.getWith().getType());
                }
                if (!OPENEHR_TYPE_NONE.equals(mapping.getWith().getType())
                        && !FhirConnectConst.OPENEHR_TYPE_DOSAGE.equals(mapping.getWith().getType())) {
                    initialHelper.setOpenEhrPath(openehr.replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + "/", openEhrPath + "/"));
                    initialHelper.setFhirPath(fhirPath.replace("." + FHIR_ROOT_FC, ""));
                    initialHelper.setMultiple(multiple);
                    fixLimitingCriteriaForInnerCreatedResources(modelMappingTemplate.getFhirConfig().getResource(), initialHelper);
                    if (needsToBeAddedToParentHelpers) {
                        coverHelpers.add(initialHelper);
                    } else {
                        helpers.add(initialHelper);
                    }
                }
                final List<FhirToOpenEhrHelper> innerHelpers = new ArrayList<>();
                if (mapping.getFollowedBy() != null) {
                    final List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();
                    for (Mapping followedByMapping : followedByMappings) {
                        if (!followedByMapping.getWith().getOpenehr().startsWith(FhirConnectConst.OPENEHR_ARCHETYPE_FC)) {
                            final String followedByOpenEhrPath = followedByMapping.getWith().getOpenehr();
                            final String delimeter = followedByOpenEhrPath.startsWith("|") ? "":"/";
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
                    initialHelper.setOpenEhrPath(openehr
                            .replace(FhirConnectConst.REFERENCE + "/", "")
                            .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + "/", openEhrPath + "/"));
                    initialHelper.setFhirPath(fhirPath.replace("." + FHIR_ROOT_FC, ""));
                    initialHelper.setMultiple(multiple);

                    initialHelper.setFhirToOpenEhrHelpers(innerHelpers);
                    fixLimitingCriteriaForInnerCreatedResources(modelMappingTemplate.getFhirConfig().getResource(), initialHelper);
                    createFlat(mainArtifact, modelMappingTemplate, templateId, openEhrPath, followedByMappings, null, innerHelpers, coverHelpers, bundle, multiple);

                    if (needsToBeAddedToParentHelpers) {
                        coverHelpers.add(initialHelper);
                    } else {
                        if (!helpers.contains(initialHelper)) {
                            helpers.add(initialHelper);
                        }
                    }
                }
                if (mapping.getSlotArchetype() != null) {
                    final List<FhirConnectMapper> slotArchetypeMapperss = openFhirTemplateRepo.getSlotMapperForArchetype(templateId, mapping.getSlotArchetype());

                    for (FhirConnectMapper slotArchetypeMappers : slotArchetypeMapperss) {
                        final String openEhrFixed = openehr.replace("/"+FhirConnectConst.REFERENCE, "");
                        openFhirMapperUtils.prepareForwardingSlotArchetypeMapperNoFhirPrefix(slotArchetypeMappers, modelMappingTemplate, fhirPath, openEhrFixed);

                        initialHelper.setFhirToOpenEhrHelpers(innerHelpers);
                        initialHelper.setOpenEhrPath(openehr
                                .replace(FhirConnectConst.REFERENCE + "/", "")
                                .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + "/", openEhrPath + "/"));
                        initialHelper.setFhirPath(fhirPath.replace("." + FHIR_ROOT_FC, ""));
                        fixLimitingCriteriaForInnerCreatedResources(modelMappingTemplate.getFhirConfig().getResource(), initialHelper);
                        createFlat(mainArtifact,
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
            }
        }
        return helpers;
    }

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
}
