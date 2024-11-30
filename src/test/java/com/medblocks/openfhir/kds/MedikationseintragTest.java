package com.medblocks.openfhir.kds;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

public class MedikationseintragTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/medikationseintrag/";
    final String OPT = "KDS_Medikationseintrag_v1.opt";
    final String FLAT = "KDSMedicationRequest.flat.json";
    final String CONTEXT = "context.KDS_medikationseintrag.yaml";
    final String BUNDLE = "KDS_Medikationseintrag_v1-Fhir-Bundle-input.json";

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
    public void kdsMedicationList_toFhir() throws IOException {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT), webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<MedicationStatement> requests = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof MedicationStatement)
                .map(en -> (MedicationStatement) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(2, requests.size());

        final MedicationStatement req1 = requests.get(0);
        final MedicationStatement req2 = requests.get(1);

        Assert.assertEquals("medication statement 2 description", req1.getNoteFirstRep().getText());
        Assert.assertEquals("medication statement 1 description", req2.getNoteFirstRep().getText());

        Assert.assertEquals("Thu Feb 03 04:05:06 CET 2022", req2.getEffectivePeriod().getStart().toString());
        Assert.assertEquals("Fri Feb 04 04:05:06 CET 2022", req2.getEffectivePeriod().getEnd().toString());

        Assert.assertEquals("Fri Feb 03 04:05:06 CET 2023", req1.getEffectivePeriod().getStart().toString());

        Assert.assertEquals("dev/null", req2.getIdentifierFirstRep().getValue());
        Assert.assertEquals("external identifier", req2.getIdentifierFirstRep().getSystem());

        Assert.assertEquals("identifier2", req2.getIdentifier().get(1).getValue());
        Assert.assertEquals("external identifier", req2.getIdentifier().get(1).getSystem());

        Assert.assertEquals("2identifier2", req1.getIdentifier().get(0).getValue());
        Assert.assertEquals("external identifier", req1.getIdentifier().get(0).getSystem());

        Assert.assertEquals(2, req2.getIdentifier().size());
        Assert.assertEquals(1, req1.getIdentifier().size());

        final List<Dosage> req2Dosages = req2.getDosage();


        Assert.assertEquals("04:05:06", new SimpleDateFormat("HH:mm:ss").format(req2.getDosageFirstRep().getTiming().getEvent().get(0).getValue()));
//        Assert.assertEquals("when time of day ereignis", req2.getDosageFirstRep().getTiming().getRepeat().getWhen().get(0));
        Assert.assertEquals("PT0S", req2.getDosageFirstRep().getTiming().getRepeat().getTimeOfDay().get(0).getValue());

        Assert.assertEquals(3, req2Dosages.size());
        Assert.assertEquals("structured dosage text", req2Dosages.get(0).getText());
        Assert.assertEquals("mm", req2Dosages.get(0).getDoseAndRateFirstRep().getDoseQuantity().getUnit());
        Assert.assertEquals("22.0", req2Dosages.get(0).getDoseAndRateFirstRep().getDoseQuantity().getValue().toPlainString());
        Assert.assertEquals(22, req2Dosages.get(0).getSequence());

        Assert.assertEquals("structured dosage 2 text", req2Dosages.get(1).getText());
        Assert.assertEquals("mm1", req2Dosages.get(1).getDoseAndRateFirstRep().getDoseQuantity().getUnit());
        Assert.assertEquals("23.0", req2Dosages.get(1).getDoseAndRateFirstRep().getDoseQuantity().getValue().toPlainString());
        Assert.assertEquals(23, req2Dosages.get(1).getSequence());

        Assert.assertEquals("unstructured dosage", req2Dosages.get(2).getText());
        Assert.assertEquals(0, req2Dosages.get(2).getSequence());
        Assert.assertEquals(0, req2Dosages.get(2).getDoseAndRate().size());

        Assert.assertEquals(true, req2.getDosageFirstRep().getAsNeededBooleanType().getValue());
        Assert.assertNull(req1.getDosageFirstRep().getAsNeededBooleanType().getValue());

        final Medication med1 = (Medication) req1.getMedicationReference().getResource();
        final Medication med2 = (Medication) req2.getMedicationReference().getResource();
        Assert.assertEquals("req0, medication code text", med2.getCode().getText());
        Assert.assertEquals("42", med2.getForm().getCodingFirstRep().getCode());
        Assert.assertNull(med1.getForm().getCodingFirstRep().getCode());
        Assert.assertEquals("req1, medication code text", med1.getCode().getText());
        Assert.assertEquals("25.0", med1.getAmount().getNumerator().getValue().toPlainString());
        Assert.assertEquals("mm", med1.getAmount().getNumerator().getUnit());
        Assert.assertEquals("20.0", med2.getAmount().getNumerator().getValue().toPlainString());
        Assert.assertEquals("mm", med2.getAmount().getNumerator().getUnit());

        Assert.assertEquals(3, med2.getIngredient().size());
        Assert.assertEquals(2, med1.getIngredient().size());
        Assert.assertEquals("ingridient item 0, 0", med2.getIngredient().get(0).getItemCodeableConcept().getText());
        Assert.assertEquals("ingridient item 0, 1", med2.getIngredient().get(1).getItemCodeableConcept().getText());

        Assert.assertEquals("ingridient item 1, 0", med1.getIngredient().get(0).getItemCodeableConcept().getText());
        Assert.assertEquals("ingridient item 1, 1", med1.getIngredient().get(1).getItemCodeableConcept().getText());

        final Medication.MedicationIngredientComponent ingridient00 = med2.getIngredient().stream()
                .filter(ing -> "ingridient item 0, 0".equals(ing.getItemCodeableConcept().getText()))
                .findAny()
                .orElse(null);

        final Medication.MedicationIngredientComponent ingridient01 = med2.getIngredient().stream()
                .filter(ing -> "ingridient item 0, 1".equals(ing.getItemCodeableConcept().getText()))
                .findAny()
                .orElse(null);

        final Medication.MedicationIngredientComponent ingridient0Empty = med2.getIngredient().stream()
                .filter(ing -> ing.getItemCodeableConcept().getText() == null)
                .findAny()
                .orElse(null);

        final Medication.MedicationIngredientComponent ingridient10 = med1.getIngredient().stream()
                .filter(ing -> "ingridient item 1, 0".equals(ing.getItemCodeableConcept().getText()))
                .findAny()
                .orElse(null);

        final Ratio zerothIngridientStrenght = ingridient00.getStrength();
        final Ratio firstIngridientStrenght = ingridient01.getStrength();

        final Ratio secondIngridientStrenght = med2.getIngredient().stream()
                .filter(ing -> ing.getItemCodeableConcept().getText() == null)
                .map(ing -> ing.getStrength())
                .findAny()
                .orElse(null);

        Assert.assertEquals("10.0", zerothIngridientStrenght.getNumerator().getValue().toPlainString());
        Assert.assertEquals("11.0", zerothIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("1mm", zerothIngridientStrenght.getNumerator().getUnit());
        Assert.assertEquals("2mm", zerothIngridientStrenght.getDenominator().getUnit());

        Assert.assertEquals("20.0", firstIngridientStrenght.getNumerator().getValue().toPlainString());
        Assert.assertEquals("21.0", firstIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("3mm", firstIngridientStrenght.getNumerator().getUnit());
        Assert.assertEquals("4mm", firstIngridientStrenght.getDenominator().getUnit());

        Assert.assertEquals("30.0", secondIngridientStrenght.getNumerator().getValue().toPlainString());
        Assert.assertEquals("31.0", secondIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("5mm", secondIngridientStrenght.getNumerator().getUnit());
        Assert.assertEquals("6mm", secondIngridientStrenght.getDenominator().getUnit());

        final Ratio firstZerothIngridientStrenght = med1.getIngredient().stream()
                .filter(ing -> "ingridient item 1, 0".equals(ing.getItemCodeableConcept().getText()))
                .map(ing -> ing.getStrength())
                .findAny()
                .orElse(null);

        final Ratio firstFirstIngridientStrenght = med1.getIngredient().stream()
                .filter(ing -> "ingridient item 1, 1".equals(ing.getItemCodeableConcept().getText()))
                .map(ing -> ing.getStrength())
                .findAny()
                .orElse(null);

        Assert.assertEquals("40.0", firstZerothIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("mm", firstZerothIngridientStrenght.getDenominator().getUnit());

        Assert.assertEquals("41.0", firstFirstIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("mm", firstFirstIngridientStrenght.getDenominator().getUnit());

        Assert.assertEquals("at0143", ingridient00.getItemCodeableConcept().getCodingFirstRep().getCode());
        Assert.assertEquals("Ad-hoc Mixtur", ingridient00.getItemCodeableConcept().getCodingFirstRep().getDisplay());

        Assert.assertEquals("at3143", ingridient0Empty.getItemCodeableConcept().getCodingFirstRep().getCode());
        Assert.assertEquals("3Ad-hoc Mixtur", ingridient0Empty.getItemCodeableConcept().getCodingFirstRep().getDisplay());

        Assert.assertEquals("at0243", ingridient10.getItemCodeableConcept().getCodingFirstRep().getCode());
        Assert.assertEquals("2Ad-hoc Mixtur", ingridient10.getItemCodeableConcept().getCodingFirstRep().getDisplay());
    }


    public JsonObject toOpenEhr() {
        final Bundle testBundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, getClass().getResourceAsStream(RESOURCES_ROOT + BUNDLE));

        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("Take 1 tablet every 6 hours as needed for pain", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung2:0/dosierung_freitext").getAsString());
        Assert.assertEquals("500.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung2:0/dosis/quantity_value|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung2:0/dosis/quantity_value|unit").getAsString());
        Assert.assertEquals("Take 1 tablet every 6 hours as needed for pain", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung:0").getAsString());
        Assert.assertEquals("Paracetamol 500mg tablet", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/arzneimittel-name").getAsString());
        Assert.assertEquals("385055001", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/darreichungsform|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/darreichungsform|terminology").getAsString());
        Assert.assertEquals("Paracetamol", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("500.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/zähler|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/zähler|unit").getAsString());
        Assert.assertEquals("1.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/nenner|magnitude").getAsString());
        Assert.assertEquals("tablet", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/nenner|unit").getAsString());
        Assert.assertEquals("11Paracetamol", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("1500.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/zähler|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/zähler|unit").getAsString());
        Assert.assertEquals("11.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/nenner|magnitude").getAsString());
        Assert.assertEquals("tablet", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/nenner|unit").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:2/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:2/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("Take 1 capsule daily", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung2:0/dosierung_freitext").getAsString());
        Assert.assertEquals("5.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung2:0/dosis/quantity_value|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung2:0/dosis/quantity_value|unit").getAsString());
        Assert.assertEquals("Take 1 capsule daily", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung:0").getAsString());
        Assert.assertEquals("Ramipril 5mg capsule", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/arzneimittel/arzneimittel-name").getAsString());

        return jsonObject;
    }
}
