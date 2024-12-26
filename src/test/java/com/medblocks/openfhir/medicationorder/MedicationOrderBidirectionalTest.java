package com.medblocks.openfhir.medicationorder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.GenericTest;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.nedap.archie.rm.datavalues.quantity.datetime.DvDateTime;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Test;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

public class MedicationOrderBidirectionalTest extends GenericTest {

    final String MODEL_MAPPINGS = "/medication_order/";
    final String CONTEXT_MAPPING = "/medication_order/medication-order.context.yml";
    final String HELPER_LOCATION = "/medication_order/";
    final String OPT = "medication order.opt";
    final String FLAT = "medication_order_flat.json";

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
    public void medicationOrderToFhirToOpenEhr() throws IOException {
        // have a composition from config
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             webTemplate);

        // transform it to FHIR
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);

        // fix references; I think this is only the case for testing, otherwise references should be intact
        for (Bundle.BundleEntryComponent bundleEntryComponent : bundle.getEntry()) {
            final MedicationRequest medReq = (MedicationRequest) bundleEntryComponent.getResource();
            final Reference medicationReference = medReq.getMedicationReference();
            final String string = UUID.randomUUID().toString();
            medicationReference.getResource().setId(string);
            medicationReference.setReference(string);
        }

        // transform it back to openEHR
        final Composition rmComposition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationaltemplate);
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
        if (medicationTexts.isEmpty() || doseAmounts.isEmpty() || additionalInstructions.isEmpty()
                || orderStarts.isEmpty() || directDurations.isEmpty()) {
            Assert.fail();
        }

        Assert.assertTrue(medicationTexts.stream().allMatch(
                med -> ((DvText) med).getValue().equals("Lorem ipsum1") || ((DvText) med).getValue()
                        .equals("Lorem ipsum0")));
        Assert.assertTrue(doseAmounts.stream().allMatch(
                med -> ((DvQuantity) med).getMagnitude().equals(21.0) && ((DvQuantity) med).getUnits().equals("mm")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(
                med -> ((DvText) ((Element) med).getValue()).getValue()
                        .startsWith("Additional instruction on one first")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(
                med -> ((DvText) ((Element) med).getValue()).getValue()
                        .startsWith("Additional instruction on one second")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(
                med -> ((DvText) ((Element) med).getValue()).getValue()
                        .startsWith("Additional instruction on two first")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(
                med -> ((DvText) ((Element) med).getValue()).getValue()
                        .startsWith("Additional instruction on two second")));
        Assert.assertTrue(additionalInstructions.stream().anyMatch(
                med -> ((DvText) ((Element) med).getValue()).getValue()
                        .startsWith("Additional instruction on two third")));
        Assert.assertEquals(5, additionalInstructions.size());
        Assert.assertEquals(1, orderStarts.size());
        final Element date = (Element) orderStarts.get(0);
        Assert.assertEquals(4, ((LocalDateTime) ((DvDateTime) date.getValue()).getValue()).getHour());
        Assert.assertEquals(5, ((LocalDateTime) ((DvDateTime) date.getValue()).getValue()).getMinute());
        Assert.assertEquals(2022, ((LocalDateTime) ((DvDateTime) date.getValue()).getValue()).getYear());
        Assert.assertEquals("Indefinite", ((DvCodedText) directDurations.get(0)).getValue());
        Assert.assertEquals("local",
                            ((DvCodedText) directDurations.get(0)).getDefiningCode().getTerminologyId().getName());
        Assert.assertEquals("at0067", ((DvCodedText) directDurations.get(0)).getDefiningCode().getCodeString());
    }

    @Test
    public void medicationOrderToOpenEhrToFhir() throws IOException {
        final Bundle testBundle = MedicationOrderToOpenEhrTest.testMedicationMedicationRequestBundle();
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context,
                                                                          testBundle, operationaltemplate);

        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
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
        Assert.assertEquals("medication text",
                            ((Medication) medReq.getMedicationReference().getResource()).getCode().getText());
        Assert.assertEquals("unit", ((Quantity) medReq.getDosageInstructionFirstRep().getDoseAndRateFirstRep()
                .getDose()).getUnit());
        Assert.assertEquals("111.0", ((Quantity) medReq.getDosageInstructionFirstRep().getDoseAndRateFirstRep()
                .getDose()).getValue().toPlainString());
        compareFlatJsons(context, operationaltemplate, testBundle, bundle);
    }

    public void compareFlatJsons(final FhirConnectContext context, final OPERATIONALTEMPLATE operationalTemplate,
                                 final Bundle testBundle, final Bundle afterBundle) {

        final JsonObject initialFlatJson = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationalTemplate);
        final JsonObject afterFlatJson = fhirToOpenEhr.fhirToFlatJsonObject(context, afterBundle, operationalTemplate);
        Assert.assertEquals(initialFlatJson.size(), afterFlatJson.size());
        for (Map.Entry<String, JsonElement> initialEntrySet : initialFlatJson.entrySet()) {
            final String initialKey = initialEntrySet.getKey();
            final String initialValue = initialEntrySet.getValue().getAsString();
            Assert.assertEquals(initialValue, afterFlatJson.getAsJsonPrimitive(initialKey).getAsString());
        }
    }

}
