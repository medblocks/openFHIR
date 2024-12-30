package com.medblocks.openfhir.growthchart;

import com.medblocks.openfhir.GenericTest;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.nedap.archie.rm.composition.Composition;
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

public class GrowthChartToFhirTest extends GenericTest {

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
    public void growthChartToFhir() throws IOException {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             new OPTParser(
                                                                                     operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
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
        final Observation firstWeight = weights.stream().filter(e -> "2022-02-03T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation secondWeight = weights.stream().filter(e -> "2022-02-04T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation thirdWeight = weights.stream().filter(e -> "2022-02-05T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
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
        final Observation firstHead = heads.stream().filter(e -> "2023-02-03T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation secondHead = heads.stream().filter(e -> "2023-02-04T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation thirdHead = heads.stream().filter(e -> "2023-02-05T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        Assert.assertEquals("50.0", firstHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("cm", firstHead.getValueQuantity().getUnit());
        Assert.assertEquals("51.0", secondHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("mm", secondHead.getValueQuantity().getUnit());
        Assert.assertEquals("52.0", thirdHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("m", thirdHead.getValueQuantity().getUnit());
    }


}
