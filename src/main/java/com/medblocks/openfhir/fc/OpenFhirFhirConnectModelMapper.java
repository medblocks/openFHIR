
package com.medblocks.openfhir.fc;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_ROOT_FC;

import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.fc.schema.model.Manual;
import com.medblocks.openfhir.fc.schema.model.ManualEntry;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import com.medblocks.openfhir.fc.schema.model.OpenEhrConfig;
import com.medblocks.openfhir.fc.schema.model.With;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenFhirFhirConnectModelMapper {

    private String name;
    private OpenFhirFhirConfig fhirConfig;
    private OpenEhrConfig openEhrConfig;
    private List<Mapping> mappings;

    public OpenFhirFhirConnectModelMapper copy() {
        final OpenFhirFhirConnectModelMapper fhirConnectMapper = new OpenFhirFhirConnectModelMapper();
        fhirConnectMapper.setFhirConfig(fhirConfig == null ? null : fhirConfig.copy());
        fhirConnectMapper.setName(name);
        fhirConnectMapper.setOpenEhrConfig(openEhrConfig == null ? null : openEhrConfig.copy());
        if (mappings != null) {
            final List<Mapping> copiedMappings = new ArrayList<>();
            for (Mapping mapping : mappings) {
                copiedMappings.add(mapping.copy());
            }
            fhirConnectMapper.setMappings(copiedMappings);
        }
        return fhirConnectMapper;
    }

    public OpenFhirFhirConnectModelMapper fromFhirConnectModelMapper(final FhirConnectModel fhirConnectModel) {
        final OpenFhirFhirConnectModelMapper openFhirFhirConnectModelMapper = new OpenFhirFhirConnectModelMapper();
        openFhirFhirConnectModelMapper.setMappings(handleMappings(fhirConnectModel.getMappings()));
        doManualMappings(fhirConnectModel.getMappings());
        openFhirFhirConnectModelMapper.setOpenEhrConfig(
                new OpenEhrConfig().withArchetype(fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype()));
        openFhirFhirConnectModelMapper.setFhirConfig(new OpenFhirFhirConfig()
                                                             .withCondition(fhirConnectModel.getSpec().getFhirConfig()
                                                                                    .getCondition())
                                                             .withMultiple(fhirConnectModel.getSpec().getFhirConfig().getMultiple())
                                                             .withResource(parseResourceType(fhirConnectModel)));
        openFhirFhirConnectModelMapper.setName(fhirConnectModel.getMetadata().getName());
        return openFhirFhirConnectModelMapper;
    }

    private void doManualMappings(final List<Mapping> mappings) {
        if(mappings == null) {
            return;
        }
        for (final Mapping mapping : mappings) {
            if(mapping.getFollowedBy() == null
                    || mapping.getFollowedBy().getMappings() == null
                    || mapping.getFollowedBy().getMappings().isEmpty()) {
                continue;
            }
            mapping.getFollowedBy().setMappings(handleMappings(mapping.getFollowedBy().getMappings()));
            doManualMappings(mapping.getFollowedBy().getMappings());
        }
    }

    private List<Mapping> handleMappings(final List<Mapping> mappingsFromFile) {
        final List<Mapping> toReturn = new ArrayList<>();
        if(mappingsFromFile == null) {
            return null;
        }
        for (final Mapping mapping : mappingsFromFile) {
            if(mapping.getManual() == null || mapping.getManual().isEmpty()) {
                // add it as is
                toReturn.add(mapping);
            } else {
                // create more than one
                for (final Manual manual : mapping.getManual()) {
                    // when setting openehr, it has to have fhir condition
                    // when setting fhir, it has to have openehr condition
                    if (manual.getOpenehr() != null) {
                        for (final ManualEntry openEhrManualEntry : manual.getOpenehr()) {
                            final Mapping fromManual = new Mapping();
                            fromManual.setName(mapping.getName() + "." + manual.getName());
                            fromManual.setWith(new With()
                                                       .withValue(openEhrManualEntry.getValue())
                                                       .withOpenehr(mapping.getWith().getOpenehr() + "/" + openEhrManualEntry.getPath()));
                            final boolean manualFhirConditionNull = manual.getFhirCondition() == null;
                            fromManual.setFhirCondition(manualFhirConditionNull ? mapping.getFhirCondition() : manual.getFhirCondition().copy());
                            toReturn.add(fromManual);
                        }
                    }
                    if (manual.getFhir() != null) {
                        for (final ManualEntry fhirManualEntry : manual.getFhir()) {
                            final Mapping fromManual = new Mapping();
                            fromManual.setName(mapping.getName() + "." + manual.getName());
                            fromManual.setWith(new With()
                                                       .withValue(fhirManualEntry.getValue())
                                                       .withFhir(mapping.getWith().getFhir() + "." + fhirManualEntry.getPath()));
                            if (manual.getOpenehrCondition() != null) {
                                final Condition openEhrCondition = manual.getOpenehrCondition().copy();
                                if(openEhrCondition.getTargetRoot().equals(OPENEHR_ROOT_FC)) {
                                    openEhrCondition.setTargetRoot(mapping.getWith().getOpenehr());
                                }
                                fromManual.setOpenehrCondition(openEhrCondition);
                            } else {
                                fromManual.setOpenehrCondition(mapping.getOpenehrCondition());
                            }
                            toReturn.add(fromManual);
                        }
                    }
                }
            }
        }
        return toReturn;
    }

    private String parseResourceType(final FhirConnectModel fhirConnectModel) {
        final String structureDefinition = fhirConnectModel.getSpec().getFhirConfig().getStructureDefinition();
        return structureDefinition.replace("http://hl7.org/fhir/StructureDefinition/", "");
    }


}
