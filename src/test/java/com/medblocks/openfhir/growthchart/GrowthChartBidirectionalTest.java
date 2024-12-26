package com.medblocks.openfhir.growthchart;

import com.medblocks.openfhir.GenericTest;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

public class GrowthChartBidirectionalTest extends GenericTest {

    final String MODEL_MAPPINGS = "/growth_chart/";
    final String CONTEXT_MAPPING = "/growth_chart/growth-chart.context.yml";
    final String HELPER_LOCATION = "/growth_chart/";
    final String OPT = "Growth chart.opt";
    final String FLAT = "growth_chart_flat.json";

    private OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();


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
    public void growthChartToFhirToOpenEhr() throws IOException {
        // have a composition from config
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             webTemplate);

        // transform it to FHIR
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);

        // transform it back to openEHR
        final Composition rmComposition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationaltemplate);

        final String weightPath = "/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value";
        final String weightCommentPath = "/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0024]/value";

        final String heightPath = "/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value";

        final String bmiPath = "/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value";
        final String headCircumferencePath = "/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0001]/events[at0010]/data[at0003]/items[at0004]/value";


        final List<Object> weightComment = rmComposition.itemsAtPath(weightCommentPath);
        final List<Object> weights = rmComposition.itemsAtPath(weightPath);
        final List<Object> heights = rmComposition.itemsAtPath(heightPath);
        final List<Object> bmis = rmComposition.itemsAtPath(bmiPath);
        final List<Object> heads = rmComposition.itemsAtPath(headCircumferencePath);

        Assert.assertEquals(3, weights.size());
        Assert.assertEquals(3, heights.size());
        Assert.assertEquals(3, bmis.size());
        Assert.assertEquals(3, heads.size());

        // weight
        Assert.assertTrue(weights.stream().allMatch(weight -> ((DvQuantity) weight).getMagnitude().equals(501.0)
                || ((DvQuantity) weight).getMagnitude().equals(502.0)
                || ((DvQuantity) weight).getMagnitude().equals(503.0)));
        Assert.assertNotNull(weights.stream().filter(weight -> ((DvQuantity) weight).getMagnitude().equals(501.0)
                && ((DvQuantity) weight).getUnits().equals("kg")).findAny().orElse(null));
        Assert.assertNotNull(weights.stream().filter(weight -> ((DvQuantity) weight).getMagnitude().equals(502.0)
                && ((DvQuantity) weight).getUnits().equals("t")).findAny().orElse(null));
        Assert.assertNotNull(weights.stream().filter(weight -> ((DvQuantity) weight).getMagnitude().equals(503.0)
                && ((DvQuantity) weight).getUnits().equals("mm")).findAny().orElse(null));
        Assert.assertEquals(3, weightComment.size());
        Assert.assertTrue(
                weightComment.stream().allMatch(weight -> ((DvText) weight).getValue().equals("body_weightLorem ipsum0")
                        || ((DvText) weight).getValue().equals("body_weightLorem ipsum1")
                        || ((DvText) weight).getValue().equals("body_weightLorem ipsum2")));

        // height
        Assert.assertTrue(heights.stream().allMatch(height -> ((DvQuantity) height).getMagnitude().equals(500.0)));

    }


    @Test
    public void growthChartToOpenEhrToFhir() throws IOException {
        final Bundle testBundle = GrowthChartToOpenEhrTest.growthChartTestBundle();

        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, testBundle, operationaltemplate);

        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);

//        compareFlatJsons(context, operationalTemplate, testBundle, bundle); can't compare because for some reason archie has a bug when deserializing flat json; it just added a time

        Assert.assertEquals(12, bundle.getEntry().size());
        List<Observation> weights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "weight".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, weights.size());
        final Observation firstWeight = weights.stream()
                .filter(e -> "65.0".equals(e.getValueQuantity().getValue().toPlainString()) && "kg".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation secondWeight = weights.stream()
                .filter(e -> "66.0".equals(e.getValueQuantity().getValue().toPlainString()) && "kg".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation thirdWeight = weights.stream()
                .filter(e -> "68.0".equals(e.getValueQuantity().getValue().toPlainString()) && "kg".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        Assert.assertNotNull(firstWeight);
        Assert.assertNotNull(secondWeight);
        Assert.assertNotNull(thirdWeight);


        Assert.assertEquals("2020-10-07T01:00:00",
                            openFhirMapperUtils.dateTimeToString(secondWeight.getEffectiveDateTimeType().getValue()));
        Assert.assertEquals("2020-10-07T03:00:00",
                            openFhirMapperUtils.dateTimeToString(thirdWeight.getEffectiveDateTimeType().getValue()));
//        Assert.assertTrue(firstWeight.getEffectiveDateTimeType().isEmpty()); // todo
        Assert.assertNull(secondWeight.getNoteFirstRep().getText());
        Assert.assertEquals("just too fat", thirdWeight.getNoteFirstRep().getText());

        List<Observation> heights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "height".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, heights.size());
        final Observation firstHeight = heights.stream()
                .filter(e -> "180.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation secondHeight = heights.stream()
                .filter(e -> "200.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation thirdHeight = heights.stream()
                .filter(e -> "220.0".equals(e.getValueQuantity().getValue().toPlainString()) && "m".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        Assert.assertNotNull(firstHeight);
        Assert.assertNotNull(secondHeight);
        Assert.assertNotNull(thirdHeight);


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
        final Observation firstHead = heads.stream()
                .filter(e -> "54.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation secondHead = heads.stream()
                .filter(e -> "55.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation thirdHead = heads.stream()
                .filter(e -> "56.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(
                        e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        Assert.assertNotNull(firstHead);
        Assert.assertNotNull(secondHead);
        Assert.assertNotNull(thirdHead);
        Assert.assertTrue(heads.stream().allMatch(obs -> obs.getStatusElement().getValueAsString().equals("final")));

        Assert.assertEquals("2020-10-07T01:00:00",
                            openFhirMapperUtils.dateTimeToString(firstHead.getEffectiveDateTimeType().getValue()));
        Assert.assertEquals("2020-10-07T02:00:00",
                            openFhirMapperUtils.dateTimeToString(secondHead.getEffectiveDateTimeType().getValue()));
        Assert.assertEquals("2020-10-07T03:00:00",
                            openFhirMapperUtils.dateTimeToString(thirdHead.getEffectiveDateTimeType().getValue()));
    }


}
