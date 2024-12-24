package com.medblocks.openfhir.toopenehr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.TestOpenFhirMappingContext;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.util.FhirConnectModelMerger;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenEhrPopulator;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class FhirToOpenEhrTest {
    final FhirPathR4 fhirPathR4 = new FhirPathR4(FhirContext.forR4());
    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();
    final TestOpenFhirMappingContext repo = new TestOpenFhirMappingContext(fhirPathR4, openFhirStringUtils, fhirConnectModelMerger);
    FhirToOpenEhr fhirToOpenEhr;

    @Before
    public void init() {
        fhirToOpenEhr = new FhirToOpenEhr(fhirPathR4,
                new OpenFhirStringUtils(),
                new FlatJsonUnmarshaller(),
                new Gson(),
                new OpenEhrRmWorker(openFhirStringUtils),
                openFhirStringUtils,
                repo,
                new OpenEhrCachedUtils(null),
                new OpenFhirMapperUtils(),
                new OpenEhrPopulator(new OpenFhirMapperUtils()));
    }


    @Test
    public void testBloodPressure_flat() {
        final FhirConnectContext context = getContext("/simple-blood-pressure.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());

        final JsonObject flat = fhirToOpenEhr.fhirToFlatJsonObject(context, testBloodPressureObservation(), getOperationalTemplate("/Blood Pressure.opt"));

        Assert.assertEquals(11, flat.size());
        Assert.assertEquals("456.0", flat.get("blood_pressure/blood_pressure/any_event:0/systolic|magnitude").getAsString());
        Assert.assertEquals("mm[Hg]", flat.get("blood_pressure/blood_pressure/any_event:0/systolic|unit").getAsString());
        Assert.assertEquals("789.0", flat.get("blood_pressure/blood_pressure/any_event:0/diastolic|magnitude").getAsString());
        Assert.assertEquals("mm[Hg2]", flat.get("blood_pressure/blood_pressure/any_event:0/diastolic|unit").getAsString());
        Assert.assertEquals("at00256", flat.get("blood_pressure/blood_pressure/location_of_measurement|code").getAsString());
        Assert.assertEquals("remotey", flat.get("blood_pressure/blood_pressure/location_of_measurement|terminology").getAsString());
        Assert.assertEquals("description", flat.get("blood_pressure/blood_pressure/any_event:0/comment").getAsString());
        Assert.assertEquals("interpretation text", flat.get("blood_pressure/blood_pressure/any_event:0/clinical_interpretation").getAsString());

        // assert hardcoded paths
        Assert.assertEquals("at1000", flat.get("blood_pressure/blood_pressure/a24_hour_average/position|code").getAsString());
        Assert.assertEquals("confounding factor", flat.get("blood_pressure/blood_pressure/a24_hour_average/confounding_factors").getAsString());
    }

    @Test
    public void testBloodPressure_RM() {
        final FhirConnectContext context = getContext("/simple-blood-pressure.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context,
                testBloodPressureObservation(), getOperationalTemplate("/Blood Pressure.opt"));
        final String systolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value";
        final String diastolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0005]/value";

        final String interpretationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at1059]";
        final String descriptionPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0033]";
        final String locationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/protocol[at0011]/items[at0014]";

        Assert.assertEquals(Double.valueOf(456), ((DvQuantity) composition.itemAtPath(systolicPath)).getMagnitude());
        Assert.assertEquals("mm[Hg]", ((DvQuantity) composition.itemAtPath(systolicPath)).getUnits());
        Assert.assertEquals(Double.valueOf(789), ((DvQuantity) composition.itemAtPath(diastolicPath)).getMagnitude());
        Assert.assertEquals("mm[Hg2]", ((DvQuantity) composition.itemAtPath(diastolicPath)).getUnits());
        Assert.assertEquals("at00256", ((DvCodedText) ((Element) composition.itemAtPath(locationPath)).getValue()).getDefiningCode().getCodeString());
        Assert.assertEquals("remotey", ((DvCodedText) ((Element) composition.itemAtPath(locationPath)).getValue()).getDefiningCode().getTerminologyId().getValue());
        Assert.assertEquals("interpretation text", ((DvText) ((Element) composition.itemAtPath(interpretationPath)).getValue()).getValue());
        Assert.assertEquals("description", ((DvText) ((Element) composition.itemAtPath(descriptionPath)).getValue()).getValue());
    }

    @Test
    public void medicationOrder_flat() {
        final OpenFhirFhirConnectModelMapper mapper = getMapper("/medication-order.model.yml");
        final FhirConnectContext context = getContext("/medication-order.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final List<FhirToOpenEhrHelper> helpers = new ArrayList<>();
        final String templateId = context.getContext().getTemplateId().toLowerCase().replace(" ", "_");
        final ArrayList<FhirToOpenEhrHelper> coverHelpers = new ArrayList<>();
        fhirToOpenEhr.createHelpers(mapper.getOpenEhrConfig().getArchetype(), mapper, templateId, templateId, mapper.getMappings(), null, helpers, coverHelpers, true, false, false);
        Assert.assertEquals("medication_order/medication_order/order/medication_item", findOpenEhrPathByFhirPath(new ArrayList<>(helpers), "MedicationRequest.medication.resolve().code.text"));
        Assert.assertEquals("medication_order/medication_order/order/therapeutic_direction/dosage/dose_amount/quantity_value", findOpenEhrPathByFhirPath(new ArrayList<>(helpers), "MedicationRequest.dosageInstruction.doseAndRate.dose"));
    }

    private String findOpenEhrPathByFhirPath(final List<FhirToOpenEhrHelper> helpers, final String fhirPath) {
        for (FhirToOpenEhrHelper helper : helpers) {
            final String found = findOpenEhrPathByFhirPath(helper, fhirPath);
            if(found != null) {
                return found;
            }
        }
        return null;
    }
    private String findOpenEhrPathByFhirPath(final FhirToOpenEhrHelper helper, final String fhirPath) {
        if(fhirPath.equals(helper.getFhirPath())) {
            return helper.getOpenEhrPath();
        }
        final String toCheckFurther = StringUtils.isEmpty(helper.getFhirPath()) ? fhirPath : fhirPath.replace(helper.getFhirPath()+".", "");
        if(helper.getFhirToOpenEhrHelpers() != null) {
            for (FhirToOpenEhrHelper innerHelper : helper.getFhirToOpenEhrHelpers()) {
                return findOpenEhrPathByFhirPath(innerHelper, toCheckFurther);
            }
        }
        return null;
    }

    @Test
    public void testFlattenning() {
        final Patient patient = new Patient();
        patient.addName(new HumanName().setUse(HumanName.NameUse.MAIDEN).setText("text0").setFamily("family0").addSuffix("sufix00").addSuffix("sufix01").addGiven("given0_0").addGiven("given0_1"));
        patient.addName(new HumanName().setUse(HumanName.NameUse.NICKNAME).setText("text1").setFamily("family1").addSuffix("sufix10").addSuffix("suffix11").addGiven("given1_0").addGiven("given1_1"));
        final String fhirToOpenEhrHelperS = "{\n" +
                "  \"fhirPath\": \"Patient.name\",\n" +
                "  \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "  \"openEhrPath\": \"person/personendaten/person/geburtsname[n]\",\n" +
                "  \"openEhrType\": \"NONE\",\n" +
                "  \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "  \"multiple\": false,\n" +
                "  \"fhirToOpenEhrHelpers\": [\n" +
                "    {\n" +
                "      \"fhirPath\": \"given\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/vollständiger_name\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"use.code\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/namensart\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"family\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/familienname-nachname\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"prefix\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/familienname-vorsatzwort\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"suffix\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/suffix[n]\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        final FhirToOpenEhrHelper helper = new Gson().fromJson(fhirToOpenEhrHelperS, FhirToOpenEhrHelper.class);
        final JsonObject flattenning = new JsonObject();
        fhirToOpenEhr.addDataPoints(helper, flattenning, patient);
        Assert.assertEquals("given0_0", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/vollständiger_name").getAsString());
        Assert.assertEquals("given1_0", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/vollständiger_name").getAsString());
        Assert.assertEquals("family1", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/familienname-nachname").getAsString());
        Assert.assertEquals("family0", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/familienname-nachname").getAsString());
        Assert.assertEquals("sufix00", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/suffix:0").getAsString());
        Assert.assertEquals("sufix01", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/suffix:1").getAsString());
        Assert.assertEquals("sufix10", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/suffix:0").getAsString());
        Assert.assertEquals("suffix11", flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/suffix:1").getAsString());
    }

    @Test
    public void medicationOrder_RM() {
        final FhirConnectContext context = getContext("/medication-order.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final Bundle bundle = testMedicationMedicationRequestBundle();
        fhirPathR4.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });
        final List<Base> medicationText = fhirPathR4.evaluate(bundle, "Bundle.entry.resource.ofType(MedicationRequest).medication.resolve().code.text", Base.class);
        Assert.assertEquals("medication text", medicationText.get(0).toString());

        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, getOperationalTemplate("/medication order.opt"));
        final String medicationTextPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[at0070]/value";
        final String doseAmountPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[openEHR-EHR-CLUSTER.dosage.v1]/items[at0144]/value";
        Assert.assertEquals("medication text", ((DvText) composition.itemAtPath(medicationTextPath)).getValue());
        Assert.assertEquals(Double.valueOf(111.0), ((DvQuantity) composition.itemAtPath(doseAmountPath)).getMagnitude());
    }

    @Test
    public void growthChart_flat() {
        final FhirConnectContext context = getContext("/example-002-growth-chart.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final Bundle bundle = growthChartTestBundle();
        fhirPathR4.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });


        final JsonObject flat = fhirToOpenEhr.fhirToFlatJsonObject(context, bundle, getOperationalTemplate("/Growth chart.opt"));

        Assert.assertEquals(33, flat.size());


        Assert.assertEquals("54.0", flat.get("growth_chart/head_circumference/any_event:0/head_circumference|magnitude").getAsString());
        Assert.assertEquals("cm", flat.get("growth_chart/head_circumference/any_event:0/head_circumference|unit").getAsString());
        Assert.assertEquals("2020-10-07T01:00:00", flat.get("growth_chart/head_circumference/any_event:0/time").getAsString());
        Assert.assertEquals("2020-10-07T02:00:00", flat.get("growth_chart/head_circumference/any_event:1/time").getAsString());
        Assert.assertEquals("2020-10-07T03:00:00", flat.get("growth_chart/head_circumference/any_event:2/time").getAsString());


        // ok
        Assert.assertEquals("20.0", flat.get("growth_chart/body_mass_index/any_event:0/body_mass_index|magnitude").getAsString());
        Assert.assertEquals("kg/m2", flat.get("growth_chart/body_mass_index/any_event:0/body_mass_index|unit").getAsString());
        Assert.assertEquals("2020-10-07T00:00:00", flat.get("growth_chart/body_mass_index/any_event:0/time").getAsString());
        Assert.assertEquals("21.0", flat.get("growth_chart/body_mass_index/any_event:1/body_mass_index|magnitude").getAsString());
        Assert.assertEquals("kg/m2", flat.get("growth_chart/body_mass_index/any_event:1/body_mass_index|unit").getAsString());
        Assert.assertEquals("2020-10-07T01:00:00", flat.get("growth_chart/body_mass_index/any_event:1/time").getAsString());
        Assert.assertEquals("22.0", flat.get("growth_chart/body_mass_index/any_event:2/body_mass_index|magnitude").getAsString());
        Assert.assertEquals("kg/m2", flat.get("growth_chart/body_mass_index/any_event:2/body_mass_index|unit").getAsString());
        Assert.assertEquals("2020-10-07T02:00:00", flat.get("growth_chart/body_mass_index/any_event:2/time").getAsString());

        // ok
        Assert.assertEquals("180.0", flat.get("growth_chart/height_length/any_event:0/height_length|magnitude").getAsString());
        Assert.assertEquals("cm", flat.get("growth_chart/height_length/any_event:0/height_length|unit").getAsString());
        Assert.assertEquals("200.0", flat.get("growth_chart/height_length/any_event:1/height_length|magnitude").getAsString());
        Assert.assertEquals("cm", flat.get("growth_chart/height_length/any_event:1/height_length|unit").getAsString());
        Assert.assertEquals("220.0", flat.get("growth_chart/height_length/any_event:2/height_length|magnitude").getAsString());
        Assert.assertEquals("m", flat.get("growth_chart/height_length/any_event:2/height_length|unit").getAsString());

        // ok
        Assert.assertEquals("65.0", flat.get("growth_chart/body_weight/any_event:0/weight|magnitude").getAsString());
        Assert.assertEquals("kg", flat.get("growth_chart/body_weight/any_event:0/weight|unit").getAsString());
        Assert.assertEquals("66.0", flat.get("growth_chart/body_weight/any_event:1/weight|magnitude").getAsString());
        Assert.assertEquals("kg", flat.get("growth_chart/body_weight/any_event:1/weight|unit").getAsString());
        Assert.assertEquals("2020-10-07T01:00:00", flat.get("growth_chart/body_weight/any_event:1/time").getAsString());
        Assert.assertEquals("68.0", flat.get("growth_chart/body_weight/any_event:2/weight|magnitude").getAsString());
        Assert.assertEquals("kg", flat.get("growth_chart/body_weight/any_event:2/weight|unit").getAsString());
        Assert.assertEquals("2020-10-07T03:00:00", flat.get("growth_chart/body_weight/any_event:2/time").getAsString());
        Assert.assertEquals("just too fat", flat.get("growth_chart/body_weight/any_event:2/comment").getAsString());
        Assert.assertNull(flat.get("growth_chart/body_weight/any_event:0/time"));
    }

    @Test
    public void testReplacePattern() {
        final String original = "kds_prozedur/procedure/seitenlokalisation[n]";
        final String newParent = "kds_prozedur/procedure/name_der_prozedur";
        final String s = openFhirStringUtils.replacePattern(original, newParent);
        Assert.assertEquals("kds_prozedur/procedure/seitenlokalisation[n]", s);

        final String original1 = "kds_prozedur/procedure[n]/seitenlokalisation[n]";
        final String newParent1 = "kds_prozedur/procedure[n]/name_der_prozedur/abc";
        final String s1 = openFhirStringUtils.replacePattern(original1, newParent1);
        Assert.assertEquals("kds_prozedur/procedure[n]/seitenlokalisation[n]", s1);
    }

    @Test
    public void growthChart_RM() {
        final FhirConnectContext context = getContext("/example-002-growth-chart.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final Bundle bundle = growthChartTestBundle();
        fhirPathR4.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });


        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, getOperationalTemplate("/Growth chart.opt"));
        final String weightPath = "/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value";
        final String weightCommentPath = "/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0024]/value";

        final String heightPath = "/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value";

        final String bmiPath = "/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value";
        final String headCircumferencePath = "/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0001]/events[at0010]/data[at0003]/items[at0004]/value";


        final List<Object> weightComment = composition.itemsAtPath(weightCommentPath);
        final List<Object> weights = composition.itemsAtPath(weightPath);
        final List<Object> heights = composition.itemsAtPath(heightPath);
        final List<Object> bmis = composition.itemsAtPath(bmiPath);
        final List<Object> heads = composition.itemsAtPath(headCircumferencePath);

        // weight
        final Double zerothWeightMagnitude = ((DvQuantity) weights.get(0)).getMagnitude();
        final String zerothWeightUnit = ((DvQuantity) weights.get(0)).getUnits();
        Assert.assertEquals(Double.valueOf(65), zerothWeightMagnitude);
        Assert.assertEquals("kg", zerothWeightUnit);
        Assert.assertEquals("just too fat", ((DvText) weightComment.get(0)).getValue());

        final Double firsthWeightMagnitude = ((DvQuantity) weights.get(1)).getMagnitude();
        final String firsthWeightUnit = ((DvQuantity) weights.get(1)).getUnits();
        Assert.assertEquals(Double.valueOf(66), firsthWeightMagnitude);
        Assert.assertEquals("kg", firsthWeightUnit);

        final Double secondWeightMagnitude = ((DvQuantity) weights.get(2)).getMagnitude();
        final String secondWeightUnit = ((DvQuantity) weights.get(2)).getUnits();
        Assert.assertEquals(Double.valueOf(68), secondWeightMagnitude);
        Assert.assertEquals("kg", secondWeightUnit);

        // height
        final Double zerothHeightMagnitude = ((DvQuantity) heights.get(0)).getMagnitude();
        final String zerothHeightUnit = ((DvQuantity) heights.get(0)).getUnits();
        Assert.assertEquals(Double.valueOf(180), zerothHeightMagnitude);
        Assert.assertEquals("cm", zerothHeightUnit);

        final Double firsthHeightMagnitude = ((DvQuantity) heights.get(1)).getMagnitude();
        final String firsthHeightUnit = ((DvQuantity) heights.get(1)).getUnits();
        Assert.assertEquals(Double.valueOf(200), firsthHeightMagnitude);
        Assert.assertEquals("cm", firsthHeightUnit);

        final Double secondHeightMagnitude = ((DvQuantity) heights.get(2)).getMagnitude();
        final String secondHeightUnit = ((DvQuantity) heights.get(2)).getUnits();
        Assert.assertEquals(Double.valueOf(220), secondHeightMagnitude);
        Assert.assertEquals("m", secondHeightUnit);

        // bmi
        final Double zerothBmiMagnitude = ((DvQuantity) bmis.get(0)).getMagnitude();
        final String zerothBmiUnit = ((DvQuantity) bmis.get(0)).getUnits();
        Assert.assertEquals(Double.valueOf(20), zerothBmiMagnitude);
        Assert.assertEquals("kg/m2", zerothBmiUnit);

        final Double firsthBmiMagnitude = ((DvQuantity) bmis.get(1)).getMagnitude();
        final String firsthBmiUnit = ((DvQuantity) bmis.get(1)).getUnits();
        Assert.assertEquals(Double.valueOf(21), firsthBmiMagnitude);
        Assert.assertEquals("kg/m2", firsthBmiUnit);

        final Double secondBmiMagnitude = ((DvQuantity) bmis.get(2)).getMagnitude();
        final String secondBmiUnit = ((DvQuantity) bmis.get(2)).getUnits();
        Assert.assertEquals(Double.valueOf(22), secondBmiMagnitude);
        Assert.assertEquals("kg/m2", secondBmiUnit);

        // head
        Assert.assertEquals(Double.valueOf(54), ((DvQuantity) heads.get(0)).getMagnitude());
        Assert.assertEquals("cm", ((DvQuantity) heads.get(0)).getUnits());
    }

    @Test
    public void replaceMultipleOccurrenceSyntax_singles() {
        String openEhrPath = "medication_order/medication_order/order[n]/medication_item";
        final JsonObject finalFlat = new JsonObject();
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING", Arrays.asList(new StringType("haa")), finalFlat);
        Assert.assertEquals("medication_order/medication_order/order:0/medication_item", new ArrayList<>(finalFlat.entrySet()).get(0).getKey());

        openEhrPath = "medication_order/medication_order/order[n]/medication_item[n]";
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING", Arrays.asList(new StringType("haa")), finalFlat);
        Assert.assertEquals("medication_order/medication_order/order:0/medication_item:0", new ArrayList<>(finalFlat.entrySet()).get(1).getKey());
    }

    @Test
    public void replaceMultipleOccurrenceSyntax_multiples() {
        String openEhrPath = "medication_order/medication_order/order[n]/medication_item";
        JsonObject finalFlat = new JsonObject();
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING", Arrays.asList(new StringType("1"), new StringType("2")), finalFlat);
        Assert.assertEquals(2, new ArrayList<>(finalFlat.entrySet()).size());
        Assert.assertEquals("1", finalFlat.get("medication_order/medication_order/order:0/medication_item").getAsString());
        Assert.assertEquals("2", finalFlat.get("medication_order/medication_order/order:1/medication_item").getAsString());

        finalFlat = new JsonObject();
        openEhrPath = "medication_order/medication_order/order[n]/medication_item[n]";
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING", Arrays.asList(new StringType("1"), new StringType("2"), new StringType("3")), finalFlat);
        Assert.assertEquals(3, new ArrayList<>(finalFlat.entrySet()).size());
        Assert.assertEquals("1", finalFlat.get("medication_order/medication_order/order:0/medication_item:0").getAsString());
        Assert.assertEquals("2", finalFlat.get("medication_order/medication_order/order:0/medication_item:1").getAsString());
        Assert.assertEquals("3", finalFlat.get("medication_order/medication_order/order:0/medication_item:2").getAsString());

        finalFlat = new JsonObject();
        openEhrPath = "medication_order/medication_order[n]/order[n]/medication_item[n]";
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING", Arrays.asList(new StringType("1"), new StringType("2"), new StringType("3")), finalFlat);
        Assert.assertEquals(3, new ArrayList<>(finalFlat.entrySet()).size());
        Assert.assertEquals("1", finalFlat.get("medication_order/medication_order:0/order:0/medication_item:0").getAsString());
        Assert.assertEquals("2", finalFlat.get("medication_order/medication_order:0/order:0/medication_item:1").getAsString());
        Assert.assertEquals("3", finalFlat.get("medication_order/medication_order:0/order:0/medication_item:2").getAsString());
    }

    @Test
    public void removeTypesTest() {
        String withType = "medication_order/medication_order[TYPE:INSTRUCTION]/order[n][TYPE:ACTIVITY]/medication_item[TYPE:ELEMENT]";
        String removed = openFhirStringUtils.removeTypes(withType);
        Assert.assertEquals("medication_order/medication_order/order[n]/medication_item", removed);

        withType = "medication_order/medication_order/order[n]/medication_item";
        removed = openFhirStringUtils.removeTypes(withType);
        Assert.assertEquals("medication_order/medication_order/order[n]/medication_item", removed);

        withType = "medication_order/medication_order[TYPE:INSTRUCTION]/order[0][TYPE:ACTIVITY]/medication_item[1][TYPE:ELEMENT]";
        removed = openFhirStringUtils.removeTypes(withType);
        Assert.assertEquals("medication_order/medication_order/order[0]/medication_item[1]", removed);
    }

    @Test
    public void getLatTypeTest() {
        String withType = "medication_order/medication_order[TYPE:INSTRUCTION]/order[n][TYPE:ACTIVITY]/medication_item[TYPE:ELEMENT]";
        String lastType = openFhirStringUtils.getLastType(withType);

        Assert.assertEquals("ELEMENT", lastType);
    }

    public static Bundle testMedicationMedicationRequestBundle() {
        final Bundle bundle = new Bundle();
        final Bundle.BundleEntryComponent medicationEntry = new Bundle.BundleEntryComponent();
        final Medication medication = new Medication();
        final String medicationUuid = UUID.randomUUID().toString();
        medication.setId(medicationUuid);
        medication.setCode(new CodeableConcept().setText("medication text"));
        medicationEntry.setResource(medication);
        medicationEntry.setFullUrl("Medication/" + medicationUuid);
        bundle.addEntry(medicationEntry);

        final Bundle.BundleEntryComponent medicationRequestEntry = new Bundle.BundleEntryComponent();
        final MedicationRequest medicationRequest = new MedicationRequest();
        final Dosage dosage = new Dosage();
        final Dosage.DosageDoseAndRateComponent doseAndRate = new Dosage.DosageDoseAndRateComponent();
        doseAndRate.setDose(new Quantity(111).setUnit("unit"));
        dosage.addDoseAndRate(doseAndRate);
        medicationRequest.addDosageInstruction(dosage);
        final Reference value = new Reference("Medication/" + medicationUuid);
        value.setResource(medication);
        medicationRequest.setMedication(value);
        medicationRequestEntry.setResource(medicationRequest);
        bundle.addEntry(medicationRequestEntry);
        return bundle;
    }

    public static Bundle growthChartTestBundle() {
        final Bundle bundle = new Bundle();
        addWeightEntry(bundle, 0, false, null);
        addWeightEntry(bundle, 1, true, null);
        addWeightEntry(bundle, 3, true, "just too fat");

        addHeightEntry(bundle, 0, false, "cm");
        addHeightEntry(bundle, 20, false, "cm");
        addHeightEntry(bundle, 30, true, "cm");
        addHeightEntry(bundle, 40, false, "m");

        addBmiEntry(bundle, 0);
        addBmiEntry(bundle, 1);
        addBmiEntry(bundle, 2);

        addHeadCircumferenceEntry(bundle, 1, false, true);
        addHeadCircumferenceEntry(bundle, 2, true, true);
        addHeadCircumferenceEntry(bundle, 3, true, true);
        addHeadCircumferenceEntry(bundle, 4, true, false);

        return bundle;
    }

    private static void addBmiEntry(final Bundle bundle, int index) {
        final Observation observation = new Observation();
        observation.addCategory(new CodeableConcept(new Coding(null, "bmi", null)));
        final Quantity value = new Quantity();
        value.setValue(20 + index);
        value.setUnit("kg/m2");
        observation.setValue(value);
        final CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding("firstThing", "ppoo", null));
        code.addCoding(new Coding("snomed", "aass", null));
        code.addCoding(new Coding("loinc", "39156-5", null));
        code.addCoding(new Coding("thirdThing", "xxyy", null));
        observation.setCode(code);
        final LocalDateTime localDateTime = LocalDateTime.of(2020, 10, 7, index, 0);
        observation.setEffective(new DateTimeType(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())));
        addEntry(bundle, observation);
    }

    private static void addHeadCircumferenceEntry(final Bundle bundle, int index, boolean noCode, final boolean addFinal) {
        final Observation observation = new Observation();
        observation.addCategory(new CodeableConcept(new Coding(null, "head_circumference", null)));
        final Quantity value = new Quantity();
        value.setValue(53 + index);
        value.setUnit("cm");
        if (addFinal) {
            observation.setStatus(Observation.ObservationStatus.FINAL);
        }
        observation.setValue(value);
        final CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding("loinc", "8287-5", null));
        observation.setCode(code);
        final LocalDateTime localDateTime = LocalDateTime.of(2020, 10, 7, index, 0);
        observation.setEffective(new DateTimeType(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())));
        addEntry(bundle, observation);
    }

    private static void addWeightEntry(final Bundle bundle, int index, boolean includeTime, final String comment) {
        final Observation observation = new Observation();
        observation.addCategory(new CodeableConcept(new Coding(null, "weight", null)));
        final Quantity value = new Quantity();
        value.setValue(65 + index);
        value.setUnit("kg");
        observation.setValue(value);
        final CodeableConcept code = new CodeableConcept();
        code.addCoding(new Coding("firstThing", "ppoo", null));
        code.addCoding(new Coding("snomed", "27113001", null));
        code.addCoding(new Coding("loinc", "29463-7", null));
        code.addCoding(new Coding("thirdThing", "xxyy", null));
        if (includeTime) {
            final LocalDateTime localDateTime = LocalDateTime.of(2020, 10, 7, index, 0);
            observation.setEffective(new DateTimeType(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())));
        }
        observation.setCode(code);
        if (comment != null) {
            observation.addNote(new Annotation().setText(comment));
        }
        addEntry(bundle, observation);
    }

    private static void addHeightEntry(final Bundle bundle, int index, boolean nonHeight, String unit) {
        final Observation observation = new Observation();
        observation.addCategory(new CodeableConcept(new Coding(null, "height", null)));
        final Quantity value = new Quantity();
        value.setValue(180 + index);
        value.setUnit(unit);
        observation.setValue(value);
        final CodeableConcept code = new CodeableConcept();
        if (!nonHeight) {
            code.addCoding(new Coding("firstThing", "ppoo", null));
            code.addCoding(new Coding("snomed", "yyssaa", null));
            code.addCoding(new Coding("loinc", "8302-2", null));
            code.addCoding(new Coding("thirdThing", "xxyy", null));
        } else {
            code.addCoding(new Coding("loinc", "not a height entry", null));
        }
        observation.setCode(code);
        addEntry(bundle, observation);
    }

    private static void addEntry(final Bundle bundle, final Resource resource) {
        final Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.setFullUrl("urn:uuid:" + UUID.randomUUID());
        entry.setResource(resource);
        bundle.addEntry(entry);
    }

    public static org.hl7.fhir.r4.model.Observation testBloodPressureObservation() {
        final org.hl7.fhir.r4.model.Observation resource = new org.hl7.fhir.r4.model.Observation();
        resource.setCode(new CodeableConcept().setText("description"));
        resource.setSubject(new Reference("Patient/123"));

        final org.hl7.fhir.r4.model.Observation.ObservationComponentComponent component1 = new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        component1.setValue(new Quantity(123));
        resource.addComponent(component1);

        final org.hl7.fhir.r4.model.Observation.ObservationComponentComponent component2 = new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        component2.setValue(new Quantity(456).setUnit("mm[Hg]"));
        resource.addComponent(component2);
        component2.setCode(new CodeableConcept().addCoding(new Coding("loinc", "8480-6", null)));

        final org.hl7.fhir.r4.model.Observation.ObservationComponentComponent component3 = new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        component3.setValue(new Quantity(789).setUnit("mm[Hg2]"));
        component3.setCode(new CodeableConcept().addCoding(new Coding("loinc", "8462-4", null)));

        resource.addComponent(component3);

        resource.addComponent(new Observation.ObservationComponentComponent().addInterpretation(new CodeableConcept().setText("interpretation text")));

        resource.setBodySite(new CodeableConcept(new Coding("remotey", "at00256", null)).setText("THIS IS LOCATION OF MEASUREMENT"));
        return resource;
    }

    private OpenFhirFhirConnectModelMapper getMapper(final String path) {
        final Representer representer = new Representer(new DumperOptions());
        representer.getPropertyUtils().setSkipMissingProperties(true);
        final Yaml yaml = new Yaml(representer);
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        return yaml.loadAs(inputStream, OpenFhirFhirConnectModelMapper.class);
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