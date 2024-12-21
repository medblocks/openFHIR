package com.medblocks.openfhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.tofhir.IntermediateCacheProcessing;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datastructures.Cluster;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.encapsulated.DvMultimedia;
import com.nedap.archie.rm.datavalues.quantity.DvCount;
import com.nedap.archie.rm.datavalues.quantity.DvOrdinal;
import com.nedap.archie.rm.datavalues.quantity.DvProportion;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.nedap.archie.rm.datavalues.quantity.datetime.DvDateTime;
import com.nedap.archie.rm.generic.PartyIdentified;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.tofhir.OpenEhrToFhir;
import com.medblocks.openfhir.tofhir.OpenEhrToFhirTest;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhr;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrTest;
import com.medblocks.openfhir.util.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BidirectionalTest {
    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());

    final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
    TestOpenFhirMappingContext repo;
    OpenEhrToFhir openEhrToFhir;
    FhirToOpenEhr fhirToOpenEhr;

    @Before
    public void init() {
        repo = new TestOpenFhirMappingContext(fhirPath, openFhirStringUtils);
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            // todo!!
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });

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
        fhirToOpenEhr = new FhirToOpenEhr(fhirPath,
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
    public void news2() throws IOException {
        final FhirConnectContext context = getContext("/news2/NEWS2_Context_Mapping.context.yaml");
        repo.initRepository(context, getClass().getResource("/news2/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/news2/NEWS2 Encounter Parent.opt");
        final Bundle testBundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, getClass().getResourceAsStream("/news2/exampleBundle.json"));
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, testBundle, operationalTemplate); // should create two of them :o what now :o
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationalTemplate); // should create two of them :o what now :o

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
        Assert.assertEquals("ScoreOf", ((DvOrdinal) composition.itemAtPath(respirationRatePath)).getSymbol().getValue());

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

        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat("/news2/news2_encounter_parent_FLAT.json"), new OPTParser(operationalTemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationalTemplate);

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
        final Observation.ObservationComponentComponent raspirationRateComponent = getComponentByCode(news2Observation.get(0), "1104301000000104", true);
        Assert.assertEquals("3", raspirationRateComponent
                .getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0064", raspirationRateComponent
                .getValueQuantity().getCode());
        Assert.assertEquals("≥25", raspirationRateComponent
                .getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent spoScale1 = getComponentByCode(news2Observation.get(0), "1104311000000102", true);
        Assert.assertEquals("2", spoScale1.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0032", spoScale1.getValueQuantity().getCode());
        Assert.assertEquals("92-93", spoScale1.getValueQuantity().getUnit());


        final Observation.ObservationComponentComponent airOrOxygen = getComponentByCode(news2Observation.get(0), "1104331000000105", true);
        Assert.assertEquals("0", airOrOxygen.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0036", airOrOxygen.getValueQuantity().getCode());
        Assert.assertEquals("Air", airOrOxygen.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent systolic = getComponentByCode(news2Observation.get(0), "1104341000000101", true);
        Assert.assertEquals("2", systolic.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0016", systolic.getValueQuantity().getCode());
        Assert.assertEquals("91-100", systolic.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent consciousness = getComponentByCode(news2Observation.get(0), "1104361000000100", true);
        Assert.assertEquals("0", consciousness.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0024", consciousness.getValueQuantity().getCode());
        Assert.assertEquals("Alert", consciousness.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent temperature = getComponentByCode(news2Observation.get(0), "1104371000000107", true);
        Assert.assertEquals("3", temperature.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("at0039", temperature.getValueQuantity().getCode());
        Assert.assertEquals("≤35.0", temperature.getValueQuantity().getUnit());

        final Observation.ObservationComponentComponent pulse = getComponentByCode(news2Observation.get(0), "1104351000000103", true);
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
        Assert.assertEquals("vital-signs", temperatureObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
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
        Assert.assertEquals("vital-signs", repirationObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("183.0", repirationObservation.get(0).getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("/min", repirationObservation.get(0).getValueQuantity().getUnit());

        // pulse oximetry3
        final List<Observation> poximetryObservation = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(obs -> obs.getCode().getCodingFirstRep().getCode().equals("103228002"))
                .collect(Collectors.toList());
        Assert.assertEquals(1, poximetryObservation.size());
        Assert.assertEquals("final", poximetryObservation.get(0).getStatusElement().getValueAsString());
        Assert.assertEquals("vital-signs", poximetryObservation.get(0).getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("37.6", poximetryObservation.get(0).getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("%", poximetryObservation.get(0).getValueQuantity().getCode());
        Assert.assertEquals("percent", poximetryObservation.get(0).getValueQuantity().getUnit());
        Assert.assertEquals("http://unitsofmeasure.org", poximetryObservation.get(0).getValueQuantity().getSystem());
    }

    private Observation.ObservationComponentComponent getComponentByCode(final Observation observation, final String code, boolean assertOnlyOne) {
        final List<Observation.ObservationComponentComponent> allMatching = observation.getComponent().stream()
                .filter(com -> code.equals(com.getCode().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        if (assertOnlyOne) {
            Assert.assertEquals(1, allMatching.size());
        }
        return allMatching.get(0);
    }


    @Test
    public void acpPoc() throws IOException {
        final FhirConnectContext context = getContext("/rso_poc_acp/acp-poc.context.yml");
        repo.initRepository(context, getClass().getResource("/rso_poc_acp/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/rso_poc_acp/ACP_POC.opt");
        final Bundle testBundle = testAcp();
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, testBundle, operationalTemplate);

        final String typeOfDirectiveCodePath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/data[at0001]/items[at0005]/value";
        final String statusPath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/data[at0001]/items[at0004]/value";
        final String commentPath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/data[at0001]/items[at0038]/value";
        final String mediaNamePath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/protocol[at0010]/items[openEHR-EHR-CLUSTER.media_file.v1]/items[at0002]/value";
        final String mediaPath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/protocol[at0010]/items[openEHR-EHR-CLUSTER.media_file.v1]";
        final String treatmentCodePath = "/content[openEHR-EHR-EVALUATION.advance_intervention_decisions.v1]/data[at0001]/items[at0014]/items[at0015]/value";
        final String decisionCodePath = "/content[openEHR-EHR-EVALUATION.advance_intervention_decisions.v1, 'Advance intervention decisions']/data[at0001]/items[at0014]/items[at0034]/value";
        final String commentInterventionPath = "/content[openEHR-EHR-EVALUATION.advance_intervention_decisions.v1]/data[at0001]/items[at0014]/items[at0040]/value/value";

        Assert.assertEquals("NR", ((DvCodedText) composition.itemAtPath(typeOfDirectiveCodePath)).getDefiningCode().getCodeString());
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4.14.1", ((DvCodedText) composition.itemAtPath(typeOfDirectiveCodePath)).getDefiningCode().getTerminologyId().getValue());
        Assert.assertEquals("active", ((DvText) composition.itemAtPath(statusPath)).getValue());
        Assert.assertEquals("Comment of this thing which is nice", ((DvText) composition.itemAtPath(commentPath)).getValue());
        final Element mediaElement = (Element) ((Cluster) composition.itemAtPath(mediaPath)).getItems().get(0);
        final DvMultimedia multimedia = (DvMultimedia) mediaElement.getValue();
        Assert.assertTrue(new String(multimedia.getData()).startsWith("JVBER"));
        Assert.assertEquals("application/pdf", multimedia.getMediaType().getCodeString());
        Assert.assertEquals("Voorbeeld voorpagina wilsverklaringen - PDF.pdf", composition.itemAtPath(mediaNamePath + "/value"));
        Assert.assertEquals("Cardiopulmonary resuscitation (procedure)", ((DvCodedText) composition.itemAtPath(treatmentCodePath)).getValue());
        Assert.assertEquals("89666000", ((DvCodedText) composition.itemAtPath(treatmentCodePath)).getDefiningCode().getCodeString());
        Assert.assertEquals("http://snomed.info/sct", ((DvCodedText) composition.itemAtPath(treatmentCodePath)).getDefiningCode().getTerminologyId().getValue());
        Assert.assertEquals("Comment of this treatment directive", composition.itemAtPath(commentInterventionPath));
        Assert.assertEquals("Yes, but", ((DvCodedText) composition.itemAtPath(decisionCodePath)).getValue());
        Assert.assertEquals("JA_MAAR", ((DvCodedText) composition.itemAtPath(decisionCodePath)).getDefiningCode().getCodeString());
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4", ((DvCodedText) composition.itemAtPath(decisionCodePath)).getDefiningCode().getTerminologyId().getValue());

        Assert.assertEquals("en", composition.getLanguage().getCodeString());
        Assert.assertEquals("ISO_639-1", composition.getLanguage().getTerminologyId().getValue());
        Assert.assertEquals("ISO_3166-1", composition.getTerritory().getTerminologyId().getValue());
        Assert.assertEquals("DE", composition.getTerritory().getCodeString());
        Assert.assertEquals("Max Muuster", ((PartyIdentified) composition.getComposer()).getName());


        // now back to fhir
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);
        Assert.assertEquals(2, bundle.getEntry().size());

        final List<Consent> treatmentDirectives = bundle.getEntry().stream()
                .map(en -> ((Consent) en.getResource()))
                .filter(en -> en.getMeta().getProfile().stream().anyMatch(prof -> "http://nictiz.nl/fhir/StructureDefinition/zib-TreatmentDirective".equals(prof.getValue())))
                .collect(Collectors.toList());

        final List<Consent> advanceDirectives = bundle.getEntry().stream()
                .map(en -> ((Consent) en.getResource()))
                .filter(en -> en.getMeta().getProfile().stream().anyMatch(prof -> "http://nictiz.nl/fhir/StructureDefinition/zib-AdvanceDirective".equals(prof.getValue())))
                .collect(Collectors.toList());

        Assert.assertEquals(1, treatmentDirectives.size());
        Assert.assertEquals(1, advanceDirectives.size());

        // assert treatment directives
        final Consent treatment = treatmentDirectives.get(0);
        Assert.assertEquals("JA_MAAR", ((CodeableConcept) treatment.getModifierExtension().get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4", ((CodeableConcept) treatment.getModifierExtension().get(0).getValue()).getCodingFirstRep().getSystem());

        Assert.assertEquals("89666000", (treatment.getExtension().stream()
                .filter(ext -> ext.getUrl().equals("http://nictiz.nl/fhir/StructureDefinition/zib-TreatmentDirective-Treatment"))
                .map(ext -> ((CodeableConcept) ext.getValue()))
                .findAny().orElse(null).getCodingFirstRep().getCode()));

        Assert.assertEquals("http://snomed.info/sct", (treatment.getExtension().stream()
                .filter(ext -> ext.getUrl().equals("http://nictiz.nl/fhir/StructureDefinition/zib-TreatmentDirective-Treatment"))
                .map(ext -> ((CodeableConcept) ext.getValue()))
                .findAny().orElse(null).getCodingFirstRep().getSystem()));

        Assert.assertEquals("Comment of this treatment directive", (treatment.getExtension().stream()
                .filter(ext -> ext.getUrl().equals("http://nictiz.nl/fhir/StructureDefinition/Comment"))
                .map(ext -> ((StringType) ext.getValue()))
                .findAny().orElse(null).getValue()));


        // assert advance directives
        final Consent advanced = advanceDirectives.get(0);
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4.14.1", advanced.getCategoryFirstRep().getCodingFirstRep().getSystem());
        Assert.assertEquals("NR", advanced.getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("active", advanced.getStatusElement().getValueAsString());

        Assert.assertEquals("Comment of this thing which is nice", (advanced.getExtension().stream()
                .filter(ext -> ext.getUrl().equals("http://nictiz.nl/fhir/StructureDefinition/Comment"))
                .map(ext -> ((StringType) ext.getValue()))
                .findAny().orElse(null).getValue()));

        // media now :o
        final Attachment sourceAttachment = advanced.getSourceAttachment();
        Assert.assertEquals("Voorbeeld voorpagina wilsverklaringen - PDF.pdf", sourceAttachment.getTitle());
        Assert.assertEquals("application/pdf", sourceAttachment.getContentType());
        Assert.assertTrue(new String(sourceAttachment.getData()).startsWith("JVBER")); // todo


    }


    public Bundle testAcp() {
        final Bundle bundle = new Bundle();
        final Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        final Consent consent = FhirContext.forR4().newXmlParser()
                .parseResource(Consent.class, getClass().getResourceAsStream("/rso_poc_acp/zib-AdvanceDirective - example.xml"));
        consent.setPatient(new Reference().setDisplay("Max Muuster"));
        entry.setResource(consent);
        bundle.addEntry(entry);

        final Bundle.BundleEntryComponent entry2 = new Bundle.BundleEntryComponent();
        entry2.setResource(FhirContext.forR4().newXmlParser()
                .parseResource(Consent.class, getClass().getResourceAsStream("/rso_poc_acp/zib-TreatmentDirective - example.xml")));
        bundle.addEntry(entry2);
        return bundle;
    }


    public Bundle testAcpMultiples() {
        final Bundle bundle = new Bundle();
        final Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        entry.setResource(FhirContext.forR4().newXmlParser()
                .parseResource(Consent.class, getClass().getResourceAsStream("/rso_poc_acp/zib-AdvanceDirective - example.xml")));
        bundle.addEntry(entry);

        final Bundle.BundleEntryComponent entry2 = new Bundle.BundleEntryComponent();
        entry2.setResource(FhirContext.forR4().newXmlParser()
                .parseResource(Consent.class, getClass().getResourceAsStream("/rso_poc_acp/zib-AdvanceDirective - example2.xml")));
        bundle.addEntry(entry2);
        return bundle;
    }

    @Test
    public void medicationOrderToFhirToOpenEhr() throws IOException {
        final FhirConnectContext context = getContext("/medication-order.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/medication order.opt");
        final WebTemplate template = new OPTParser(operationalTemplate).parse();

        // have a composition from config
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat("/medication_order_flat.json"), template);

        // transform it to FHIR
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);

        // fix references; I think this is only the case for testing, otherwise references should be intact
        for (Bundle.BundleEntryComponent bundleEntryComponent : bundle.getEntry()) {
            final MedicationRequest medReq = (MedicationRequest) bundleEntryComponent.getResource();
            final Reference medicationReference = medReq.getMedicationReference();
            final String string = UUID.randomUUID().toString();
            medicationReference.getResource().setId(string);
            medicationReference.setReference(string);
        }

        // transform it back to openEHR
        final Composition rmComposition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationalTemplate);
        final String medicationTextPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[at0070]/value";
        final String doseAmountPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[openEHR-EHR-CLUSTER.dosage.v1]/items[at0144]/value";
        final String additionalInstruction = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[at0044]";
        final String orderStartDate = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[at0113]/items[at0012]";
        final String directionDuration = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[at0066]/value";


        final List<Object> medicationTexts = rmComposition.itemsAtPath(medicationTextPath);
        final List<Object> doseAmounts = rmComposition.itemsAtPath(doseAmountPath);
        final List<Object> additionalInstructions = rmComposition.itemsAtPath(additionalInstruction);
        final List<Object> orderStarts = rmComposition.itemsAtPath(orderStartDate);
        final List<Object> directDurations = rmComposition.itemsAtPath(directionDuration);
        if (medicationTexts.isEmpty() || doseAmounts.isEmpty() || additionalInstructions.isEmpty() || orderStarts.isEmpty() || directDurations.isEmpty()) {
            Assert.fail();
        }

        Assert.assertTrue(medicationTexts.stream().allMatch(med -> ((DvText) med).getValue().equals("Lorem ipsum1") || ((DvText) med).getValue().equals("Lorem ipsum0")));
        Assert.assertTrue(doseAmounts.stream().allMatch(med -> ((DvQuantity) med).getMagnitude().equals(21.0) && ((DvQuantity) med).getUnits().equals("mm")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(med -> ((DvText) ((Element) med).getValue()).getValue().startsWith("Additional instruction on one first")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(med -> ((DvText) ((Element) med).getValue()).getValue().startsWith("Additional instruction on one second")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(med -> ((DvText) ((Element) med).getValue()).getValue().startsWith("Additional instruction on two first")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(med -> ((DvText) ((Element) med).getValue()).getValue().startsWith("Additional instruction on two second")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(med -> ((DvText) ((Element) med).getValue()).getValue().startsWith("Additional instruction on two third")));
        Assert.assertEquals(5, additionalInstructions.size());
        Assert.assertEquals(1, orderStarts.size());
        final Element date = (Element) orderStarts.get(0);
        Assert.assertEquals(4, ((LocalDateTime) ((DvDateTime) date.getValue()).getValue()).getHour());
        Assert.assertEquals(5, ((LocalDateTime) ((DvDateTime) date.getValue()).getValue()).getMinute());
        Assert.assertEquals(2022, ((LocalDateTime) ((DvDateTime) date.getValue()).getValue()).getYear());
        Assert.assertEquals("Indefinite", ((DvCodedText) directDurations.get(0)).getValue());
        Assert.assertEquals("local", ((DvCodedText) directDurations.get(0)).getDefiningCode().getTerminologyId().getName());
        Assert.assertEquals("at0067", ((DvCodedText) directDurations.get(0)).getDefiningCode().getCodeString());
    }

    @Test
    public void medicationOrderToOpenEhrToFhir() throws IOException {
        final FhirConnectContext context = getContext("/medication-order.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/medication order.opt");
        final Bundle testBundle = FhirToOpenEhrTest.testMedicationMedicationRequestBundle();
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context,
                testBundle, operationalTemplate);

        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);
        // fix references; I think this is only the case for testing, otherwise references should be intact
        for (Bundle.BundleEntryComponent bundleEntryComponent : bundle.getEntry()) {
            final MedicationRequest medReq = (MedicationRequest) bundleEntryComponent.getResource();
            final Reference medicationReference = medReq.getMedicationReference();
            final String string = UUID.randomUUID().toString();
            medicationReference.getResource().setId(string);
            medicationReference.setReference(string);
        }


        Assert.assertEquals(1, bundle.getEntry().size());
        final MedicationRequest medReq = (MedicationRequest) bundle.getEntryFirstRep().getResource();
        Assert.assertEquals("medication text", ((Medication) medReq.getMedicationReference().getResource()).getCode().getText());
        Assert.assertEquals("unit", ((Quantity) medReq.getDosageInstructionFirstRep().getDoseAndRateFirstRep().getDose()).getUnit());
        Assert.assertEquals("111.0", ((Quantity) medReq.getDosageInstructionFirstRep().getDoseAndRateFirstRep().getDose()).getValue().toPlainString());
        compareFlatJsons(context, operationalTemplate, testBundle, bundle);
    }

    @Test
    public void bloodPressureToFhirToOpenEhr() throws IOException {
        final FhirConnectContext context = getContext("/simple-blood-pressure.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/Blood Pressure.opt");
        final WebTemplate template = new OPTParser(operationalTemplate).parse();

        // have a composition from config
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat("/blood-pressure_flat.json"), template);

        // transform it to FHIR
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);
        OpenEhrToFhirTest.assertBloodPressureFhir(bundle); // this is being tested elsewhere but whatever.., why not

        // transform it back to openEHR
        final Composition rmComposition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationalTemplate);
        final String systolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value";
        final String diastolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0005]/value";

        final String interpretationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at1059]";
        final String descriptionPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0033]";
        final String locationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/protocol[at0011]/items[at0014]";

        final List<Object> objects = composition.itemsAtPath(systolicPath);
        if (objects.isEmpty()) {
            Assert.fail();
        }
        for (Object systolicValues : objects) {
            final Double systolicMagnitude = ((DvQuantity) systolicValues).getMagnitude();
            Assert.assertTrue(rmComposition.itemsAtPath(systolicPath).stream().anyMatch(item -> ((DvQuantity) item).getMagnitude().equals(systolicMagnitude)));
        }
        final List<Object> objects1 = composition.itemsAtPath(diastolicPath);
        if (objects1.isEmpty()) {
            Assert.fail();
        }
        for (Object diastolicValues : objects1) {
            final Double systolicMagnitude = ((DvQuantity) diastolicValues).getMagnitude();
            Assert.assertTrue(rmComposition.itemsAtPath(diastolicPath).stream().anyMatch(item -> ((DvQuantity) item).getMagnitude().equals(systolicMagnitude)));
        }
        final List<Object> objects2 = composition.itemsAtPath(interpretationPath);
        if (objects2.isEmpty()) {
            Assert.fail();
        }
        for (Object interpretationValues : objects2) {
            final String interpretationValue = ((DvText) ((Element) interpretationValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(interpretationPath).stream().anyMatch(item -> ((DvText) ((Element) item).getValue()).getValue().equals(interpretationValue)));
        }
        final List<Object> objects3 = composition.itemsAtPath(descriptionPath);
        if (objects3.isEmpty()) {
            Assert.fail();
        }
        for (Object descriptionValues : objects3) {
            final String descriptionValue = ((DvText) ((Element) descriptionValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(descriptionPath).stream().anyMatch(item -> ((DvText) ((Element) item).getValue()).getValue().equals(descriptionValue)));
        }
        final List<Object> objects4 = composition.itemsAtPath(locationPath);
        if (objects4.isEmpty()) {
            Assert.fail();
        }
        for (Object locationValues : objects4) {
            final String locationValue = ((DvText) ((Element) locationValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(locationPath).stream().anyMatch(item -> ((DvText) ((Element) item).getValue()).getValue().equals(locationValue)));
        }

    }

    @Test
    public void growthChartToFhirToOpenEhr() throws IOException {
        final FhirConnectContext context = getContext("/example-002-growth-chart.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/Growth chart.opt");
        final WebTemplate template = new OPTParser(operationalTemplate).parse();

        // have a composition from config
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat("/growth_chart_flat.json"), template);

        // transform it to FHIR
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);

        // transform it back to openEHR
        final Composition rmComposition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationalTemplate);

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
        Assert.assertNotNull(weights.stream().filter(weight -> ((DvQuantity) weight).getMagnitude().equals(501.0) && ((DvQuantity) weight).getUnits().equals("kg")).findAny().orElse(null));
        Assert.assertNotNull(weights.stream().filter(weight -> ((DvQuantity) weight).getMagnitude().equals(502.0) && ((DvQuantity) weight).getUnits().equals("t")).findAny().orElse(null));
        Assert.assertNotNull(weights.stream().filter(weight -> ((DvQuantity) weight).getMagnitude().equals(503.0) && ((DvQuantity) weight).getUnits().equals("mm")).findAny().orElse(null));
        Assert.assertEquals(3, weightComment.size());
        Assert.assertTrue(weightComment.stream().allMatch(weight -> ((DvText) weight).getValue().equals("body_weightLorem ipsum0")
                || ((DvText) weight).getValue().equals("body_weightLorem ipsum1")
                || ((DvText) weight).getValue().equals("body_weightLorem ipsum2")));

        // height
        Assert.assertTrue(heights.stream().allMatch(height -> ((DvQuantity) height).getMagnitude().equals(500.0)));

    }

    @Test
    public void bloodPressureToOpenEhrToFhir() throws IOException {
        final FhirConnectContext context = getContext("/simple-blood-pressure.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/Blood Pressure.opt");
        final Observation testResource = FhirToOpenEhrTest.testBloodPressureObservation();
        final Bundle testBundle = new Bundle().addEntry(new Bundle.BundleEntryComponent().setResource(testResource));
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context,
                testResource, operationalTemplate);

        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);

        compareFlatJsons(context, operationalTemplate, testBundle, bundle);

        Assert.assertEquals(1, bundle.getEntry().size());
        final Observation observation = (Observation) bundle.getEntryFirstRep().getResource();
        Assert.assertEquals("description", observation.getCode().getText());
        Assert.assertEquals(3, observation.getComponent().size());
        Assert.assertEquals("interpretation text", observation.getComponent().stream().filter(com -> com.getCode().isEmpty()).map(com -> com.getInterpretationFirstRep().getText()).findFirst().orElse(null));
        Assert.assertEquals("456.0", observation.getComponent().stream().filter(com -> "8480-6".equals(com.getCode().getCodingFirstRep().getCode())).map(com -> com.getValueQuantity().getValue().toPlainString()).findFirst().orElse(null));
        Assert.assertEquals("789.0", observation.getComponent().stream().filter(com -> "8462-4".equals(com.getCode().getCodingFirstRep().getCode())).map(com -> com.getValueQuantity().getValue().toPlainString()).findFirst().orElse(null));
        Assert.assertEquals("mm[Hg2]", observation.getComponent().stream().filter(com -> "8462-4".equals(com.getCode().getCodingFirstRep().getCode())).map(com -> com.getValueQuantity().getUnit()).findFirst().orElse(null));
        Assert.assertEquals("mm[Hg]", observation.getComponent().stream().filter(com -> "8480-6".equals(com.getCode().getCodingFirstRep().getCode())).map(com -> com.getValueQuantity().getUnit()).findFirst().orElse(null));
        Assert.assertEquals("THIS IS LOCATION OF MEASUREMENT", observation.getBodySite().getText());
        Assert.assertEquals("at00256", observation.getBodySite().getCodingFirstRep().getCode());
        Assert.assertEquals("remotey", observation.getBodySite().getCodingFirstRep().getSystem());
    }

    @Test
    public void growthChartToOpenEhrToFhir() throws IOException {
        final FhirConnectContext context = getContext("/example-002-growth-chart.context.yml");
        repo.initRepository(context, getClass().getResource("/").getFile());
        final Bundle testBundle = FhirToOpenEhrTest.growthChartTestBundle();
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });


        final OPERATIONALTEMPLATE operationalTemplate = getOperationalTemplate("/Growth chart.opt");
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, testBundle, operationalTemplate);

        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationalTemplate);

//        compareFlatJsons(context, operationalTemplate, testBundle, bundle); can't compare because for some reason archie has a bug when deserializing flat json; it just added a time

        Assert.assertEquals(12, bundle.getEntry().size());
        List<Observation> weights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "weight".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, weights.size());
        final Observation firstWeight = weights.stream().filter(e -> "65.0".equals(e.getValueQuantity().getValue().toPlainString()) && "kg".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation secondWeight = weights.stream().filter(e -> "66.0".equals(e.getValueQuantity().getValue().toPlainString()) && "kg".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation thirdWeight = weights.stream().filter(e -> "68.0".equals(e.getValueQuantity().getValue().toPlainString()) && "kg".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        Assert.assertNotNull(firstWeight);
        Assert.assertNotNull(secondWeight);
        Assert.assertNotNull(thirdWeight);


        Assert.assertEquals("2020-10-07T01:00:00", openFhirMapperUtils.dateTimeToString(secondWeight.getEffectiveDateTimeType().getValue()));
        Assert.assertEquals("2020-10-07T03:00:00", openFhirMapperUtils.dateTimeToString(thirdWeight.getEffectiveDateTimeType().getValue()));
//        Assert.assertTrue(firstWeight.getEffectiveDateTimeType().isEmpty()); // todo
        Assert.assertNull(secondWeight.getNoteFirstRep().getText());
        Assert.assertEquals("just too fat", thirdWeight.getNoteFirstRep().getText());

        List<Observation> heights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "height".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, heights.size());
        final Observation firstHeight = heights.stream().filter(e -> "180.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation secondHeight = heights.stream().filter(e -> "200.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation thirdHeight = heights.stream().filter(e -> "220.0".equals(e.getValueQuantity().getValue().toPlainString()) && "m".equals(e.getValueQuantity().getUnit()))
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
        final Observation firstHead = heads.stream().filter(e -> "54.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation secondHead = heads.stream().filter(e -> "55.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        final Observation thirdHead = heads.stream().filter(e -> "56.0".equals(e.getValueQuantity().getValue().toPlainString()) && "cm".equals(e.getValueQuantity().getUnit()))
                .findFirst().orElse(null);
        Assert.assertNotNull(firstHead);
        Assert.assertNotNull(secondHead);
        Assert.assertNotNull(thirdHead);
        Assert.assertTrue(heads.stream().allMatch(obs -> obs.getStatusElement().getValueAsString().equals("final")));

        Assert.assertEquals("2020-10-07T01:00:00", openFhirMapperUtils.dateTimeToString(firstHead.getEffectiveDateTimeType().getValue()));
        Assert.assertEquals("2020-10-07T02:00:00", openFhirMapperUtils.dateTimeToString(secondHead.getEffectiveDateTimeType().getValue()));
        Assert.assertEquals("2020-10-07T03:00:00", openFhirMapperUtils.dateTimeToString(thirdHead.getEffectiveDateTimeType().getValue()));
    }

    private void compareFlatJsons(final FhirConnectContext context, final OPERATIONALTEMPLATE operationalTemplate, final Bundle testBundle, final Bundle afterBundle) {
        repo.initRepository(context, getClass().getResource("/").getFile());
        final JsonObject initialFlatJson = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationalTemplate);
        final JsonObject afterFlatJson = fhirToOpenEhr.fhirToFlatJsonObject(context, afterBundle, operationalTemplate);
        Assert.assertEquals(initialFlatJson.size(), afterFlatJson.size());
        for (Map.Entry<String, JsonElement> initialEntrySet : initialFlatJson.entrySet()) {
            final String initialKey = initialEntrySet.getKey();
            final String initialValue = initialEntrySet.getValue().getAsString();
            Assert.assertEquals(initialValue, afterFlatJson.getAsJsonPrimitive(initialKey).getAsString());
        }
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