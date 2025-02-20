package com.medblocks.openfhir.util;

import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FhirConnectModelMerger {

    /**
     * Will join all coreModels with extensions according to extension logic (add, append, overwrite, ..)
     *
     * @param coreModels core models as gotten from the database
     * @param extensions extension models as gotten from the database
     * @return a list of OpenFhirFhirConnectModelMappers used in the actual implementation of the engine
     */
    public List<OpenFhirFhirConnectModelMapper> joinModelMappers(final List<FhirConnectModel> coreModels,
                                                                 final List<FhirConnectModel> extensions) {
        if (extensions == null) {
            return coreModels.stream()
                    .map(vm -> new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(vm))
                    .collect(Collectors.toList());
        }
        final List<OpenFhirFhirConnectModelMapper> builtMappers = new ArrayList<>();
        for (final FhirConnectModel coreModel : coreModels) {
            // find if there is any extension that exists for this core
            final List<FhirConnectModel> extensionsOfThisCore = extensions.stream()
                    .filter(ext -> ext.getSpec().get_extends().equals(coreModel.getMetadata().getName()))
                    .collect(Collectors.toList());

            builtMappers.add(mergeWithCore(coreModel, extensionsOfThisCore));
        }
        return builtMappers;
    }

    /**
     * Merges extensions to the core model mapping
     *
     * @param coreModel to be merged on
     * @param extensions to be merged
     * @return constructed OpenFhirFhirConnectModelMapper that results from a merge of core and extensions
     */
    private OpenFhirFhirConnectModelMapper mergeWithCore(final FhirConnectModel coreModel,
                                                         final List<FhirConnectModel> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            // no extension exists for this core mapping, add it to the output as is
            return new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(coreModel);
        }
        for (final FhirConnectModel extension : extensions) {
            if(extension.getMappings() == null) {
                continue;
            }
            for (final Mapping extensionMapping : extension.getMappings()) {
                switch (extensionMapping.getExtension()) {
                    case ADD -> addMapping(coreModel, extensionMapping);
                    case APPEND -> appendMapping(coreModel, extensionMapping);
                    case OVERWRITE -> overwriteMapping(coreModel, extensionMapping);
                }
            }
        }
        return new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(coreModel);
    }

    /**
     * Overwrites a mapping, meaning it overwrites it completely
     *
     * @param coreModel where it looks for this specific mapping to overwrite it
     * @param extensionMapping the one to overwrite with
     */
    void overwriteMapping(final FhirConnectModel coreModel, final Mapping extensionMapping) {
        log.info("Overriding {} with mapping from the extension", extensionMapping.getName());
        final Mapping toOverwrite = findMappingByName(coreModel.getMappings(), extensionMapping.getName());
        if (toOverwrite == null) {
            log.error("Couldn't find mapping named {} in the core model mapper. Not overwriting anything.",
                      extensionMapping.getName());
            return;
        }
        toOverwrite.copyOverWith(extensionMapping);
    }

    /**
     * Appends a mapping to a specific other mapping
     *
     * @param coreModel where it looks for this specific mapping that it needs to append to
     * @param extensionMapping that needs to be appended
     */
    void appendMapping(final FhirConnectModel coreModel, final Mapping extensionMapping) {
        log.info("Appending {} with mapping from the extension", extensionMapping.getName());
        final Mapping toAppendTo = findMapping(coreModel.getMappings(), extensionMapping.getAppendTo());
        if (toAppendTo == null) {
            log.error("Couldn't find mapping named {} in the core model mapper. Not appending anything.",
                      extensionMapping.getName());
            return;
        }
        // add the thing that is being appended
        if (extensionMapping.getFhirCondition() != null) {
            log.info("Appending fhirCondition to mapping {}", extensionMapping.getAppendTo());
            toAppendTo.setFhirCondition(extensionMapping.getFhirCondition());
        }
        if (extensionMapping.getOpenehrCondition() != null) {
            log.info("Appending openehrCondition to mapping {}", extensionMapping.getAppendTo());
            toAppendTo.setOpenehrCondition(extensionMapping.getOpenehrCondition());
        }
        if (extensionMapping.getWith() != null) {
            // is this a legit case? could you append a with? I think not, if anything, you need to append a followedBy
            // else just overwrite instead of append?
            log.warn(
                    "Extension mapping with is no null. Ignoring this as it's unclear what it should do. Perhaps you want to overwrite the mapping instead?");
        }
        if (extensionMapping.getFollowedBy() != null) {
            log.info("Appending followed by mappings to the end of existing followed by mappings of {}",
                     extensionMapping.getAppendTo());
            if (toAppendTo.getFollowedBy() != null) {
                final List<Mapping> existingMappings = toAppendTo.getFollowedBy().getMappings();
                final List<Mapping> modifiableExistingMappings = new ArrayList<>(
                        existingMappings); // because it could be unmodifiable
                modifiableExistingMappings.addAll(extensionMapping.getFollowedBy().getMappings());
                toAppendTo.getFollowedBy().setMappings(modifiableExistingMappings);
            } else {
                toAppendTo.setFollowedBy(extensionMapping.getFollowedBy());
            }
        }
    }

    /**
     * Adds a mapping to core coreModel mappers
     *
     * @param coreModel where it adds the mapping to
     * @param extensionMapping to be added
     */
    void addMapping(final FhirConnectModel coreModel, final Mapping extensionMapping) {
        log.info("Adding {} mapping from the extension", extensionMapping.getName());
        if (coreModel.getMappings() == null) {
            coreModel.setMappings(new ArrayList<>());
        }
        coreModel.getMappings().add(extensionMapping);
    }

    /**
     * Goes over all mappings and finds the one that has the same name; does so recursively in followedBy as well
     *
     * @param mappings to look into for one matching the 'name'
     * @param appendTo looking for a mapping with this name (this can be json path)
     * @return the matching mapping or null if it finds no such
     */
    Mapping findMapping(final List<Mapping> mappings, final String appendTo) {
        if (mappings == null) {
            return null;
        }
        final String toLookFor = appendTo.split("\\.")[0];
        final Mapping foundOnFirstLevel = mappings.stream()
                .filter(map -> toLookFor.equals(map.getName()))
                .findAny()
                .orElse(null);
        if (foundOnFirstLevel == null) {
            log.error("Couldn't find {} when trying to find a mapping to append to.", toLookFor);
            return null;
        }
        if (!appendTo.contains(".")) {
            // it means we've actually found it now, no more json paths
            return foundOnFirstLevel;
        }
        // Remove the first part of the appendTo string before the dot
        final String remainingPath = appendTo.substring(appendTo.indexOf('.') + 1);
        return findMapping(foundOnFirstLevel.getFollowedBy().getMappings(), remainingPath);
    }

    /**
     * Will find a mapping by name, even if the mapping we're looking for is nested in followed by mappings.
     * <p>
     * This method, in contrary to findMapping above, doesn't require json path
     */
    Mapping findMappingByName(final List<Mapping> mappings, final String name) {
        if (mappings == null) {
            return null;
        }
        final Mapping foundOnFirstLevel = mappings.stream()
                .filter(map -> name.equals(map.getName()))
                .findAny()
                .orElse(null);
        if (foundOnFirstLevel != null) {
            return foundOnFirstLevel;
        }
        for (final Mapping mapping : mappings) {
            if (mapping.getFollowedBy() == null) {
                continue;
            }
            final Mapping found = findMappingByName(mapping.getFollowedBy().getMappings(), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
