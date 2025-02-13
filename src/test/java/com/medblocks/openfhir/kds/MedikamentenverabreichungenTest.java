package com.medblocks.openfhir.kds;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.Period;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class MedikamentenverabreichungenTest extends KdsBidirectionalTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT = "/kds_new/projects/org.highmed/KDS/medikationsverabreichung/KDS_medikationseintrag.context.yaml";
    final String HELPER_LOCATION = "/kds/medikationsverabreichung/";
    final String OPT = "KDS_Medikamentenverabreichungen.opt";
    final String FLAT = "KDS_Medikamentenverabreichungen.flat.json";

    final String BUNDLE = "KDS_Medikamentenverabreichungen_Bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhir() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT), webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<MedicationAdministration> administrations = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof MedicationAdministration)
                .map(en -> (MedicationAdministration) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, administrations.size());

        final MedicationAdministration medicationAdministration = administrations.get(0);
        final Medication medicationResource = (Medication) medicationAdministration.getMedicationReference().getResource();

        Assert.assertEquals("arzneimittel-name", medicationResource.getCode().getText());

        Assert.assertEquals("in-progress", medicationAdministration.getStatusElement().getValueAsString());
//        Assert.assertEquals("context reference encounter 123", medicationAdministration.getContext().getReference());

        final Period effectivePeriod = medicationAdministration.getEffectivePeriod();
        Assert.assertEquals("2022-02-03T04:05:06+01:00", effectivePeriod.getStartElement().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", effectivePeriod.getEndElement().getValueAsString());

        Assert.assertEquals("Admin note comment", medicationAdministration.getNoteFirstRep().getText());

        final MedicationAdministration.MedicationAdministrationDosageComponent dosage = medicationAdministration.getDosage();
        Assert.assertEquals("20 mg orally once daily", dosage.getText());
//        Assert.assertEquals("1", dosage.getDose().getUnit());
        Assert.assertEquals("22.0", dosage.getDose().getValue().toPlainString());

        Assert.assertEquals("route42", dosage.getRoute().getCodingFirstRep().getCode());

        Assert.assertEquals("SiteDisplay", dosage.getSite().getText());
        Assert.assertEquals("siteCode", dosage.getSite().getCodingFirstRep().getCode());
        Assert.assertEquals("//snomed.info/sct", dosage.getSite().getCodingFirstRep().getSystem());

        Assert.assertEquals("21.0", dosage.getRateQuantity().getValue().toPlainString());
        Assert.assertEquals("l/h", dosage.getRateQuantity().getUnit());

        Assert.assertEquals("Reason code", medicationAdministration.getReasonCodeFirstRep().getText());
        Assert.assertEquals("dev/null", medicationAdministration.getRequest().getIdentifier().getValue());
    }



    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        // status
        Assert.assertEquals("completed", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/status|code").getAsString());

        // berichtId
        Assert.assertEquals("MA123456", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/bericht_id").getAsString());

        // reason code
        Assert.assertEquals("Hypertensive disorder, systemic arterial", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/klinische_indikation").getAsString());

        // route
        Assert.assertEquals("26643006", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/details_zur_verabreichung/verabreichungsweg|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/details_zur_verabreichung/verabreichungsweg|terminology").getAsString());
        Assert.assertEquals("Oral", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/details_zur_verabreichung/verabreichungsweg|value").getAsString());

        //  - name: "category"
        Assert.assertEquals("228", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/setting|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/setting|terminology").getAsString());
        Assert.assertEquals("Inpatient", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/setting|value").getAsString());

        // - name: "note"
        Assert.assertEquals("textab", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/kommentar").getAsString());

        // dosage text
        Assert.assertEquals("20 mg orally once daily", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/dosierung/dosierung_freitext").getAsString());

        // dose value
        Assert.assertEquals(250.0, jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/dosierung/dosis|magnitude").getAsDouble(), 0);
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/dosierung/dosis|unit").getAsString());

        // name
        Assert.assertEquals("Paracetamol 500mg tablet", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/arzneimittel-name").getAsString());

        // form
        Assert.assertEquals("UTA", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/darreichungsform|code").getAsString());
        Assert.assertEquals("https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_BMP_DARREICHUNGSFORM", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/darreichungsform|terminology").getAsString());

        //- name: "klinischeIndikation"
        Assert.assertEquals("Hypertensive disorder, systemic arterial", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/klinische_indikation").getAsString());

        return jsonObject;
    }
}
