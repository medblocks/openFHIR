package com.medblocks.openfhir.kds;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
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

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@Ignore
public class LaborberichtTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/laborbericht/";
    final String OPT = "KDS_Laborbericht.opt";
    final String FLAT = "KDS_Laborbericht.flat.json";
    final String CONTEXT = "laborbericht.context.yaml";
    final String BUNDLE = "KDS_Laborbericht_bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(RESOURCES_ROOT + CONTEXT);
        repo.initRepository(context, getClass().getResource(RESOURCES_ROOT).getFile());
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(RESOURCES_ROOT + OPT));
        operationaltemplate = getOperationalTemplate();
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("26436-6", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/labortest-kategorie|code").getAsString());
        Assert.assertEquals("LOINC", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/labortest-kategorie|terminology").getAsString());
        Assert.assertEquals("Normal blood count", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/conclusion").getAsString());
        Assert.assertEquals("2024-08-22T10:30:00", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/time").getAsString());
        Assert.assertEquals("122555007", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/specimen_type|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/specimen_type|terminology").getAsString());
        Assert.assertEquals("Venous blood specimen", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/specimen_type|value").getAsString());
        Assert.assertEquals("Sample collected in the morning.", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/comment").getAsString());
        Assert.assertEquals("SP-987654", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/laboratory_specimen_identifier|id").getAsString());
        Assert.assertEquals("2024-08-24T11:00:00", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/collection_date_time/date_time_value").getAsString());
        Assert.assertEquals("Aspiration", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/collection_method").getAsString());
        Assert.assertEquals("Right arm", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/körperstelle").getAsString());
        Assert.assertEquals("1234567", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/probenmaterial:0/specimen_collector_identifier|id").getAsString());
        Assert.assertEquals("7.4", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/pro_laboranalyt:0/messwert:0/quantity_value|magnitude").getAsString());
        Assert.assertEquals("g/dL", jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/pro_laboranalyt:0/messwert:0/quantity_value|unit").getAsString());

        return jsonObject;
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allDiagnosticReports = bundle.getEntry().stream().filter(en -> en.getResource() instanceof DiagnosticReport).collect(Collectors.toList());
        assertEquals(1, allDiagnosticReports.size());

        final DiagnosticReport diagnosticReport = (DiagnosticReport) allDiagnosticReports.get(0).getResource();

        //  - name: "Conclusion"
        assertEquals("Normal blood count", diagnosticReport.getConclusion());

        //  - name: "Status"
        assertEquals("Final", diagnosticReport.getStatusElement().getValueAsString());

        //   - name: "Effective"
        assertEquals("2022-02-03T04:05:06+01:00", diagnosticReport.getEffectiveDateTimeType().getValueAsString());

        // - name: "Category"
        assertEquals(1, diagnosticReport.getCategory().size());
        assertEquals("LOINC", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getSystem());
        assertEquals("26436-6", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertEquals("laboratory", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getDisplay());

        // Assert Specimen - name: "specimen"
        Specimen specimen = (Specimen) diagnosticReport.getSpecimenFirstRep().getResource();

        // - name: "specimen identifier"
        assertEquals("SP-987654", specimen.getIdentifierFirstRep().getValue());

        //  - name: "specimen type"
        assertEquals("122555007", specimen.getType().getCodingFirstRep().getCode());
        assertEquals("Venous blood specimen", specimen.getType().getText());

        //  - name: "specimen collection"
        //   - name: "specimen collector"
        assertEquals("1234567", specimen.getCollection().getCollector().getIdentifier().getValue());
        //  - name: "specimen collection date time"
        assertEquals("2022-02-03T04:05:06+01:00", specimen.getCollection().getCollectedDateTimeType().getValueAsString());

        //  - name: "specimen collection method"
        assertEquals("Aspiration - action", specimen.getCollection().getMethod().getText());

        //  - name: "specimen collection body site"
        assertEquals("Arm", specimen.getCollection().getBodySite().getText());

        //   - name: "specimen note"
        assertEquals("Sample collected in the morning.", specimen.getNoteFirstRep().getText());

        // Assert Observation  - name: "result"
        Observation observation = (Observation) diagnosticReport.getResultFirstRep().getResource();

        //     - name: "value"
        assertEquals(7.4, observation.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("g/dL", observation.getValueQuantity().getUnit());

        // laboryte name
        assertEquals("718-7", observation.getCode().getCodingFirstRep().getCode());
        assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips", observation.getCode().getCodingFirstRep().getSystem());
        assertEquals("Hemoglobin [Mass/volume] in Blood", observation.getCode().getText());
    }

    @Test
    public void toFhir_multiples() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + "KDS_Laborbericht_multiples.flat.json"), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allDiagnosticReports = bundle.getEntry().stream().filter(en -> en.getResource() instanceof DiagnosticReport).collect(Collectors.toList());
        assertEquals(1, allDiagnosticReports.size());

        final DiagnosticReport diagnosticReport = (DiagnosticReport) allDiagnosticReports.get(0).getResource();

        Assert.assertEquals(2, diagnosticReport.getSpecimen().size());
        Assert.assertEquals(2, diagnosticReport.getResult().size());

        //  - name: "Conclusion"
        assertEquals("Normal blood count", diagnosticReport.getConclusion());

        //   - name: "Effective"
        assertEquals("2022-02-03T04:05:06+01:00", diagnosticReport.getEffectiveDateTimeType().getValueAsString());

        // - name: "Category"
        assertEquals(1, diagnosticReport.getCategory().size());
        assertEquals("LOINC", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getSystem());
        assertEquals("26436-6", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertEquals("laboratory", diagnosticReport.getCategoryFirstRep().getCodingFirstRep().getDisplay());

        // Assert Specimen - name: "specimen"
        Specimen specimen = (Specimen) diagnosticReport.getSpecimenFirstRep().getResource();

        // - name: "specimen identifier"
        assertEquals("SP-987654", specimen.getIdentifierFirstRep().getValue());

        //  - name: "specimen type"
        assertEquals("122555007", specimen.getType().getCodingFirstRep().getCode());
        assertEquals("Venous blood specimen", specimen.getType().getText());

        //  - name: "specimen collection"
        //   - name: "specimen collector"
        assertEquals("1234567", specimen.getCollection().getCollector().getIdentifier().getValue());
        //  - name: "specimen collection date time"
        assertEquals("2022-02-03T04:05:06+01:00", specimen.getCollection().getCollectedDateTimeType().getValueAsString());

        //  - name: "specimen collection method"
        assertEquals("Aspiration - action", specimen.getCollection().getMethod().getText());

        //  - name: "specimen collection body site"
        assertEquals("Arm", specimen.getCollection().getBodySite().getText());

        //   - name: "specimen note"
        assertEquals("Sample collected in the morning.", specimen.getNoteFirstRep().getText());

        // Assert Specimen - name: "specimen"
        Specimen specimen1 = (Specimen) diagnosticReport.getSpecimen().get(1).getResource();

        // - name: "specimen identifier"
        assertEquals("1_SP-987654", specimen1.getIdentifierFirstRep().getValue());

        //  - name: "specimen type"
        assertEquals("1_122555007", specimen1.getType().getCodingFirstRep().getCode());
        assertEquals("1_Venous blood specimen", specimen1.getType().getText());

        //  - name: "specimen collection"
        //   - name: "specimen collector"
        assertEquals("1_1234567", specimen1.getCollection().getCollector().getIdentifier().getValue());
        //  - name: "specimen collection date time"
        assertEquals("2025-02-03T04:05:06+01:00", specimen1.getCollection().getCollectedDateTimeType().getValueAsString());

        //  - name: "specimen collection method"
        assertEquals("1_Aspiration - action", specimen1.getCollection().getMethod().getText());

        //  - name: "specimen collection body site"
        assertEquals("1_Arm", specimen1.getCollection().getBodySite().getText());

        //   - name: "specimen note"
        assertEquals("1_Sample collected in the morning.", specimen1.getNoteFirstRep().getText());

        // Assert Observation  - name: "result"
        Observation observation = (Observation) diagnosticReport.getResultFirstRep().getResource();

        //     - name: "value"
        assertEquals(8.4, observation.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("g/dL", observation.getValueQuantity().getUnit());

        // Assert Observation  - name: "result"
        Observation observation1 = (Observation) diagnosticReport.getResult().get(1).getResource();

        //     - name: "value"
        assertEquals(7.4, observation1.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("g/dL", observation1.getValueQuantity().getUnit());
    }
}
