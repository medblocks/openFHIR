package com.medblocks.openfhir.growthchart;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.GenericTest;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.Assert;
import org.junit.Test;

public class GrowthChartToOpenEhrTest extends GenericTest {

    final String MODEL_MAPPINGS = "/growth_chart/";
    final String CONTEXT_MAPPING = "/growth_chart/growth-chart.context.yml";
    final String HELPER_LOCATION = "/growth_chart/";
    final String OPT = "Growth chart.opt";

    private FhirPathR4 fhirPathR4 = new FhirPathR4(FhirContext.forR4());


    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }


    @Test
    public void growthChart_flat() {
        final Bundle bundle = growthChartTestBundle();
        fhirPathR4.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });


        final JsonObject flat = fhirToOpenEhr.fhirToFlatJsonObject(context, bundle, operationaltemplate);

        Assert.assertEquals(33, flat.size());


        Assert.assertEquals("54.0", flat.get("growth_chart/head_circumference/any_event:0/head_circumference|magnitude")
                .getAsString());
        Assert.assertEquals("cm", flat.get("growth_chart/head_circumference/any_event:0/head_circumference|unit")
                .getAsString());
        Assert.assertEquals("2020-10-07T01:00:00",
                            flat.get("growth_chart/head_circumference/any_event:0/time").getAsString());
        Assert.assertEquals("2020-10-07T02:00:00",
                            flat.get("growth_chart/head_circumference/any_event:1/time").getAsString());
        Assert.assertEquals("2020-10-07T03:00:00",
                            flat.get("growth_chart/head_circumference/any_event:2/time").getAsString());


        // ok
        Assert.assertEquals("20.0", flat.get("growth_chart/body_mass_index/any_event:0/body_mass_index|magnitude")
                .getAsString());
        Assert.assertEquals("kg/m2",
                            flat.get("growth_chart/body_mass_index/any_event:0/body_mass_index|unit").getAsString());
        Assert.assertEquals("2020-10-07T00:00:00",
                            flat.get("growth_chart/body_mass_index/any_event:0/time").getAsString());
        Assert.assertEquals("21.0", flat.get("growth_chart/body_mass_index/any_event:1/body_mass_index|magnitude")
                .getAsString());
        Assert.assertEquals("kg/m2",
                            flat.get("growth_chart/body_mass_index/any_event:1/body_mass_index|unit").getAsString());
        Assert.assertEquals("2020-10-07T01:00:00",
                            flat.get("growth_chart/body_mass_index/any_event:1/time").getAsString());
        Assert.assertEquals("22.0", flat.get("growth_chart/body_mass_index/any_event:2/body_mass_index|magnitude")
                .getAsString());
        Assert.assertEquals("kg/m2",
                            flat.get("growth_chart/body_mass_index/any_event:2/body_mass_index|unit").getAsString());
        Assert.assertEquals("2020-10-07T02:00:00",
                            flat.get("growth_chart/body_mass_index/any_event:2/time").getAsString());

        // ok
        Assert.assertEquals("180.0",
                            flat.get("growth_chart/height_length/any_event:0/height_length|magnitude").getAsString());
        Assert.assertEquals("cm", flat.get("growth_chart/height_length/any_event:0/height_length|unit").getAsString());
        Assert.assertEquals("200.0",
                            flat.get("growth_chart/height_length/any_event:1/height_length|magnitude").getAsString());
        Assert.assertEquals("cm", flat.get("growth_chart/height_length/any_event:1/height_length|unit").getAsString());
        Assert.assertEquals("220.0",
                            flat.get("growth_chart/height_length/any_event:2/height_length|magnitude").getAsString());
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
    public void growthChart_RM() {

        final Bundle bundle = growthChartTestBundle();
        fhirPathR4.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });


        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, bundle,
                                                                          operationaltemplate);
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

    private static void addHeadCircumferenceEntry(final Bundle bundle, int index, boolean noCode,
                                                  final boolean addFinal) {
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
            observation.setEffective(
                    new DateTimeType(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())));
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


}
