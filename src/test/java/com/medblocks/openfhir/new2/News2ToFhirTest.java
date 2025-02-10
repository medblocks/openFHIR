package com.medblocks.openfhir.new2;

import com.medblocks.openfhir.GenericTest;
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

public class News2ToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/news2/";
    final String CONTEXT_MAPPING = "/news2/NEWS2_Context_Mapping.context.yaml";
    final String HELPER_LOCATION = "/news2/";
    final String OPT = "NEWS2 Encounter Parent.opt";
    final String FLAT = "news2_encounter_parent_FLAT.json";
    final String BUNDLE = "exampleBundle.json";


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
    public void toFhir() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        // news2
        final List<Observation> news2Observation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("1104051000000101"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, news2Observation.size());
        Assert.assertEquals("final", news2Observation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("survey", news2Observation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("9", news2Observation.get(0).getValueQuantity().getValue().toPlainString());
//        Assert.assertEquals(Long.valueOf("1724566899000"), (Long) news2Observation.get(0).getEffectiveDateTimeType().getValue().getTime());
        final Observation.ObservationComponentComponent raspirationRateComponent = getComponentByCode(
                news2Observation.get(0), "1104301000000104", true);
        Assert.assertEquals("3", raspirationRateComponent
                .getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0064", raspirationRateComponent
                .getValueQuantity().getCode());
        Assert.assertEquals("≥25", raspirationRateComponent
                .getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent spoScale1 = getComponentByCode(news2Observation.get(0),
                                                                                       "1104311000000102", true);
        Assert.assertEquals("2", spoScale1.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0032", spoScale1.getValueQuantity().getCode());
        Assert.assertEquals("92-93", spoScale1.getValueQuantity().getUnit());


        final Observation.ObservationComponentComponent airOrOxygen = getComponentByCode(news2Observation.get(0),
                                                                                         "1104331000000105", true);
        Assert.assertEquals("0", airOrOxygen.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0036", airOrOxygen.getValueQuantity().getCode());
        Assert.assertEquals("Air", airOrOxygen.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent systolic = getComponentByCode(news2Observation.get(0),
                                                                                      "1104341000000101", true);
        Assert.assertEquals("2", systolic.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0016", systolic.getValueQuantity().getCode());
        Assert.assertEquals("91-100", systolic.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent consciousness = getComponentByCode(news2Observation.get(0),
                                                                                           "1104361000000100", true);
        Assert.assertEquals("0", consciousness.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0024", consciousness.getValueQuantity().getCode());
        Assert.assertEquals("Alert", consciousness.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent temperature = getComponentByCode(news2Observation.get(0),
                                                                                         "1104371000000107", true);
        Assert.assertEquals("3", temperature.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0039", temperature.getValueQuantity().getCode());
        Assert.assertEquals("≤35.0", temperature.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent pulse = getComponentByCode(news2Observation.get(0),
                                                                                   "1104351000000103", true);
        Assert.assertEquals("1", pulse.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0012", pulse.getValueQuantity().getCode());
        Assert.assertEquals("41-50", pulse.getValueQuantity().getUnit());


        // acvpu
        final List<Observation> acvpuObservation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("1104441000000107"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, acvpuObservation.size());
        Assert.assertEquals("final", acvpuObservation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("survey", acvpuObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("at0015", acvpuObservation.get(0).getValueCodeableConcept().getCodingFirstRep().getCode());
        Assert.assertEquals("Confusion", acvpuObservation.get(0).getValueCodeableConcept().getText());


        // blood pressure
        final List<Observation> bpObservation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("75367002"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, bpObservation.size());
        Assert.assertEquals("final", bpObservation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("vital-signs", bpObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("at0028", bpObservation.get(0).getBodySite().getCodingFirstRep().getCode());
        Assert.assertEquals("Left thigh", bpObservation.get(0).getBodySite().getText());

        Assert.assertEquals("175.0", getComponentByCode(bpObservation.get(0), "1091811000000102", true)
                .getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("mm[Hg]", getComponentByCode(bpObservation.get(0), "1091811000000102", true)
                .getValueQuantity().getUnit());

        Assert.assertEquals("335.0", getComponentByCode(bpObservation.get(0), "72313002", true)
                .getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("mm[Hg]", getComponentByCode(bpObservation.get(0), "72313002", true)
                .getValueQuantity().getUnit());

        // body temperature
        final List<Observation> temperatureObservation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("276885007"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, temperatureObservation.size());
        Assert.assertEquals("final", temperatureObservation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("vital-signs",
                            temperatureObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("2.1", temperatureObservation.get(0).getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("Cel", temperatureObservation.get(0).getValueQuantity().getUnit());

        // pulse heart beat
        final List<Observation> bpsObservation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("364075005"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, bpsObservation.size());
        Assert.assertEquals("final", bpsObservation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("vital-signs", bpsObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("120.0", bpsObservation.get(0).getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("/min", bpsObservation.get(0).getValueQuantity().getUnit());

        // respiration
        final List<Observation> repirationObservation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("86290005"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, repirationObservation.size());
        Assert.assertEquals("final", repirationObservation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("vital-signs",
                            repirationObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("183.0", repirationObservation.get(0).getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("/min", repirationObservation.get(0).getValueQuantity().getUnit());

        // pulse oximetry3
        final List<Observation> poximetryObservation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("103228002"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, poximetryObservation.size());
        Assert.assertEquals("final", poximetryObservation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("vital-signs",
                            poximetryObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("37.6", poximetryObservation.get(0).getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("%", poximetryObservation.get(0).getValueQuantity().getCode());
        Assert.assertEquals("percent", poximetryObservation.get(0).getValueQuantity().getUnit());
        Assert.assertEquals("http://unitsofmeasure.org", poximetryObservation.get(0).getValueQuantity().getSystem());
    }

    private Observation.ObservationComponentComponent getComponentByCode(final Observation observation,
                                                                         final String code, boolean assertOnlyOne) {
        final List<Observation.ObservationComponentComponent> allMatching = observation.getComponent().stream()
                .filter(com -> code.equals(com.getCode().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        if (assertOnlyOne) {
            Assert.assertEquals(1, allMatching.size());
        }
        return allMatching.get(0);
    }

}
