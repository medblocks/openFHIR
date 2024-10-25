package com.medblocks.openfhir.util;

import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.annotation.Child;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;
import static com.medblocks.openfhir.fc.FhirConnectConst.THIS;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.*;

/**
 * Creates and instantiates HAPI FHIR Resources based on FHIR Path expressions
 */
@Slf4j
@Component
public class FhirInstanceCreator {

    private final String R4_HAPI_PACKAGE = "org.hl7.fhir.r4.model.";

    private final OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public FhirInstanceCreator(OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
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
     * Creates a new Resource of the resourceType
     *
     * @param resourceType type of a FHIR Resource. If this is not a valid Resource type, exception will be logged
     *                     and null will be returned
     * @return instantiated FHIR Resource
     */
    public Resource create(final String resourceType) {
        try {
            return (Resource) getFhirResourceType(resourceType).getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            log.error("Couldn't create a new instance of {}", resourceType, e);
            return null;
        }
    }

    private String getWhereForInstantiation(final String fhirPath,
                                            final Class clazz) {
        final String[] splitByDot = fhirPath.split("\\.");
        if (splitByDot.length > 1) {
            boolean shouldHandleWhere;
            if (splitByDot[0].equals(clazz.getSimpleName()) && splitByDot.length > 2) {
                shouldHandleWhere = splitByDot[1].startsWith(WHERE) || splitByDot[2].startsWith(WHERE);
            } else {
                shouldHandleWhere = splitByDot[1].startsWith(WHERE);
            }

            if (fhirPath.startsWith(WHERE)) {
                shouldHandleWhere = true;
            }

            if (shouldHandleWhere) {
                return openFhirStringUtils.extractWhereCondition(fhirPath);
            }
        }
        return null;
    }

    /**
     * Creates a list from split paths, removing resolve elements and cast elements if what follows
     * is resolving or casting
     *
     * @param splitFhirPaths all split paths up unitl now
     * @param resolveFollows whether resolve follows next
     * @param castFollows    whether cast follows next
     * @return a List of all split elements without cast or resolve segments (as(), resolve())
     */
    private List<String> listFromSplitPath(final String[] splitFhirPaths,
                                           final boolean resolveFollows,
                                           final boolean castFollows) {
        return Arrays.stream(splitFhirPaths)
                .filter(en -> {
                    if (resolveFollows) {
                        return StringUtils.isNotEmpty(en) && !en.equals(RESOLVE);
                    } else if (castFollows) {
                        return StringUtils.isNotEmpty(en) && !en.startsWith("as(");
                    } else {
                        return StringUtils.isNotEmpty(en);
                    }
                })
                .collect(Collectors.toList());
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
        Class aClass = specialThisHandling ? clazz : findClass(theField, forcingClassToUse);
        if (aClass == null) {
            // fallback if we cant find the one we want...
            aClass = findClass(theField, null);
        }
        final Object generatedInstance = newInstance(aClass);
        Object objectToReturn = (generatedInstance instanceof DomainResource && !resolveFollows) ? new Reference() : generatedInstance;

        Object setObj = null;
        if (!specialThisHandling) {
            objectToReturn = theField.getType().equals(Enumeration.class) && objectToReturn instanceof CodeType ? new Enumeration<>() : generatedInstance;
            setObj = setFieldObject(theField, resource, objectToReturn);
        } else {
            if (originalResource instanceof List) {
                ((List) originalResource).add(generatedInstance);
            }
        }
        final String path = splitPath + (StringUtils.isBlank(followingWhereCondition) ? "" : ("." + followingWhereCondition));
        return InstantiateAndSetReturn.builder()
                .returning(specialThisHandling ? originalResource : setObj)
                .path(path.replace(THIS, ""))
                .isList(specialThisHandling ? originalResource instanceof List : theField.getType() == List.class)
                .build();
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
                final Class nextClass = getFhirResourceType(resolveResourceType);
                final Object nextClassInstance = newInstance(nextClass);

                final List<String> list = Arrays.asList(fhirPath.split("\\."));
                final InstantiateAndSetReturn returning = instantiateAndSetElement(nextClassInstance, nextClass,
                        String.join(".", list.subList(1, list.size())),
                        forcingClass,
                        resolveResourceType);
                ((Reference) resource).setResource((IBaseResource) nextClassInstance);
                final Object obj = nextClassInstance;
                final String path = RESOLVE;
                return InstantiateAndSetReturn.builder().returning(obj).path(path).inner(returning).isList(false).build();
            }

            fhirPath = fhirPath
                    .replace(RESOLVE + "." + FHIR_ROOT_FC + ".", "")
                    .replace(RESOLVE + "." + FHIR_ROOT_FC, "");
        }

        if (fhirPath.startsWith(".")) {
            fhirPath = fhirPath.substring(1);
        }

        String followingWhereCondition = getWhereForInstantiation(fhirPath, clazz);

        if (followingWhereCondition != null) {
            fhirPath = fhirPath
                    .replace("." + followingWhereCondition, "")
                    .replace(followingWhereCondition, "");
        }

        final String preparedFhirPath = prepareFhirPathForInstantiation(clazz, fhirPath);
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
            final List<String> list = listFromSplitPath(splitFhirPaths, resolveFollows, castFollows);

            final String castingTo = castFollows ? openFhirStringUtils.getCastType(preparedFhirPath) : null;
            final Class nextClass = castFollows ? getClassForName(R4_HAPI_PACKAGE + castingTo) : findClass(theField, resolveFollows ? resolveResourceType : null);
            final Object nextClassInstance = newInstance(nextClass);


