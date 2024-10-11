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
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RESOLVE;

/**
 * Creates and instantiates HAPI FHIR Resources based on FHIR Path expressions
 */
@Slf4j
@Component
public class FhirInstanceCreator {

    private final String R4_HAPI_PACKAGE = "org.hl7.fhir.r4.model.";

    private OpenFhirStringUtils openFhirStringUtils;

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

    public Resource create(final String resourceType) {
        try {
            return (Resource) getFhirResourceType(resourceType).getDeclaredConstructor().newInstance();
        } catch (final Exception e) {
            log.error("Couldn't create a new instance of {}", resourceType, e);
            return null;
        }
    }

    public InstantiateAndSetReturn instantiateAndSetElement(final Object resource, final Class clazz, final String fhirPath, final String forcingClass) {
        return instantiateAndSetElement(resource, clazz, fhirPath, forcingClass, null);
    }

    public InstantiateAndSetReturn instantiateAndSetElement(Object resource, Class clazz, String fhirPath, final String forcingClass, final String resolveResourceType) {
        final Object originalResource = resource;
        if (resource instanceof List) {
            resource = ((List<?>) resource).get(((List<?>) resource).size() - 1);
            clazz = resource.getClass();
        }

        if(StringUtils.isBlank(fhirPath)) {
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
                        list.subList(1, list.size()).stream().collect(Collectors.joining(".")),
                        forcingClass,
                        resolveResourceType);
                ((Reference) resource).setResource((IBaseResource) nextClassInstance);
                final Object obj = nextClassInstance;
                final String path = RESOLVE;
                return InstantiateAndSetReturn.builder()
                        .returning(obj)
                        .path(path)
                        .inner(returning)
                        .isList(false)
                        .build();
            }

