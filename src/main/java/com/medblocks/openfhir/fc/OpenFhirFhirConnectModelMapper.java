
package com.medblocks.openfhir.fc;

import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import com.medblocks.openfhir.fc.schema.model.OpenEhrConfig;
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

    private OpenFhirFhirConfig fhirConfig;
    private OpenEhrConfig openEhrConfig;
    private List<Mapping> mappings;

    public OpenFhirFhirConnectModelMapper copy() {
        final OpenFhirFhirConnectModelMapper fhirConnectMapper = new OpenFhirFhirConnectModelMapper();
        fhirConnectMapper.setFhirConfig(fhirConfig == null ? null : fhirConfig.copy());

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
        openFhirFhirConnectModelMapper.setMappings(fhirConnectModel.getMappings());
        openFhirFhirConnectModelMapper.setOpenEhrConfig(
                new OpenEhrConfig().withArchetype(fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype()));
        openFhirFhirConnectModelMapper.setFhirConfig(new OpenFhirFhirConfig()
                                                             .withCondition(fhirConnectModel.getSpec().getFhirConfig()
                                                                                    .getCondition())
                                                             .withMultiple(fhirConnectModel.getSpec().getFhirConfig().getMultiple())
                                                             .withResource(parseResourceType(fhirConnectModel)));
        return openFhirFhirConnectModelMapper;
    }

    private String parseResourceType(final FhirConnectModel fhirConnectModel) {
        final String structureDefinition = fhirConnectModel.getSpec().getFhirConfig().getStructureDefinition();
        return structureDefinition.replace("http://hl7.org/fhir/StructureDefinition/", "");
    }


}