            final InstantiateAndSetReturn returning = instantiateAndSetElement(nextClassInstance, nextClass,
                    String.join(".", list.subList(1, list.size())),
                    forcingClass,
                    resolveResourceType);
            final Object obj = setFieldObject(theField, resource, nextClassInstance);
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

    public String enrichFhirPathWithRecurring(final Class clazz, final String fhirPath, final StringBuilder sb) {
        final String preparedFhirPath = prepareFhirPathForInstantiation(clazz, fhirPath);
        final String[] splitFhirPaths = preparedFhirPath.split("\\.");
        for (int i = 0; i < splitFhirPaths.length; i++) {
            String splitPath = splitFhirPaths[i];
            final Field[] childElements = FieldUtils.getFieldsWithAnnotation(clazz, Child.class);
            final Field theField = Arrays.stream(childElements).filter(child -> splitPath.equals(child.getName())).findFirst().orElse(null);
            if (theField == null) {
                continue;
            }
            sb.append(".").append(splitPath);
            if (theField.getType() == List.class) {
                sb.append(RECURRING_SYNTAX);
            }
            final int arrayLength = splitFhirPaths.length - 1;
            final boolean resolveFollows = i != arrayLength && RESOLVE.equals(splitFhirPaths[i + 1]);
            final boolean castFollows = i != arrayLength && splitFhirPaths[i + 1].startsWith("as(");
            if (preparedFhirPath.equals(splitPath)) {
                // means we've reached the end
                return sb.toString();
            }
            final List<String> list = listFromSplitPath(splitFhirPaths, resolveFollows, castFollows);
            final String castingTo = castFollows ? openFhirStringUtils.getCastType(preparedFhirPath) : null;
            final Class nextClass = castFollows ? getClassForName(R4_HAPI_PACKAGE + castingTo) : findClass(theField, null);

            return enrichFhirPathWithRecurring(nextClass,
                    String.join(".", list.subList(1, list.size())), sb);
        }
        return null;
    }

    private Object setFieldObject(final Field theField, final Object resource, final Object settingObject) {
        if (theField == null) {
            return null;
        }
        final Object value = wrapInReferenceIfNeeded(settingObject);
        try {
            theField.setAccessible(true);
            if (theField.getType() == List.class) {
                final List<Object> list = new ArrayList<>();
                if (theField.get(resource) == null) {
                    theField.set(resource, list);
                    list.add(value);
                    return list;
                } else {
                    final List existingList = (List) theField.get(resource);
                    existingList.add(value);
                    return existingList;
                }
            } else {
                theField.set(resource, value);
            }
        } catch (IllegalAccessException e) {
            log.error("Error trying to set field object.", e);
        }
        return value;
    }

    private Object wrapInReferenceIfNeeded(final Object settingObject) {
        if (settingObject instanceof DomainResource) {
            return new Reference().setResource((DomainResource) settingObject);
        }
        return settingObject;
    }

    private Class findClass(final Field field, final String forcingClass) {
        if (field == null) {
            return null;
        }
        final Child childAnnotation = field.getAnnotation(Child.class);

        final Class<? extends IElement>[] types = childAnnotation.type();

        if (types.length == 0) {
            // backboneelement
            if (field.getGenericType() instanceof ParameterizedType) {
                final Type actualTypeArgument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                return getClassForName(actualTypeArgument.getTypeName());
            }
            if (StringUtils.isNotEmpty(forcingClass) && field.getType().getName().endsWith("Type")) {
                return getClassForName(field.getType().getName().replace("Type", forcingClass));
            } else {
                return getClassForName(field.getType().getName());
            }
        } else {
            if (forcingClass == null) {
                if (field.getType().isAssignableFrom(Enumeration.class)) {
                    return field.getType();
                }
                return types[0];
            } else if ("extension".equals(field.getName()) || "modifierExtension".equals(field.getName())) {
                // special handling
                return Extension.class;
            }
            return Arrays.stream(types)
                    .filter(type -> {
                        final Class classForName = getClassForName(type.getName());
                        if (classForName == null) {
                            return false;
                        }
                        return forcingClass.equals(type.getSimpleName())
                                || ("Coding".equals(forcingClass) && "CodeType".equals(type.getSimpleName()))
                                || getFhirResourceType(forcingClass).isAssignableFrom(classForName);
                    })
                    .map(type -> getClassForName(type.getName())) // if getClassForName was false, then element wouldn't be in the list as its filtered by that above
                    .findFirst()
                    .orElse(null);
        }
    }

    private Object newInstance(final Class clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            log.error("Error trying to create a new instance of class: {}", clazz, e);
        }
        return null;
    }

    private Class getClassForName(final String name) {
        try {
            return Class.forName(name);
        } catch (final Exception e) {
            log.error("Error: ", e);
            return null;
        }
    }

    private String prepareFhirPathForInstantiation(final Class clazz, final String fhirPath) {
        String prepared = fhirPath;

        if (fhirPath.startsWith(clazz.getSimpleName())) {
            prepared = prepared.replace(clazz.getSimpleName() + ".", "");
        }

        return prepared;
    }

    public Class<? extends IBaseResource> getFhirResourceType(final String resourceName) {
        try {
            return (Class<? extends IBaseResource>) Class.forName(Patient.class.getPackage().getName() + "." + resourceName);
        } catch (final ClassNotFoundException e) {
            return null;
        }
    }
}
