package com.medblocks.openfhir.util;

import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.fc.schema.model.FollowedBy;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import com.medblocks.openfhir.fc.schema.model.Mapping.ModelExtension;
import com.medblocks.openfhir.fc.schema.model.With;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FhirConnectModelMergerTest {

    private FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();

    @Test
    void findMapping() {
        final List<Mapping> mappings = mockMappingTree();

        Assertions.assertEquals("c", fhirConnectModelMerger.findMapping(mappings, "a.b.c").getName());
        Assertions.assertNull(fhirConnectModelMerger.findMapping(mappings, "a.x.c"));
        Assertions.assertNull(fhirConnectModelMerger.findMapping(null, "a.x.c"));
        Assertions.assertEquals("a", fhirConnectModelMerger.findMapping(mappings, "a").getName());
    }

    @Test
    void findMappingByName() {
        final List<Mapping> mappings = mockMappingTree();

        Assertions.assertEquals("c", fhirConnectModelMerger.findMappingByName(mappings, "c").getName());
        Assertions.assertEquals("x", fhirConnectModelMerger.findMappingByName(mappings, "x").getName());
        Assertions.assertEquals("a", fhirConnectModelMerger.findMappingByName(mappings, "a").getName());
        Assertions.assertNull(fhirConnectModelMerger.findMappingByName(mappings, "p"));
    }

    @Test
    void overwriteMapping() {
        final FhirConnectModel fhirConnectModel = new FhirConnectModel();
        fhirConnectModel.setMappings(mockMappingTree());

        final Mapping extension = new Mapping();
        extension.setExtension(ModelExtension.OVERWRITE);
        extension.setFhirCondition(new Condition().withCriteria("random criteria"));
        extension.setName("b");

        fhirConnectModelMerger.overwriteMapping(fhirConnectModel, extension);

        final List<Mapping> vanillaMappings = fhirConnectModel.getMappings();
        Assertions.assertEquals("random criteria",
                                fhirConnectModelMerger.findMappingByName(vanillaMappings, "b").getFhirCondition()
                                        .getCriteria());
        Assertions.assertNull(fhirConnectModelMerger.findMappingByName(vanillaMappings, "b").getWith());
    }

    @Test
    void addMapping() {
        final FhirConnectModel fhirConnectModel = new FhirConnectModel();
        fhirConnectModel.setMappings(mockMappingTree());

        final Mapping extension = new Mapping();
        extension.setExtension(ModelExtension.ADD);
        extension.setFhirCondition(new Condition().withCriteria("random criteria"));

        fhirConnectModelMerger.addMapping(fhirConnectModel, extension);

        final List<Mapping> vanillaMappings = fhirConnectModel.getMappings();
        Assertions.assertEquals("random criteria",
                                vanillaMappings.get(vanillaMappings.size() - 1).getFhirCondition().getCriteria());
    }

    @Test
    void appendMapping_fhirCondition() {
        final FhirConnectModel fhirConnectModel = new FhirConnectModel();
        fhirConnectModel.setMappings(mockMappingTree());

        final Mapping extension = new Mapping();
        extension.setExtension(ModelExtension.APPEND);
        extension.setAppendTo("a.b.c");
        extension.setFhirCondition(new Condition().withCriteria("random criteria"));

        fhirConnectModelMerger.appendMapping(fhirConnectModel, extension);

        Assertions.assertEquals("random criteria",
                                fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b.c")
                                        .getFhirCondition().getCriteria());
    }

    @Test
    void appendMapping_openehrCondition() {
        final FhirConnectModel fhirConnectModel = new FhirConnectModel();
        fhirConnectModel.setMappings(mockMappingTree());

        final Mapping extension = new Mapping();
        extension.setExtension(ModelExtension.APPEND);
        extension.setAppendTo("a.b.c");
        extension.setOpenehrCondition(new Condition().withCriteria("random criteria"));

        fhirConnectModelMerger.appendMapping(fhirConnectModel, extension);

        Assertions.assertEquals("random criteria",
                                fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b.c")
                                        .getOpenehrCondition().getCriteria());
    }

    @Test
    void appendMapping_appendToNotFound() {
        final FhirConnectModel fhirConnectModel = new FhirConnectModel();
        fhirConnectModel.setMappings(mockMappingTree());

        final Mapping extension = new Mapping();
        extension.setExtension(ModelExtension.APPEND);
        extension.setAppendTo("a.x.c");
        extension.setOpenehrCondition(new Condition().withCriteria("random criteria"));

        fhirConnectModelMerger.appendMapping(fhirConnectModel, extension);

        Assertions.assertNull(fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b.c")
                                      .getOpenehrCondition());
    }

    @Test
    void appendMapping_followedBy() {
        final FhirConnectModel fhirConnectModel = new FhirConnectModel();
        fhirConnectModel.setMappings(mockMappingTree());

        final Mapping extension = new Mapping();
        extension.setExtension(ModelExtension.APPEND);
        extension.setAppendTo("a.b");
        extension.setFollowedBy(new FollowedBy().withMappings(
                List.of(new Mapping().withName("x").withWith(new With().withFhir("fhir.path")),
                        new Mapping().withName("y"))));

        fhirConnectModelMerger.appendMapping(fhirConnectModel, extension);

        Assertions.assertEquals(3, fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b")
                .getFollowedBy().getMappings().size());

        Assertions.assertEquals("c", fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b")
                .getFollowedBy().getMappings().get(0).getName());

        Assertions.assertEquals("x", fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b")
                .getFollowedBy().getMappings().get(1).getName());

        Assertions.assertEquals("fhir.path", fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b")
                .getFollowedBy().getMappings().get(1).getWith().getFhir());

        Assertions.assertEquals("y", fhirConnectModelMerger.findMapping(fhirConnectModel.getMappings(), "a.b")
                .getFollowedBy().getMappings().get(2).getName());
    }

    private List<Mapping> mockMappingTree() {
        final List<Mapping> mappings = new ArrayList<>();
        final Mapping firstLevelMapping = new Mapping();
        firstLevelMapping.setName("a");
        final Mapping secondLevelMapping = new Mapping();
        secondLevelMapping.setName("b");
        secondLevelMapping.setFhirCondition(new Condition().withCriteria("a=b"));
        secondLevelMapping.setWith(new With().withFhir("Fhir.Path"));
        final FollowedBy secondFollowedBy = new FollowedBy();
        secondFollowedBy.setMappings(List.of(new Mapping().withName("c")));
        final FollowedBy firstFollowedBy = new FollowedBy();
        firstLevelMapping.setFollowedBy(firstFollowedBy);
        secondLevelMapping.setFollowedBy(secondFollowedBy);
        firstFollowedBy.setMappings(List.of(new Mapping().withName("d"),
                                            new Mapping().withName("e"),
                                            secondLevelMapping));
        mappings.add(new Mapping().withName("x"));
        mappings.add(firstLevelMapping);
        mappings.add(new Mapping().withName("y"));
        mappings.add(new Mapping().withName("z"));
        return mappings;
    }
}