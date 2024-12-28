package com.medblocks.openfhir.kds;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class LaborberichtTest extends KdsBidirectionalTest {


    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT = "/kds_new/projects/org.highmed/KDS/laborbericht/KDS_laborbericht.context.yaml";
    final String HELPER_LOCATION = "/kds/laborbericht/";
    final String OPT = "KDS_Laborbericht.opt";
    final String FLAT = "KDS_Laborbericht.flat.json";

    final String BUNDLE = "KDS_Laborbericht_bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("Example Lab Organization",
                            jsonObject.getAsJsonPrimitive("laborbericht/context/_health_care_facility|name")
                                    .getAsString());
        Assert.assertEquals("Example Lab Organization",
                            jsonObject.getAsJsonPrimitive("laborbericht/composer|name").getAsString());
        Assert.assertEquals("26436-6",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/labortest-kategorie|code")
                                    .getAsString());
        Assert.assertEquals("LOINC",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/labortest-kategorie|terminology")
                                    .getAsString());
        Assert.assertEquals("final", jsonObject.getAsJsonPrimitive("laborbericht/context/status").getAsString());
        Assert.assertEquals("Normal blood count",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/schlussfolgerung").getAsString());
        Assert.assertEquals("2022-02-03T04:05:06Z",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/time").getAsString());
        Assert.assertEquals("SP-987654", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/probenmaterial:0/externer_identifikator|id").getAsString());
        Assert.assertEquals("2024-08-24T11:00:00", jsonObject.getAsJsonPrimitive(
                        "laborbericht/laborbefund/probenmaterial:0/zeitpunkt_der_probenentnahme/date_time_value")
                .getAsString());
        Assert.assertEquals("1234567", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/probenmaterial:0/identifikator_des_probenehmers|id").getAsString());
        Assert.assertEquals("Aspiration", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/probenmaterial:0/probenentnahmemethode").getAsString());
        Assert.assertEquals("Right arm",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/körperstelle")
                                    .getAsString());
        Assert.assertEquals("122555007",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/probenart|code")
                                    .getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/probenmaterial:0/probenart|terminology").getAsString());
        Assert.assertEquals("Venous blood specimen",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/probenart|value")
                                    .getAsString());
        Assert.assertEquals("Sample collected in the morning.",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/kommentar")
                                    .getAsString());
        Assert.assertEquals("2022-02-03T05:05:06", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/probenmaterial:0/zeitpunkt_des_probeneingangs").getAsString());
        Assert.assertEquals("available", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/probenmaterial:0/eignung_zur_analyse|code").getAsString());
        Assert.assertEquals("final", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/ergebnis-status|code").getAsString());
        Assert.assertEquals("2022-02-03T04:05:06Z", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/zeitpunkt_ergebnis-status").getAsString());
        Assert.assertEquals("7.4", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/messwert:0/quantity_value|magnitude").getAsString());
        Assert.assertEquals("g/dL", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/messwert:0/quantity_value|unit").getAsString());
        Assert.assertEquals("718-7", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/bezeichnung_des_analyts|code").getAsString());
        Assert.assertEquals("http://loinc.org", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/bezeichnung_des_analyts|terminology").getAsString());
        Assert.assertEquals("Hemoglobin [Mass/volume] in Blood", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/bezeichnung_des_analyts|value").getAsString());
        Assert.assertEquals("H", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/interpretation|code").getAsString());
        Assert.assertEquals("http://hl7.org/fhir/ValueSet/observation-interpretation", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/interpretation|terminology").getAsString());
        Assert.assertEquals("Interpretation description", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/interpretation|value").getAsString());
        Assert.assertEquals("Blood test using standard laboratory methods", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/pro_laboranalyt:0/testmethode|other").getAsString());
        Assert.assertEquals("The observation result is within normal range, but further review may be needed.",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/pro_laboranalyt:0/kommentar:0")
                                    .getAsString());
        Assert.assertEquals("FILL-12345",
                            jsonObject.getAsJsonPrimitive("laborbericht/context/bericht_id").getAsString());

        return jsonObject;
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                                     new OPTParser(
                                                                                             operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allDiagnosticReports = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof DiagnosticReport).collect(Collectors.toList());
        assertEquals(1, allDiagnosticReports.size());

        final DiagnosticReport diagnosticReport = (DiagnosticReport) allDiagnosticReports.get(0).getResource();

        //- name: "healthCareFacility"
//        - name: "composer"
        Assert.assertEquals(2, diagnosticReport.getPerformer().size());
        Assert.assertEquals("DOE, John", diagnosticReport.getPerformer().get(0).getDisplay());
        Assert.assertEquals("Max Mustermann", diagnosticReport.getPerformer().get(1).getDisplay());

        //   - name: "Effective"
        assertEquals("2020-02-03T04:05:06+01:00",
                     diagnosticReport.getEffectivePeriod().getStartElement().getValueAsString());
        assertEquals("2022-02-03T04:05:06+01:00",
                     diagnosticReport.getEffectivePeriod().getEndElement().getValueAsString());

        // - name: "Category"
        assertEquals(1, diagnosticReport.getCategory().size());
        assertEquals("LOINC", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getSystem());
        assertEquals("26436-6", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertEquals("laboratory", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getDisplay());

        //  - name: "Status"
        assertEquals("registered", diagnosticReport.getStatusElement().getValueAsString());

        //  - name: "Conclusion"
        assertEquals("Normal blood count", diagnosticReport.getConclusion());

        // - name: "issued"
        assertEquals("2022-02-03T04:05:06.000+01:00", diagnosticReport.getIssuedElement().getValueAsString());

        // - name: "berichtId"
        assertEquals(1, diagnosticReport.getIdentifierFirstRep().getType().getCoding().size());
        assertEquals("FILL", diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getCode());
        assertEquals("http://terminology.hl7.org/CodeSystem/v2-0203",
                     diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getSystem());
        assertEquals("bericht_id", diagnosticReport.getIdentifierFirstRep().getValue());

        // Assert Specimen - name: "specimen"
        Specimen specimen = (Specimen) diagnosticReport.getSpecimenFirstRep().getResource();

        // - name: "identifier"
        assertEquals("SP-987654", specimen.getIdentifierFirstRep().getValue());

        //  - name: "collected"
        assertEquals("2022-02-03T04:05:06+01:00",
                     specimen.getCollection().getCollectedDateTimeType().getValueAsString());

        //  - name: "collector"
        assertEquals("collectorId", specimen.getCollection().getCollector().getIdentifier().getValue());

        //  - name: "specimen type"
        assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());

        //  - name: "specimenCollectionMethod"
        assertEquals("Aspiration - action", specimen.getCollection().getMethod().getText());

        //  specimenCollectionBodySite
        assertEquals("Arm", specimen.getCollection().getBodySite().getText());

        // - name: "samplingContext"
        assertEquals("Lorem ipsum", specimen.getCollection().getFastingStatusCodeableConcept().getText());

        // - name: "type"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());
        Assert.assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());

        //   - name: "note"
        assertEquals("Sample collected in the morning.", specimen.getNoteFirstRep().getText());

        // - name: "descriptionOfSpecimen"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0493' available",
                specimen.getConditionFirstRep().getText());
        Assert.assertEquals("conditionCode", specimen.getConditionFirstRep().getCodingFirstRep().getCode());

        // - name: "identifierOfSpecimen"
        assertEquals("identifierOfSpecimen", specimen.getAccessionIdentifier().getValue());

        // - name: "dateReceived"
        assertEquals("2022-02-03T04:05:06+01:00", specimen.getReceivedTimeElement().getValueAsString());

        // specimen - name: "status"
        assertEquals("at0062", specimen.getStatusElement().getValueAsString());

        // basedOn, identifierInReference
        assertEquals("identifikation_der_laboranforderung",
                     diagnosticReport.getBasedOnFirstRep().getIdentifier().getValue());


        // Assert Observation  - name: "result"
        Observation observation = (Observation) diagnosticReport.getResultFirstRep().getResource();

        // - name: "status"
        Assert.assertEquals("at0015", observation.getStatusElement().getValueAsString());

        // - name: "issued"
        Assert.assertEquals("2022-02-03T04:05:06.000+01:00", observation.getIssuedElement().getValueAsString());

        //     - name: "analyteMeasurement"
        assertEquals(7.4, observation.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("mm", observation.getValueQuantity().getUnit());

        // laboryte name
        assertEquals("718-7", observation.getCode().getCodingFirstRep().getCode());
        assertEquals(
                "//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips",
                observation.getCode().getCodingFirstRep().getSystem());
        assertEquals("Hemoglobin [Mass/volume] in Blood", observation.getCode().getText());

        // - name: "interpretation"
        assertEquals("142", observation.getInterpretationFirstRep().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/observation-interpretation' available",
                observation.getInterpretationFirstRep().getText());

        // - name: "testmethod"
        assertEquals("testmethode", observation.getMethod().getText());

        // - name: "comment"
        assertEquals("komm", observation.getNoteFirstRep().getText());
    }

    @Test
    public void toFhir_multiples() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + "KDS_Laborbericht_multiples.flat.json"),
                new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allDiagnosticReports = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof DiagnosticReport).collect(Collectors.toList());
        assertEquals(1, allDiagnosticReports.size());

        final DiagnosticReport diagnosticReport = (DiagnosticReport) allDiagnosticReports.get(0).getResource();

        Assert.assertEquals(2, diagnosticReport.getSpecimen().size());
        Assert.assertEquals(2, diagnosticReport.getResult().size());


        //- name: "healthCareFacility"
//        - name: "composer"
        Assert.assertEquals(2, diagnosticReport.getPerformer().size());
        Assert.assertEquals("DOE, John", diagnosticReport.getPerformer().get(0).getDisplay());
        Assert.assertEquals("Max Mustermann", diagnosticReport.getPerformer().get(1).getDisplay());

        //   - name: "Effective"
        assertEquals("2020-02-03T04:05:06+01:00",
                     diagnosticReport.getEffectivePeriod().getStartElement().getValueAsString());
        assertEquals("2022-02-03T04:05:06+01:00",
                     diagnosticReport.getEffectivePeriod().getEndElement().getValueAsString());

        // - name: "Category"
        assertEquals(1, diagnosticReport.getCategory().size());
        assertEquals("LOINC", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getSystem());
        assertEquals("26436-6", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertEquals("laboratory", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getDisplay());

        //  - name: "Status"
        assertEquals("registered", diagnosticReport.getStatusElement().getValueAsString());

        //  - name: "Conclusion"
        assertEquals("Normal blood count", diagnosticReport.getConclusion());

        // - name: "issued"
        assertEquals("2022-02-03T04:05:06.000+01:00", diagnosticReport.getIssuedElement().getValueAsString());

        // - name: "berichtId"
        assertEquals(1, diagnosticReport.getIdentifierFirstRep().getType().getCoding().size());
        assertEquals("FILL", diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getCode());
        assertEquals("http://terminology.hl7.org/CodeSystem/v2-0203",
                     diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getSystem());
        assertEquals("bericht_id", diagnosticReport.getIdentifierFirstRep().getValue());

        // Assert Specimen - name: "specimen"
        Specimen specimen = (Specimen) diagnosticReport.getSpecimenFirstRep().getResource();

        // - name: "identifier"
        assertEquals("SP-987654", specimen.getIdentifierFirstRep().getValue());

        //  - name: "collected"
        assertEquals("2022-02-03T04:05:06+01:00",
                     specimen.getCollection().getCollectedDateTimeType().getValueAsString());

        //  - name: "collector"
        assertEquals("collectorId", specimen.getCollection().getCollector().getIdentifier().getValue());

        //  - name: "specimen type"
        assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());

        //  - name: "specimenCollectionMethod"
        assertEquals("Aspiration - action", specimen.getCollection().getMethod().getText());

        //  specimenCollectionBodySite
        assertEquals("Arm", specimen.getCollection().getBodySite().getText());

        // - name: "samplingContext"
        assertEquals("Lorem ipsum", specimen.getCollection().getFastingStatusCodeableConcept().getText());

        // - name: "type"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());
        Assert.assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());

        //   - name: "note"
        assertEquals("Sample collected in the morning.", specimen.getNoteFirstRep().getText());

        // - name: "descriptionOfSpecimen"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0493' available",
                specimen.getConditionFirstRep().getText());
        Assert.assertEquals("conditionCode", specimen.getConditionFirstRep().getCodingFirstRep().getCode());

        // - name: "identifierOfSpecimen"
        assertEquals("identifierOfSpecimen", specimen.getAccessionIdentifier().getValue());

        // - name: "dateReceived"
        assertEquals("2022-02-03T04:05:06+01:00", specimen.getReceivedTimeElement().getValueAsString());

        // specimen - name: "status"
        assertEquals("at0062", specimen.getStatusElement().getValueAsString());

        Specimen specimen1 = (Specimen) diagnosticReport.getSpecimen().get(1).getResource();

        // - name: "identifier"
        assertEquals("1_SP-987654", specimen1.getIdentifierFirstRep().getValue());

        //  - name: "collected"
        assertEquals("3022-02-03T04:05:06+01:00",
                     specimen1.getCollection().getCollectedDateTimeType().getValueAsString());

        //  - name: "collector"
        assertEquals("1_collectorId", specimen1.getCollection().getCollector().getIdentifier().getValue());

        //  - name: "specimen type"
        assertEquals("1_probenartcode", specimen1.getType().getCodingFirstRep().getCode());

        //  - name: "specimenCollectionMethod"
        assertEquals("1_Aspiration - action", specimen1.getCollection().getMethod().getText());

        //  specimenCollectionBodySite
        assertEquals("1_Arm", specimen1.getCollection().getBodySite().getText());

        // - name: "samplingContext"
        assertEquals("1_Lorem ipsum", specimen1.getCollection().getFastingStatusCodeableConcept().getText());

        // - name: "type"
        Assert.assertEquals("1_probenartcode", specimen1.getType().getCodingFirstRep().getCode());

        //   - name: "note"
        assertEquals("1_Sample collected in the morning.", specimen1.getNoteFirstRep().getText());

        // - name: "descriptionOfSpecimen"
        Assert.assertEquals("1_conditionCode", specimen1.getConditionFirstRep().getCodingFirstRep().getCode());

        // - name: "identifierOfSpecimen"
        assertEquals("1_identifierOfSpecimen", specimen1.getAccessionIdentifier().getValue());

        // - name: "dateReceived"
        assertEquals("3022-02-03T04:05:06+01:00", specimen1.getReceivedTimeElement().getValueAsString());

        // specimen - name: "status"
        assertEquals("1_at0062", specimen1.getStatusElement().getValueAsString());


        // basedOn, identifierInReference
        assertEquals("identifikation_der_laboranforderung",
                     diagnosticReport.getBasedOnFirstRep().getIdentifier().getValue());


        // Assert Observation  - name: "result"
        Observation observation = (Observation) diagnosticReport.getResultFirstRep().getResource();

        // - name: "status"
        Assert.assertEquals("at0015", observation.getStatusElement().getValueAsString());

        // - name: "issued"
        Assert.assertEquals("2022-02-03T04:05:06.000+01:00", observation.getIssuedElement().getValueAsString());

        //     - name: "analyteMeasurement"
        assertEquals(7.4, observation.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("mm", observation.getValueQuantity().getUnit());

        // laboryte name
        assertEquals("718-7", observation.getCode().getCodingFirstRep().getCode());
        assertEquals(
                "//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips",
                observation.getCode().getCodingFirstRep().getSystem());
        assertEquals("Hemoglobin [Mass/volume] in Blood", observation.getCode().getText());

        // - name: "interpretation"
        assertEquals("142", observation.getInterpretationFirstRep().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/observation-interpretation' available",
                observation.getInterpretationFirstRep().getText());

        // - name: "testmethod"
        assertEquals("testmethode", observation.getMethod().getText());

        // - name: "comment"
        assertEquals("komm", observation.getNoteFirstRep().getText());

        Observation observation1 = (Observation) diagnosticReport.getResult().get(1).getResource();

        // - name: "status"
        Assert.assertEquals("1_at0015", observation1.getStatusElement().getValueAsString());

        // - name: "issued"
        Assert.assertEquals("3022-02-03T04:05:06.000+01:00", observation1.getIssuedElement().getValueAsString());

        //     - name: "analyteMeasurement"
        assertEquals(8.4, observation1.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("1_mm", observation1.getValueQuantity().getUnit());

        // laboryte name
        assertEquals("1_718-7", observation1.getCode().getCodingFirstRep().getCode());
        assertEquals("1_Hemoglobin [Mass/volume] in Blood", observation1.getCode().getText());

        // - name: "interpretation"
        assertEquals("1_142", observation1.getInterpretationFirstRep().getCodingFirstRep().getCode());

        // - name: "testmethod"
        assertEquals("1_testmethode", observation1.getMethod().getText());

        // - name: "comment"
        assertEquals("1_komm", observation1.getNoteFirstRep().getText());
    }
}
