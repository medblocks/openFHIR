package com.medblocks.openfhir.tofhir;

import com.medblocks.openfhir.util.FhirInstanceCreator;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.*;

@Component
public class IntermediateCacheProcessing {

    private final OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public IntermediateCacheProcessing(OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
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
     * @param parentFollowedByOpenEhr          parent's openehr path
     * @return FindingOuterMost that is constructed from found elements, where removedPath is the fhir path that was removed from the
     * cache key as part of the iteration of finding it, and lastObject is the found object that existed within the cache
     */
    public OpenEhrToFhir.FindingOuterMost findTheOuterMostThatExistsWithinCache(final Map<String, Object> instantiatedIntermediateElements,
                                                                                final Resource coverInstance,
                                                                                final String fhirPath,
                                                                                final String fullOpenEhrPath,
                                                                                final String removedPath,
                                                                                final boolean isFollowedBy,
                                                                                final String parentFollowedByOpenEhr) {
        final String keyForIntermediateElements = createKeyForIntermediateElements(coverInstance.toString(), fhirPath, fullOpenEhrPath);
        if (isFollowedBy && parentFollowedByOpenEhr != null) {
            // we need to ignore the openehr in the key because followed by means we need to find one that has already been created!
            final String preparedParentPath = openFhirStringUtils.prepareParentOpenEhrPath(parentFollowedByOpenEhr,
                    fullOpenEhrPath);
            final String keyToCheckFor = getKeyThatIgnoresOpenEhrPath(fullOpenEhrPath, preparedParentPath, fhirPath, coverInstance);

            final List<String> elementsMatching = extractMatchingElementsFromIntermediateCache(instantiatedIntermediateElements,
                    keyToCheckFor);

            elementsMatching.sort(Comparator.comparingInt(String::length));
            final String elementMatching = elementsMatching.isEmpty() ? null : elementsMatching.get(0);
            if (elementMatching != null) {
                return new OpenEhrToFhir.FindingOuterMost(instantiatedIntermediateElements.get(elementMatching), removedPath);
            }
        }
        if (instantiatedIntermediateElements.containsKey(keyForIntermediateElements)) {
            return new OpenEhrToFhir.FindingOuterMost(instantiatedIntermediateElements.get(keyForIntermediateElements), removedPath);
        }
        if (!fhirPath.contains(".")) {
            // we've reached the end, apparently there's nothing in the cache that would match this at all
            return new OpenEhrToFhir.FindingOuterMost(null, removedPath);
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
                parentFollowedByOpenEhr);
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
    public void populateIntermediateCache(final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn,
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
     * Finds keyToCheckFor in the intermediate cache and gets all elements from cache that match that key, with the following
     * business rule:
     * - if cache key starts with the key we are checking for - good
     * - if key we are checking for starts with a key from cache, this is also good, as long as the digit is not the only
     * difference
     *
     * @param instantiatedIntermediateElements intermediate cache
     * @param keyToCheckFor                    key we are looking for
     * @return all elements that match that key, where matching logic is defined and not a direct equals
     */
    private List<String> extractMatchingElementsFromIntermediateCache(final Map<String, Object> instantiatedIntermediateElements,
                                                                      final String keyToCheckFor) {
        return instantiatedIntermediateElements.keySet().stream()
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
    }

    private String getKeyThatIgnoresOpenEhrPath(final String fullOpenEhrPath, final String preparedParentPath,
                                                final String fhirPath, final Resource coverInstance) {
        if (fullOpenEhrPath.contains(preparedParentPath)) {
            // means that child openehr path is a sub-path of the followed by parent
            return createKeyForIntermediateElements(coverInstance.toString(), fhirPath, preparedParentPath);
        } else {
            return createKeyForIntermediateElements(coverInstance.toString(), fhirPath, "");
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
        return new StringJoiner("_").add(objectRef).add(fixedFhirPath).add(fullOpenEhrPath.replace(RECURRING_SYNTAX, "")).toString();
    }
}
