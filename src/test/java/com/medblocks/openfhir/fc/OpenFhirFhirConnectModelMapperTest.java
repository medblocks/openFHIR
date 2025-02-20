package com.medblocks.openfhir.fc;

import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import com.medblocks.openfhir.util.OpenFhirTestUtility;
import java.io.InputStream;
import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public class OpenFhirFhirConnectModelMapperTest {

    @Test
    public void handleManualMappingsTest() {
        final Yaml yaml = OpenFhirTestUtility.getYaml();
        final InputStream inputStream = this.getClass()
                .getResourceAsStream("/com/medblocks/openfhir/fc/manualMappingsTest.yml");
        final FhirConnectModel fhirConnectModel = yaml.loadAs(inputStream, FhirConnectModel.class);
        final OpenFhirFhirConnectModelMapper handled = new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(
                fhirConnectModel);

        // assert first level mappings
        Assert.assertEquals(6, handled.getMappings().size());
        Assert.assertEquals("context", handled.getMappings().get(0).getName());
        Assert.assertEquals("$resource.effective.as(Period)", handled.getMappings().get(0).getWith().getFhir());
        Assert.assertEquals("$composition/context", handled.getMappings().get(0).getWith().getOpenehr());
        Assert.assertEquals(2, handled.getMappings().get(0).getFollowedBy().getMappings().size());
        Assert.assertEquals("contextStart",
                            handled.getMappings().get(0).getFollowedBy().getMappings().get(0).getName());
        Assert.assertEquals("contextEnd", handled.getMappings().get(0).getFollowedBy().getMappings().get(1).getName());
        Assert.assertEquals("_end_time",
                            handled.getMappings().get(0).getFollowedBy().getMappings().get(1).getWith().getOpenehr());

        final Mapping firstLevelDraftTerminology = handled.getMappings().get(1);
        Assert.assertEquals("firstLevelTest.draft", firstLevelDraftTerminology.getName());
        Assert.assertEquals("openehr", firstLevelDraftTerminology.getWith().getValue());
        Assert.assertEquals("$archetype/ism_transition/current_state/terminology_id",
                            firstLevelDraftTerminology.getWith().getOpenehr());
        Assert.assertEquals("status", firstLevelDraftTerminology.getFhirCondition().getTargetRoot());
        Assert.assertEquals("[draft]", firstLevelDraftTerminology.getFhirCondition().getCriteria());

        final Mapping firstLevelDraftValue = handled.getMappings().get(2);
        Assert.assertEquals("firstLevelTest.draft", firstLevelDraftValue.getName());
        Assert.assertEquals("Initial", firstLevelDraftValue.getWith().getValue());
        Assert.assertEquals("$archetype/ism_transition/current_state/value",
                            firstLevelDraftValue.getWith().getOpenehr());
        Assert.assertEquals("status", firstLevelDraftValue.getFhirCondition().getTargetRoot());
        Assert.assertEquals("[draft]", firstLevelDraftValue.getFhirCondition().getCriteria());

        final Mapping firstLevelDraftCode = handled.getMappings().get(3);
        Assert.assertEquals("firstLevelTest.draft", firstLevelDraftCode.getName());
        Assert.assertEquals("524", firstLevelDraftCode.getWith().getValue());
        Assert.assertEquals("$archetype/ism_transition/current_state/defining_code",
                            firstLevelDraftCode.getWith().getOpenehr());
        Assert.assertEquals("status", firstLevelDraftCode.getFhirCondition().getTargetRoot());
        Assert.assertEquals("[draft]", firstLevelDraftCode.getFhirCondition().getCriteria());

        final Mapping firstLevelDraftFhir = handled.getMappings().get(4);
        Assert.assertEquals("firstLevelTest.draft", firstLevelDraftFhir.getName());
        Assert.assertEquals("draft", firstLevelDraftFhir.getWith().getValue());
        Assert.assertEquals("$resource.status", firstLevelDraftFhir.getWith().getFhir());
        Assert.assertEquals("$archetype/ism_transition/current_state",
                            firstLevelDraftFhir.getOpenehrCondition().getTargetRoot());
        Assert.assertEquals("defining_code", firstLevelDraftFhir.getOpenehrCondition().getTargetAttribute());
        Assert.assertEquals("[524]", firstLevelDraftFhir.getOpenehrCondition().getCriteria());

        // assert second level mappings
        final Mapping innerMapping = handled.getMappings().get(5);
        Assert.assertEquals("$resource", innerMapping.getWith().getFhir());
        Assert.assertEquals("$archetype/ism_transition/current_state", innerMapping.getWith().getOpenehr());
        Assert.assertEquals(2, innerMapping.getFollowedBy().getMappings().size());
        Assert.assertEquals("performer", innerMapping.getFollowedBy().getMappings().get(0).getName());
        Assert.assertEquals("$resource.performer.as(Reference).display", innerMapping.getFollowedBy().getMappings().get(0).getWith().getFhir());
        Assert.assertEquals("$composition/perfomer", innerMapping.getFollowedBy().getMappings().get(0).getWith().getOpenehr());
        Assert.assertEquals("STRING", innerMapping.getFollowedBy().getMappings().get(0).getWith().getType());
        Assert.assertNull(innerMapping.getFollowedBy().getMappings().get(0).getFollowedBy());

        final Mapping secondLevelMapping = innerMapping.getFollowedBy().getMappings().get(1);
        Assert.assertEquals("secondLevel", secondLevelMapping.getName());
        Assert.assertEquals("status", secondLevelMapping.getWith().getFhir());
        Assert.assertEquals("something/else", secondLevelMapping.getWith().getOpenehr());
        Assert.assertNull(secondLevelMapping.getManual());

        Assert.assertEquals(5, secondLevelMapping.getFollowedBy().getMappings().size());
        final Mapping secondLevelFirst = secondLevelMapping.getFollowedBy().getMappings().get(0);
        Assert.assertEquals("name", secondLevelFirst.getName());
        Assert.assertEquals("$resource.code", secondLevelFirst.getWith().getFhir());
        Assert.assertEquals("$archetype", secondLevelFirst.getWith().getOpenehr());
        Assert.assertEquals("Name", secondLevelFirst.getFollowedBy().getMappings().get(0).getName());
        Assert.assertEquals("coding", secondLevelFirst.getFollowedBy().getMappings().get(0).getWith().getFhir());
        Assert.assertEquals("description[at0001]/items[at0002]", secondLevelFirst.getFollowedBy().getMappings().get(0).getWith().getOpenehr());
        Assert.assertEquals("CODING", secondLevelFirst.getFollowedBy().getMappings().get(0).getWith().getType());
        Assert.assertEquals("[http://fhir.de/CodeSystem/bfarm/ops, http://snomed.info/sct]", secondLevelFirst.getFollowedBy().getMappings().get(0).getFhirCondition().getCriteria());

        final Mapping secondLevelTheOnesTerminology = secondLevelMapping.getFollowedBy().getMappings().get(1);
        final Mapping secondLevelTheOnesValue = secondLevelMapping.getFollowedBy().getMappings().get(2);
        final Mapping secondLevelTheOnesCode = secondLevelMapping.getFollowedBy().getMappings().get(3);
        final Mapping secondLevelTheOnesFhir = secondLevelMapping.getFollowedBy().getMappings().get(4);
        Assert.assertEquals(5, secondLevelMapping.getFollowedBy().getMappings().size());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesTerminology.getName());
        Assert.assertEquals("third/terminology_id", secondLevelTheOnesTerminology.getWith().getOpenehr());
        Assert.assertEquals("openehr", secondLevelTheOnesTerminology.getWith().getValue());
        Assert.assertEquals("[active]", secondLevelTheOnesTerminology.getFhirCondition().getCriteria());
        Assert.assertEquals("status", secondLevelTheOnesTerminology.getFhirCondition().getTargetRoot());
        Assert.assertEquals("value", secondLevelTheOnesTerminology.getFhirCondition().getTargetAttribute());
        Assert.assertNull(secondLevelTheOnesTerminology.getOpenehrCondition());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesValue.getName());
        Assert.assertEquals("third/value", secondLevelTheOnesValue.getWith().getOpenehr());
        Assert.assertEquals("InitialX", secondLevelTheOnesValue.getWith().getValue());
        Assert.assertEquals("[active]", secondLevelTheOnesValue.getFhirCondition().getCriteria());
        Assert.assertEquals("status", secondLevelTheOnesValue.getFhirCondition().getTargetRoot());
        Assert.assertEquals("value", secondLevelTheOnesValue.getFhirCondition().getTargetAttribute());
        Assert.assertNull(secondLevelTheOnesValue.getOpenehrCondition());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesCode.getName());
        Assert.assertEquals("third/defining_code", secondLevelTheOnesCode.getWith().getOpenehr());
        Assert.assertEquals("xxx", secondLevelTheOnesCode.getWith().getValue());
        Assert.assertEquals("[active]", secondLevelTheOnesCode.getFhirCondition().getCriteria());
        Assert.assertEquals("status", secondLevelTheOnesCode.getFhirCondition().getTargetRoot());
        Assert.assertEquals("value", secondLevelTheOnesCode.getFhirCondition().getTargetAttribute());
        Assert.assertNull(secondLevelTheOnesCode.getOpenehrCondition());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesFhir.getName());
        Assert.assertEquals("code.status", secondLevelTheOnesFhir.getWith().getFhir());
        Assert.assertEquals("yyyy", secondLevelTheOnesFhir.getWith().getValue());
        Assert.assertEquals("third", secondLevelTheOnesFhir.getOpenehrCondition().getTargetRoot());
        Assert.assertEquals("[999]", secondLevelTheOnesFhir.getOpenehrCondition().getCriteria());
        Assert.assertNull(secondLevelTheOnesFhir.getFhirCondition());
    }
}