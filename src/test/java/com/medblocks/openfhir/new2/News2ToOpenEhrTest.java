package com.medblocks.openfhir.new2;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.GenericTest;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.quantity.DvCount;
import com.nedap.archie.rm.datavalues.quantity.DvOrdinal;
import com.nedap.archie.rm.datavalues.quantity.DvProportion;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.nedap.archie.rm.datavalues.quantity.datetime.DvDateTime;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.TemporalAccessor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.junit.Test;

public class News2ToOpenEhrTest extends GenericTest {

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
    public void news2() throws IOException {
        final Bundle testBundle = FhirContext.forR4().newJsonParser()
                .parseResource(Bundle.class, getClass().getResourceAsStream(HELPER_LOCATION + BUNDLE));
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, testBundle,
                                                                          operationaltemplate); // should create two of them :o what now :o
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle,
                                                                         operationaltemplate); // should create two of them :o what now :o

        // assert composition
        final String totalScorePath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0028]/value"; // path to DvCount
        final String respirationRatePath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0006]/value"; // path to DvOrdinal
        final String spo2Path = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0029]/value"; // path to DvOrdinal
        final String airOrOxygenPath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0034]/value"; // path to DvOrdinal
        final String systolicPath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value"; // path to DvOrdinal
        final String pulsePath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0005]/value"; // path to DvOrdinal
        final String consciousPath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0008]/value"; // path to DvOrdinal
        final String temperaturePath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0007]/value"; // path to DvOrdinal
        final String timePath = "/content[openEHR-EHR-OBSERVATION.news2.v1]/data[at0001]/events[at0002]/time"; // dvdatetime

        Assert.assertEquals(Long.valueOf(6), ((DvCount) composition.itemAtPath(totalScorePath)).getMagnitude());
        final TemporalAccessor temporalAccessorTime = ((DvDateTime) composition.itemAtPath(timePath)).getValue();
        Assert.assertEquals(2018, LocalDateTime.from(temporalAccessorTime).getYear());
        Assert.assertEquals(Month.OCTOBER, LocalDateTime.from(temporalAccessorTime).getMonth());
        Assert.assertEquals(4, LocalDateTime.from(temporalAccessorTime).getDayOfMonth());

        final Long respirationValue = ((DvOrdinal) composition.itemAtPath(respirationRatePath)).getValue();
        Assert.assertEquals(Long.valueOf(2), respirationValue);
        Assert.assertEquals("ScoreOf",
                            ((DvOrdinal) composition.itemAtPath(respirationRatePath)).getSymbol().getValue());

        Assert.assertEquals(Long.valueOf(2), ((DvOrdinal) composition.itemAtPath(spo2Path)).getValue());
        Assert.assertEquals(Long.valueOf(0), ((DvOrdinal) composition.itemAtPath(airOrOxygenPath)).getValue());
        Assert.assertEquals(Long.valueOf(0), ((DvOrdinal) composition.itemAtPath(systolicPath)).getValue());
        Assert.assertEquals(Long.valueOf(1), ((DvOrdinal) composition.itemAtPath(pulsePath)).getValue());
        Assert.assertEquals(Long.valueOf(0), ((DvOrdinal) composition.itemAtPath(consciousPath)).getValue());
        Assert.assertEquals(Long.valueOf(1), ((DvOrdinal) composition.itemAtPath(temperaturePath)).getValue());

        final String acvpPath = "/content[openEHR-EHR-OBSERVATION.acvpu.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value"; // dvcodedtext
        final DvCodedText acvp = (DvCodedText) composition.itemAtPath(acvpPath);
        Assert.assertEquals("Pain", acvp.getValue());
        Assert.assertEquals("at0007", acvp.getDefiningCode().getCodeString());

        final String systolicArchPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value"; // DvQuantity
        final DvQuantity systolicQ = (DvQuantity) composition.itemAtPath(systolicArchPath);
        Assert.assertEquals("millimeter of mercury", systolicQ.getUnits());
        Assert.assertEquals(Double.valueOf(120.0), systolicQ.getMagnitude());

        final String locationOfMeasurementPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/protocol[at0011]/items[at0014]/value";
        final DvCodedText locationOfMeas = (DvCodedText) composition.itemAtPath(locationOfMeasurementPath);
        Assert.assertEquals("Left thigh", locationOfMeas.getValue());

        final String tempArchPath = "/content[openEHR-EHR-OBSERVATION.body_temperature.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value"; // DvQuantity
        final DvQuantity temp = (DvQuantity) composition.itemAtPath(tempArchPath);
        Assert.assertEquals("degree Celsius", temp.getUnits());
        Assert.assertEquals(Double.valueOf(37.5), temp.getMagnitude());

        final String pulseArchPath = "/content[openEHR-EHR-OBSERVATION.pulse.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value"; // DvQuantity
        final DvQuantity pulseQ = (DvQuantity) composition.itemAtPath(pulseArchPath);
        Assert.assertEquals("heart beats per minute", pulseQ.getUnits());
        Assert.assertEquals(Double.valueOf(95.0), pulseQ.getMagnitude());

        final String spo2ArchPath = "/content[openEHR-EHR-OBSERVATION.pulse_oximetry.v1]/data[at0001]/events[at0002]/data[at0003]/items[at0006]/value"; // DvProportion
        final DvProportion spo2 = (DvProportion) composition.itemAtPath(spo2ArchPath);
        Assert.assertEquals(Double.valueOf(93), spo2.getNumerator());
        Assert.assertEquals(Double.valueOf(100), spo2.getDenominator());
        Assert.assertEquals(Long.valueOf(2), spo2.getType());
        Assert.assertEquals(Double.valueOf(0.93), spo2.getMagnitude());
    }


}
