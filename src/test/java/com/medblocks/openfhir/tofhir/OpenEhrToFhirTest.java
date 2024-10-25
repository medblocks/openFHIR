package com.medblocks.openfhir.tofhir;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.TestOpenFhirMappingContext;
import com.medblocks.openfhir.fc.model.FhirConnectContext;
import com.medblocks.openfhir.util.*;
import com.nedap.archie.rm.composition.Composition;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Test;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OpenEhrToFhirTest {
    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());
    final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
    final TestOpenFhirMappingContext repo = new TestOpenFhirMappingContext(fhirPath, openFhirStringUtils);
    final OpenEhrToFhir openEhrToFhir;

    {
        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(openFhirStringUtils);
        openEhrToFhir = new OpenEhrToFhir(new FlatJsonMarshaller(),
                repo,
                new OpenEhrCachedUtils(null),
                new Gson(),
                openFhirStringUtils,
                new OpenEhrRmWorker(openFhirStringUtils),
                new OpenFhirMapperUtils(),
                new FhirInstancePopulator(),
                new FhirInstanceCreator(openFhirStringUtils, fhirInstanceCreatorUtility),
                fhirInstanceCreatorUtility,
                fhirPath,
                new IntermediateCacheProcessing(openFhirStringUtils));
    }

    public static void assertBloodPressureFhir(final Bundle bundle) {
        Assert.assertEquals(3, bundle.getEntry().size());
        final Observation obs1 = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> en.getCode().getText().equals("First bp"))
                .findFirst().orElse(null);
        final Observation obs2 = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> en.getCode().getText().equals("Second bp"))
                .findFirst().orElse(null);
        final Observation obs3 = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> en.getCode().getText().equals("Third bp"))
                .findFirst().orElse(null);

        Assert.assertEquals("THIS IS LOCATION OF MEASUREMENT", obs1.getBodySite().getText());
        Assert.assertEquals("THIS IS LOCATION OF MEASUREMENT", obs2.getBodySite().getText());
        Assert.assertEquals("THIS IS LOCATION OF MEASUREMENT", obs3.getBodySite().getText());

        Assert.assertEquals("at0025", obs1.getBodySite().getCodingFirstRep().getCode());
        Assert.assertEquals("local", obs1.getBodySite().getCodingFirstRep().getSystem());
        Assert.assertEquals("at0025", obs2.getBodySite().getCodingFirstRep().getCode());
        Assert.assertEquals("local", obs2.getBodySite().getCodingFirstRep().getSystem());
        Assert.assertEquals("at0025", obs3.getBodySite().getCodingFirstRep().getCode());
        Assert.assertEquals("local", obs3.getBodySite().getCodingFirstRep().getSystem());

        Assert.assertTrue(Arrays.asList(obs1, obs3).stream().allMatch(o -> o.getComponent().size() == 3)); // it includes interpretation component
        Assert.assertTrue(Arrays.asList(obs2).stream().allMatch(o -> o.getComponent().size() == 2));

        Assert.assertTrue(Arrays.asList(obs2).stream()
                .allMatch(o -> o.getComponent().stream()
                        .allMatch(com -> com.getCode() != null
                                && com.getCode().getCoding().size() == 1
                                && (com.getCode().getCodingFirstRep().getCode().equals("8480-6") || com.getCode().getCodingFirstRep().getCode().equals("8462-4")))));

        Assert.assertTrue(Arrays.asList(obs1, obs3).stream()
                .allMatch(o -> o.getComponent().stream()
                        .allMatch(com -> !com.getInterpretationFirstRep().isEmpty() || (com.getCode() != null
                                && com.getCode().getCoding().size() == 1
                                && (com.getCode().getCodingFirstRep().getCode().equals("8480-6") || com.getCode().getCodingFirstRep().getCode().equals("8462-4"))))));

        Assert.assertTrue(obs1.getComponent().stream().anyMatch(comp -> "This is interpreted as 0th clin interpretation".equals(comp.getInterpretationFirstRep().getText())));
        Assert.assertTrue(obs3.getComponent().stream().anyMatch(comp -> "This is interpreted as 2th clin interpretation".equals(comp.getInterpretationFirstRep().getText())));

        final Quantity firstDiastolic = obs1.getComponent().stream()
                .filter(comp -> comp.getCode().getCodingFirstRep().getCode().equals("8462-4"))
                .map(Observation.ObservationComponentComponent::getValueQuantity)
                .findFirst()
                .orElse(null);
        Assert.assertEquals("501.0", firstDiastolic.getValue().toPlainString());

        final Quantity firstSystolic = obs1.getComponent().stream()
                .filter(comp -> comp.getCode().getCodingFirstRep().getCode().equals("8480-6"))
                .map(Observation.ObservationComponentComponent::getValueQuantity)
                .findFirst()
                .orElse(null);
        Assert.assertEquals("500.0", firstSystolic.getValue().toPlainString());

        final Quantity secondDiastolic = obs2.getComponent().stream()
                .filter(comp -> comp.getCode().getCodingFirstRep().getCode().equals("8462-4"))
                .map(Observation.ObservationComponentComponent::getValueQuantity)
                .findFirst()
                .orElse(null);
        Assert.assertEquals("502.0", secondDiastolic.getValue().toPlainString());

        final Quantity secondSystolic = obs2.getComponent().stream()
                .filter(comp -> comp.getCode().getCodingFirstRep().getCode().equals("8480-6"))
                .map(Observation.ObservationComponentComponent::getValueQuantity)
                .findFirst()
                .orElse(null);
        Assert.assertEquals("600.0", secondSystolic.getValue().toPlainString());

        final Quantity thirdDiastolic = obs3.getComponent().stream()
                .filter(comp -> comp.getCode().getCodingFirstRep().getCode().equals("8462-4"))
                .map(Observation.ObservationComponentComponent::getValueQuantity)
                .findFirst()
                .orElse(null);
        Assert.assertEquals("503.0", thirdDiastolic.getValue().toPlainString());

        final Quantity thirdSystolic = obs3.getComponent().stream()
                .filter(comp -> comp.getCode().getCodingFirstRep().getCode().equals("8480-6"))
                .map(Observation.ObservationComponentComponent::getValueQuantity)
                .findFirst()
                .orElse(null);
        Assert.assertEquals("700.0", thirdSystolic.getValue().toPlainString());

        // assert hardcoded
        Assert.assertTrue(Stream.of(obs1, obs2, obs3).allMatch(obs -> obs.getPerformerFirstRep().getDisplay().equals("John Doe")));
    }

    @Test
    public void bloodPressureToFhir() throws IOException {
        final FhirConnectContext context = getContext("/simple-blood-pressure.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/Blood Pressure.opt");
        final WebTemplate growthChartOptTemplate = new OPTParser(operationalTemplate).parse();
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat("/blood-pressure_flat.json"), growthChartOptTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);

        assertBloodPressureFhir(bundle);
    }

    @Test
    public void growthChartToFhir() throws IOException {
        final FhirConnectContext context = getContext("/example-002-growth-chart.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/Growth chart.opt");
        final WebTemplate growthChartOptTemplate = new OPTParser(operationalTemplate).parse();
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat("/growth_chart_flat.json"), growthChartOptTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);
        Assert.assertEquals(12, bundle.getEntry().size());
        // 3x weight
        // 3x height
        // 3x bmi
        // 3x head

        List<Observation> weights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "weight".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, weights.size());
        final Observation firstWeight = weights.stream().filter(e -> "2022-02-03T04:05:06".equals(openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation secondWeight = weights.stream().filter(e -> "2022-02-04T04:05:06".equals(openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation thirdWeight = weights.stream().filter(e -> "2022-02-05T04:05:06".equals(openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        Assert.assertEquals("501.0", firstWeight.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("kg", firstWeight.getValueQuantity().getUnit());
        Assert.assertEquals("502.0", secondWeight.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("t", secondWeight.getValueQuantity().getUnit());
        Assert.assertEquals("503.0", thirdWeight.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("mm", thirdWeight.getValueQuantity().getUnit());
        Assert.assertEquals("body_weightLorem ipsum0", firstWeight.getNoteFirstRep().getText());
        Assert.assertEquals("body_weightLorem ipsum1", secondWeight.getNoteFirstRep().getText());
        Assert.assertEquals("body_weightLorem ipsum2", thirdWeight.getNoteFirstRep().getText());

        List<Observation> heights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "height".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, heights.size());

        List<Observation> bmis = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "bmi".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, bmis.size());

        List<Observation> heads = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "head_circumference".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, heads.size());
        final Observation firstHead = heads.stream().filter(e -> "2023-02-03T04:05:06".equals(openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation secondHead = heads.stream().filter(e -> "2023-02-04T04:05:06".equals(openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation thirdHead = heads.stream().filter(e -> "2023-02-05T04:05:06".equals(openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        Assert.assertEquals("50.0", firstHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("cm", firstHead.getValueQuantity().getUnit());
        Assert.assertEquals("51.0", secondHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("mm", secondHead.getValueQuantity().getUnit());
        Assert.assertEquals("52.0", thirdHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("m", thirdHead.getValueQuantity().getUnit());
    }

    @Test
    public void medicationOrderToFhir() throws IOException {
        final FhirConnectContext context = getContext("/medication-order.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/medication order.opt");
        final WebTemplate growthChartOptTemplate = new OPTParser(operationalTemplate).parse();
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat("/medication_order_flat.json"), growthChartOptTemplate);
        final Bundle createdResources = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);
        Assert.assertEquals(2, createdResources.getEntry().size());
        final MedicationRequest medicationRequestOne = (MedicationRequest) createdResources.getEntry().stream()
                .map(res -> (MedicationRequest) res.getResource())
                .filter(res -> res.getNote().stream().anyMatch(note -> note.getText().startsWith("Additional instruction on one")))
                .findFirst()
                .orElse(null);
        Assert.assertEquals("Lorem ipsum0", ((Medication) medicationRequestOne.getMedicationReference().getResource()).getCode().getText());
        Assert.assertEquals("21.0", ((Quantity) medicationRequestOne.getDosageInstructionFirstRep().getDoseAndRateFirstRep().getDose()).getValue().toPlainString());
        Assert.assertEquals("mm", ((Quantity) medicationRequestOne.getDosageInstructionFirstRep().getDoseAndRateFirstRep().getDose()).getUnit());
        Assert.assertEquals(2, medicationRequestOne.getNote().size());
        Assert.assertTrue(medicationRequestOne.getNote().stream().anyMatch(note -> note.getText().equals("Additional instruction on one first")));
        Assert.assertTrue(medicationRequestOne.getNote().stream().anyMatch(note -> note.getText().equals("Additional instruction on one second")));
        Assert.assertNull(medicationRequestOne.getAuthoredOn());

        Assert.assertEquals("at0067", medicationRequestOne.getDosageInstruction().get(0).getAdditionalInstructionFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("local", medicationRequestOne.getDosageInstruction().get(0).getAdditionalInstructionFirstRep().getCodingFirstRep().getSystem());

        final MedicationRequest medicationRequestTwo = (MedicationRequest) createdResources.getEntry().stream()
                .map(res -> (MedicationRequest) res.getResource())
                .filter(res -> res.getNote().stream().anyMatch(note -> note.getText().startsWith("Additional instruction on two")))
                .findFirst()
                .orElse(null);
        Assert.assertEquals("Lorem ipsum1", ((Medication) medicationRequestTwo.getMedicationReference().getResource()).getCode().getText());
        Assert.assertTrue(medicationRequestTwo.getDosageInstruction().isEmpty());
        Assert.assertEquals(3, medicationRequestTwo.getNote().size());
        Assert.assertTrue(medicationRequestTwo.getNote().stream().anyMatch(note -> note.getText().equals("Additional instruction on two first")));
        Assert.assertTrue(medicationRequestTwo.getNote().stream().anyMatch(note -> note.getText().equals("Additional instruction on two second")));
        Assert.assertTrue(medicationRequestTwo.getNote().stream().anyMatch(note -> note.getText().equals("Additional instruction on two third")));

        Assert.assertEquals("2022-02-03T04:05:06", openFhirMapperUtils.dateTimeToString(medicationRequestTwo.getAuthoredOn()));
    }

    @Test
    public void joinValuesThatAreOne() {
        final List<String> toJoin = Arrays.asList(
                "growth_chart/body_weight/any_event:1/weight|unit",
                "growth_chart/body_weight/any_event:1/comment",
                "growth_chart/body_weight/any_event:1/state_of_dress|code",
                "growth_chart/body_weight/any_event:1/state_of_dress|terminology",
                "growth_chart/body_weight/any_event:1/state_of_dress|value",
                "growth_chart/body_weight/any_event:1/confounding_factors:0",
                "growth_chart/body_weight/any_event:1/time",
                "growth_chart/body_weight/any_event:2/weight|unit",
                "growth_chart/body_weight/any_event:2/weight|magnitude",
                "growth_chart/body_weight/any_event:2/comment",
                "growth_chart/body_weight/any_event:2/state_of_dress|code",
                "growth_chart/body_weight/any_event:2/state_of_dress|value",
                "growth_chart/body_weight/any_event:2/state_of_dress|terminology",
                "growth_chart/body_weight/any_event:2/confounding_factors:0",
                "growth_chart/body_weight/any_event:2/time",
                "growth_chart/body_weight/any_event:2/width",
                "growth_chart/body_weight/any_event:2/math_function|terminology",
                "growth_chart/body_weight/any_event:2/math_function|code",
                "growth_chart/body_weight/any_event:2/math_function|value",
                "growth_chart/body_weight/language|code",
                "growth_chart/body_weight/language|terminology",
                "growth_chart/body_weight/encoding|code",
                "growth_chart/body_weight/encoding|terminology",
                "growth_chart/body_weight/_work_flow_id|id",
                "growth_chart/body_weight/_work_flow_id|id_scheme",
                "growth_chart/body_weight/_work_flow_id|namespace",
                "growth_chart/body_weight/_work_flow_id|type",
                "growth_chart/body_weight/_guideline_id|id"
        );
        final Map<String, List<String>> stringListMap = openEhrToFhir.joinValuesThatAreOne(toJoin);
        Assert.assertEquals(3, stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").size());
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|code", stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(0));
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|terminology", stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(1));
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|value", stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(2));
        Assert.assertEquals(3, stringListMap.get("growth_chart/body_weight/any_event:2/math_function").size());
        Assert.assertEquals(2, stringListMap.get("growth_chart/body_weight/encoding").size());
        Assert.assertEquals(1, stringListMap.get("growth_chart/body_weight/any_event:1/confounding_factors:0").size());
    }

    @Test
    public void joinValuesThatAreOne_dots() {
        final List<String> toJoin = Arrays.asList("stationärer_versorgungsfall/context/start_time",
                "stationärer_versorgungsfall/context/setting|terminology",
                "stationärer_versorgungsfall/context/setting|code",
                "stationärer_versorgungsfall/context/setting|value",
                "stationärer_versorgungsfall/context/_end_time",
                "stationärer_versorgungsfall/context/_health_care_facility|name",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|code",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|code",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|code",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|code",
                "stationärer_versorgungsfall/aufnahmedaten/kennung_vor_der_aufnahme",
                "stationärer_versorgungsfall/aufnahmedaten/datum_uhrzeit_der_aufnahme",
                "stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/campus"
        );

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive("random")));

        final String testingPath = "$openEhrArchetype.aufnahmedaten.aufnahmegrund_-_1\\._und_2\\._stelle";

        final String prepared = openFhirStringUtils.prepareOpenEhrSyntax(testingPath, "stationärer_versorgungsfall");

        final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(prepared);
        final List<String> matchingEntries = openEhrToFhir.getAllEntriesThatMatch(withRegex, flatJsonObject);
        Assert.assertEquals(3, matchingEntries.size());

    }

    private String getFlat(final String path) throws IOException {
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        return IOUtils.toString(inputStream);
    }

    private FhirConnectContext getContext(final String path) {
        final Yaml yaml = new Yaml();
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        return yaml.loadAs(inputStream, FhirConnectContext.class);
    }

    private OPERATIONALTEMPLATE getOperationalTemplate(final String path) {
        try {
            return TemplateDocument.Factory.parse(this.getClass().getResourceAsStream(path)).getTemplate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}