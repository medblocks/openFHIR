package com.medblocks.openfhir.bloodpressure;

import com.medblocks.openfhir.GenericTest;
import com.nedap.archie.rm.composition.Composition;
import java.util.Arrays;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.Assert;
import org.junit.Test;

public class BloodPressureToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/blood_pressure/";
    final String CONTEXT_MAPPING = "/blood_pressure/simple-blood-pressure.context.yml";
    final String HELPER_LOCATION = "/blood_pressure/";
    final String OPT = "Blood Pressure.opt";
    final String FLAT = "blood-pressure_flat.json";


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
    public void bloodPressureToFhir() {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             new OPTParser(
                                                                                     operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);

        assertBloodPressureFhir(bundle);
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

        Assert.assertTrue(Arrays.asList(obs1, obs3).stream()
                                  .allMatch(o -> o.getComponent().size() == 3)); // it includes interpretation component
        Assert.assertTrue(Arrays.asList(obs2).stream().allMatch(o -> o.getComponent().size() == 2));

        Assert.assertTrue(Arrays.asList(obs2).stream()
                                  .allMatch(o -> o.getComponent().stream()
                                          .allMatch(com -> com.getCode() != null
                                                  && com.getCode().getCoding().size() == 1
                                                  && (com.getCode().getCodingFirstRep().getCode().equals("8480-6")
                                                  || com.getCode().getCodingFirstRep().getCode().equals("8462-4")))));

        Assert.assertTrue(Arrays.asList(obs1, obs3).stream()
                                  .allMatch(o -> o.getComponent().stream()
                                          .allMatch(com -> !com.getInterpretationFirstRep().isEmpty() || (
                                                  com.getCode() != null
                                                          && com.getCode().getCoding().size() == 1
                                                          && (
                                                          com.getCode().getCodingFirstRep().getCode().equals("8480-6")
                                                                  || com.getCode().getCodingFirstRep().getCode()
                                                                  .equals("8462-4"))))));

        Assert.assertTrue(obs1.getComponent().stream().anyMatch(
                comp -> "This is interpreted as 0th clin interpretation".equals(
                        comp.getInterpretationFirstRep().getText())));
        Assert.assertTrue(obs3.getComponent().stream().anyMatch(
                comp -> "This is interpreted as 2th clin interpretation".equals(
                        comp.getInterpretationFirstRep().getText())));

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
        Assert.assertTrue(Stream.of(obs1, obs2, obs3)
                                  .allMatch(obs -> obs.getPerformerFirstRep().getDisplay().equals("John Doe")));
    }

}
