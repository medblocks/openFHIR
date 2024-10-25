package com.medblocks.openfhir.util;

import ca.uhn.fhir.model.api.annotation.Child;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;
import static com.medblocks.openfhir.fc.FhirConnectConst.THIS;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RESOLVE;

/**
 * Creates and instantiates HAPI FHIR Resources based on FHIR Path expressions
 */
@Slf4j
@Component
public class FhirInstanceCreator {

    private final String R4_HAPI_PACKAGE = "org.hl7.fhir.r4.model.";

    private final OpenFhirStringUtils openFhirStringUtils;
    private final FhirInstanceCreatorUtility fhirInstanceCreatorUtility;

    @Autowired
    public FhirInstanceCreator(OpenFhirStringUtils openFhirStringUtils, FhirInstanceCreatorUtility fhirInstanceCreatorUtility) {
        this.openFhirStringUtils = openFhirStringUtils;
        this.fhirInstanceCreatorUtility = fhirInstanceCreatorUtility;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstantiateAndSetReturn {
        Object returning;
        boolean isList;
        InstantiateAndSetReturn inner;
        String path;
    }

    /**
     * After reaching the last segment of the path, instantiation is ended with the InstantiateAndSetReturn object
     * holding all information of instantiated elements with corresponding paths
     */
    private InstantiateAndSetReturn endInstantiation(final boolean resolveFollows,
                                                     final String resolveResourceType,
                                                     final String forcingClass,
                                                     final boolean specialThisHandling,
                                                     final Class clazz,
                                                     final Field theField,
                                                     final Object resource,
                                                     final Object originalResource,
                                                     final String splitPath,
                                                     final String followingWhereCondition) {
        // means we've reached the end
        final String forcingClassToUse = resolveFollows ? resolveResourceType : forcingClass;
        Class aClass = specialThisHandling ? clazz : fhirInstanceCreatorUtility.findClass(theField, forcingClassToUse);
        if (aClass == null) {
            // fallback if we cant find the one we want...
            aClass = fhirInstanceCreatorUtility.findClass(theField, null);
        }
        final Object generatedInstance = fhirInstanceCreatorUtility.newInstance(aClass);

        Object setObj = null;
        if (!specialThisHandling) {
            setObj = fhirInstanceCreatorUtility.handleSpecialThisKeyword(generatedInstance, resolveFollows, theField, resource);
        } else if (originalResource instanceof List) {
            ((List<Object>) originalResource).add(generatedInstance);
        }
        final String path = splitPath + (StringUtils.isBlank(followingWhereCondition) ? "" : ("." + followingWhereCondition));
        return InstantiateAndSetReturn.builder()
                .returning(specialThisHandling ? originalResource : setObj)
                .path(path.replace(THIS, ""))
                .isList(specialThisHandling ? originalResource instanceof List : theField.getType() == List.class)
                .build();
    }

    private InstantiateAndSetReturn handleInstantiateAndSetResolve(final String resolveResourceType,
                                                                   final String fhirPath,
                                                                   final String forcingClass,
                                                                   final Object resource) {
        final Class nextClass = fhirInstanceCreatorUtility.getFhirResourceType(resolveResourceType);
        final Object nextClassInstance = fhirInstanceCreatorUtility.newInstance(nextClass);

        final List<String> list = Arrays.asList(fhirPath.split("\\."));
        final InstantiateAndSetReturn returning = instantiateAndSetElement(nextClassInstance, nextClass,
                String.join(".", list.subList(1, list.size())),
                forcingClass,
                resolveResourceType);
        ((Reference) resource).setResource((IBaseResource) nextClassInstance);
        return InstantiateAndSetReturn.builder().returning(nextClassInstance).path(RESOLVE).inner(returning).isList(false).build();
    }

    /**
     * Instantiates and element and sets it on a parent Resource based on the given fhirPath
     *
     * @param resource     could be a FHIR Resource or any FHIR Base element that you're using as a base for the fhirPath
     * @param clazz        class of the element that is to be instantiated
     * @param fhirPath     fhir path for the instantiation
     * @param forcingClass when fhirPath contains a resolve() and FHIR defines multiple resources that can be referenced,
     *                     forcingClass tells us which Resource we really want to resolve()
     * @return all elements that were instantiated throughout the fhirPath evaluation together with corresponding fhirPaths
     */
    public InstantiateAndSetReturn instantiateAndSetElement(final Object resource, final Class clazz, final String fhirPath, final String forcingClass) {
        return instantiateAndSetElement(resource, clazz, fhirPath, forcingClass, null);
    }

    public InstantiateAndSetReturn instantiateAndSetElement(Object resource, Class clazz, String fhirPath, final String forcingClass, final String resolveResourceType) {
        final Object originalResource = resource;
        if (resource instanceof List) {
            resource = ((List<?>) resource).get(((List<?>) resource).size() - 1);
            clazz = resource.getClass();
        }

        if (StringUtils.isBlank(fhirPath)) {
            return InstantiateAndSetReturn.builder()
                    .returning(resource)
                    .path("")
                    .isList(resource instanceof List)
                    .build();
        }

        if (resource instanceof Reference) {
            resource = ((Reference) resource).getResource() == null ? resource : ((Reference) resource).getResource();
            clazz = resource.getClass();

            if (fhirPath.startsWith(RESOLVE)) {
                return handleInstantiateAndSetResolve(resolveResourceType, fhirPath, forcingClass, resource);
            }

            fhirPath = fhirPath
                    .replace(RESOLVE + "." + FHIR_ROOT_FC + ".", "")
                    .replace(RESOLVE + "." + FHIR_ROOT_FC, "");
        }

        if (fhirPath.startsWith(".")) {
            fhirPath = fhirPath.substring(1);
        }

        String followingWhereCondition = fhirInstanceCreatorUtility.getWhereForInstantiation(fhirPath, clazz);

        if (followingWhereCondition != null) {
            fhirPath = fhirPath
                    .replace("." + followingWhereCondition, "")
                    .replace(followingWhereCondition, "");
        }

        final String preparedFhirPath = fhirInstanceCreatorUtility.prepareFhirPathForInstantiation(clazz, fhirPath);
        final String[] splitFhirPaths = preparedFhirPath.split("\\.");
        for (int i = 0; i < splitFhirPaths.length; i++) {
            String splitPath = splitFhirPaths[i].equals("class") ? "class_" : splitFhirPaths[i];

            final Field[] childElements = FieldUtils.getFieldsWithAnnotation(clazz, Child.class);
            final Field theField = Arrays.stream(childElements).filter(child -> splitPath.equals(child.getName())).findFirst().orElse(null);
            boolean specialThisHandling = THIS.equals(splitPath); // means we really just one this same element, nothing else
            if (!specialThisHandling && theField == null) {
                continue;
            }
            final int arrayLength = splitFhirPaths.length - 1;
            final boolean resolveFollows = i != arrayLength && RESOLVE.equals(splitFhirPaths[i + 1]);
            final boolean castFollows = i != arrayLength && splitFhirPaths[i + 1].startsWith("as(");

            // second part of the condition is there to solve cases when the path ends with a cast, i.e. asNeeded.as(Boolean)
            if (preparedFhirPath.equals(splitPath)) {
                // means we've reached the end
                return endInstantiation(resolveFollows, resolveResourceType, forcingClass, specialThisHandling, clazz,
                        theField, resource, originalResource, splitPath, followingWhereCondition);
            }
            final List<String> list = fhirInstanceCreatorUtility.listFromSplitPath(splitFhirPaths, resolveFollows, castFollows);

            final String castingTo = castFollows ? openFhirStringUtils.getCastType(preparedFhirPath) : null;
            final Class nextClass = castFollows ? fhirInstanceCreatorUtility.getClassForName(R4_HAPI_PACKAGE + castingTo) : fhirInstanceCreatorUtility.findClass(theField, resolveFollows ? resolveResourceType : null);
            final Object nextClassInstance = fhirInstanceCreatorUtility.newInstance(nextClass);


            final InstantiateAndSetReturn returning = instantiateAndSetElement(nextClassInstance, nextClass,
                    String.join(".", list.subList(1, list.size())),
                    forcingClass,
                    resolveResourceType);
            final Object obj = fhirInstanceCreatorUtility.setFieldObject(theField, resource, nextClassInstance);
            final String path = splitPath + (castFollows ? ("." + splitFhirPaths[i + 1]) : "") + (StringUtils.isBlank(followingWhereCondition) ? "" : ("." + followingWhereCondition));
            return InstantiateAndSetReturn.builder()
                    .returning(obj)
                    .path(path)
                    .inner(returning)
                    .isList(theField != null && theField.getType() == List.class)
                    .build();
        }
        return null;
    }
}
