package com.medblocks.openfhir.kds;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

@Ignore
public class MedikamentenverabreichungenTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/medikamentenverabreichungen/";
    final String OPT = "KDS_Medikamentenverabreichungen.opt";
    final String FLAT = "KDS_Medikamentenverabreichungen.flat.json";
    final String CONTEXT = "medikamentenverabreichungen.context.yaml";
    final String BUNDLE = "KDS_Medikamentenverabreichungen_Bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(RESOURCES_ROOT + CONTEXT);
        repo.initRepository(context, getClass().getResource(RESOURCES_ROOT).getFile());
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(RESOURCES_ROOT + OPT));
        operationaltemplate = getOperationalTemplate();
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhir() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT), webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<MedicationAdministration> administrations = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof MedicationAdministration)
                .map(en -> (MedicationAdministration) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, administrations.size());

        final MedicationAdministration medicationAdministration = administrations.get(0);
        Assert.assertEquals("Aspirin 81 MG Oral Tablet", medicationAdministration.getMedicationCodeableConcept().getText());
        Assert.assertEquals("in-progress", medicationAdministration.getStatusElement().getValueAsString());
        Assert.assertEquals("context reference encounter 123", medicationAdministration.getContext().getReference());
        Assert.assertTrue(medicationAdministration.getEffectiveDateTimeType().getValueAsString().contains("04:05:06")); // it adds today's date by deafult because there's a discrepancy between TIME in openEHR and DATETIME in FHIR

        Assert.assertEquals("Admin note comment", medicationAdministration.getNoteFirstRep().getText());

        final MedicationAdministration.MedicationAdministrationDosageComponent dosage = medicationAdministration.getDosage();
        Assert.assertEquals("20 mg orally once daily", dosage.getText());
        Assert.assertEquals("1", dosage.getDose().getUnit());
        Assert.assertEquals("22.0", dosage.getDose().getValue().toPlainString());

        Assert.assertEquals("Oral route", dosage.getRoute().getText());
        Assert.assertEquals("26643006", dosage.getRoute().getCodingFirstRep().getCode());
        Assert.assertEquals("http://snomed.info/sct", dosage.getRoute().getCodingFirstRep().getSystem());

        Assert.assertEquals("Oral route", dosage.getSite().getText());
        Assert.assertEquals("26643006", dosage.getSite().getCodingFirstRep().getCode());
        Assert.assertEquals("http://snomed.info/sct", dosage.getSite().getCodingFirstRep().getSystem());

        Assert.assertEquals("250.0", dosage.getRateRatio().getNumerator().getValue().toPlainString());
        Assert.assertEquals("1", dosage.getRateRatio().getNumerator().getUnit());

        Assert.assertEquals("8.0", dosage.getRateRatio().getDenominator().getValue().toPlainString());
        Assert.assertEquals("1", dosage.getRateRatio().getDenominator().getUnit());

        Assert.assertEquals("Reason code", medicationAdministration.getReasonCodeFirstRep().getText());
    }



    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        // status
        Assert.assertEquals("completed", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/status|code").getAsString());

        // context
        Assert.assertEquals("Encounter/encounter-12345", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/fallidentifikation/fall-kennung").getAsString());

        // effective
        Assert.assertEquals("16:30:00", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/dosierung/zeitablauf_-_täglich/verabreichungszeitpunkt_-intervall/time_value").getAsString());

        // reason code
        Assert.assertEquals("Hypertensive disorder, systemic arterial", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/klinische_indikation").getAsString());

        // route
        Assert.assertEquals("26643006", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoff-code_ask_snomed_ct_unii_cas/wirkstoff-code|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoff-code_ask_snomed_ct_unii_cas/wirkstoff-code|terminology").getAsString());
        Assert.assertEquals("Oral", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoff-code_ask_snomed_ct_unii_cas/wirkstoff-code|value").getAsString());

        // dose ratio
        Assert.assertEquals(250.0, jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/zähler|magnitude").getAsDouble(), 0);
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/zähler|unit").getAsString());

        Assert.assertEquals(8.0, jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/nenner|magnitude").getAsDouble(), 0);
        Assert.assertEquals("h", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/nenner|unit").getAsString());

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

        // wirkstofftyp
        Assert.assertEquals("Active", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstofftyp").getAsString());

        // wirkstoffmenge this is one of the open topics in excel, if wirkstoffmenge should be mapped from referenced Medication, this should assert to true, but then line 99+ will fail
//        Assert.assertEquals("500", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/zähler|magnitude").getAsString());
//        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/zähler|unit").getAsString());
//        Assert.assertEquals("1", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/nenner|magnitude").getAsString());
//        Assert.assertEquals("tablet", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/wirkstoff/wirkstoffmenge/nenner|unit").getAsString());

        return jsonObject;
    }
}