            fhirPath = fhirPath
                    .replace(RESOLVE + "." + FHIR_ROOT_FC + ".", "")
                    .replace(RESOLVE + "." + FHIR_ROOT_FC, "");
        }

        if (fhirPath.startsWith(".")) {
            fhirPath = fhirPath.substring(1);
        }

        String followingWhereCondition = null;
        final String[] splitByDot = fhirPath.split("\\.");
        if (splitByDot.length > 1) {
            boolean shouldHandleWhere;
            if (splitByDot[0].equals(clazz.getSimpleName()) && splitByDot.length > 2) {
                shouldHandleWhere = splitByDot[1].startsWith("where") || splitByDot[2].startsWith("where");
            } else {
                shouldHandleWhere = splitByDot[1].startsWith("where");
            }

            if(fhirPath.startsWith("where")) {
                shouldHandleWhere = true;
            }

            if (shouldHandleWhere) {
                followingWhereCondition = openFhirStringUtils.extractWhereCondition(fhirPath);
            }
        }

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
            final boolean resolveFollows = i != (splitFhirPaths.length - 1) ? RESOLVE.equals(splitFhirPaths[i + 1]) : false;
            final boolean castFollows = i != (splitFhirPaths.length - 1) ? splitFhirPaths[i + 1].startsWith("as(") : false;

            // second part of the condition is there to solve cases when the path ends with a cast, i.e. asNeeded.as(Boolean)
            if (preparedFhirPath.equals(splitPath)) {
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
            final List<String> list = Arrays.asList(splitFhirPaths).stream()
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
            final String castingTo = castFollows ? openFhirStringUtils.getCastType(preparedFhirPath) : null;
            final Class nextClass = castFollows ? getClassForName(R4_HAPI_PACKAGE + castingTo) : findClass(theField, resolveFollows ? resolveResourceType : null);
            final Object nextClassInstance = newInstance(nextClass);


            final InstantiateAndSetReturn returning = instantiateAndSetElement(nextClassInstance, nextClass,
                    list.subList(1, list.size()).stream().collect(Collectors.joining(".")),
                    forcingClass,
                    resolveResourceType);
            final Object obj = setFieldObject(theField, resource, nextClassInstance);
            final String path = splitPath + (castFollows ? ("." + splitFhirPaths[i + 1]) : "") + (StringUtils.isBlank(followingWhereCondition) ? "" : ("." + followingWhereCondition));
            return InstantiateAndSetReturn.builder()
                    .returning(obj)
                    .path(path)
                    .inner(returning)
                    .isList(theField.getType() == List.class)
                    .build();
        }
        return null;
    }

    public void setElement(final Object resource, final Class clazz, final String fhirPath, final Object toSet) {
        final String preparedFhirPath = prepareFhirPathForInstantiation(clazz, fhirPath);
        final String[] splitFhirPaths = preparedFhirPath.split("\\.");
        for (int i = 0; i < splitFhirPaths.length; i++) {
            String splitPath = splitFhirPaths[i];
            final Field[] childElements = FieldUtils.getFieldsWithAnnotation(clazz, Child.class);
            final Field theField = Arrays.stream(childElements).filter(child -> splitPath.equals(child.getName())).findFirst().orElse(null);
            if (theField == null) {
                continue;
            }
            final boolean resolveFollows = i != (splitFhirPaths.length - 1) ? RESOLVE.equals(splitFhirPaths[i + 1]) : false;
            final boolean castFollows = i != (splitFhirPaths.length - 1) ? splitFhirPaths[i + 1].startsWith("as(") : false;
            if (preparedFhirPath.equals(splitPath)) {
                // means we've reached the end
                theField.setAccessible(true);
                try {
                    theField.set(resource, toSet);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                return;
            }
            final List<String> list = Arrays.asList(splitFhirPaths).stream()
                    .filter(en -> {
                        if (!resolveFollows && !castFollows) {
                            return true;
                        } else {
                            return !en.equals(RESOLVE) && !en.startsWith("as(");
                        }
                    })
                    .collect(Collectors.toList());
            final String castingTo = castFollows ? openFhirStringUtils.getCastType(preparedFhirPath) : null;
            final Class nextClass = castFollows ? getClassForName(R4_HAPI_PACKAGE + castingTo) : findClass(theField, null);
            final Object nextClassInstance = newInstance(nextClass);


            setElement(nextClassInstance,
                    nextClass,
                    list.subList(1, list.size()).stream().collect(Collectors.joining(".")),
                    toSet);
            return;
        }
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
            sb.append("." + splitPath);
            if (theField.getType() == List.class) {
                sb.append("[n]");
            }
            final boolean resolveFollows = i != (splitFhirPaths.length - 1) ? RESOLVE.equals(splitFhirPaths[i + 1]) : false;
            final boolean castFollows = i != (splitFhirPaths.length - 1) ? splitFhirPaths[i + 1].startsWith("as(") : false;
            if (preparedFhirPath.equals(splitPath)) {
                // means we've reached the end
                return sb.toString();
            }
            final List<String> list = Arrays.asList(splitFhirPaths).stream()
                    .filter(en -> {
                        if (!resolveFollows && !castFollows) {
                            return true;
                        } else {
                            return !en.equals(RESOLVE) && !en.startsWith("as(");
                        }
                    })
                    .collect(Collectors.toList());
            final String castingTo = castFollows ? openFhirStringUtils.getCastType(preparedFhirPath) : null;
            final Class nextClass = castFollows ? getClassForName(R4_HAPI_PACKAGE + castingTo) : findClass(theField, null);

            return enrichFhirPathWithRecurring(nextClass,
                    list.subList(1, list.size()).stream().collect(Collectors.joining(".")), sb);
        }
        return null;
    }

    private Object setFieldObject(final Field theField, final Object resource, final Object settingObject) {
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
                    .filter(type -> forcingClass.equals(type.getSimpleName()) || ("Coding".equals(forcingClass) && "CodeType".equals(type.getSimpleName())) || getFhirResourceType(forcingClass).isAssignableFrom(getClassForName(type.getName())))
                    .map(type -> getClassForName(type.getName()))
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
