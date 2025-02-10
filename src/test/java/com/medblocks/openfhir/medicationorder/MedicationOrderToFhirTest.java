package com.medblocks.openfhir.medicationorder;

import com.medblocks.openfhir.GenericTest;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.Assert;
import org.junit.Test;

public class MedicationOrderToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/medication_order/";
    final String CONTEXT_MAPPING = "/medication_order/medication-order.context.yml";
    final String HELPER_LOCATION = "/medication_order/";
    final String OPT = "medication order.opt";
    final String FLAT = "medication_order_flat.json";

    private OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();


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
    public void medicationOrderToFhir() {

        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             new OPTParser(
                                                                                     operationaltemplate).parse());
        final Bundle createdResources = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        Assert.assertEquals(2, createdResources.getEntry().size());
        final MedicationRequest medicationRequestOne = (MedicationRequest) createdResources.getEntry().stream()
                .map(res -> (MedicationRequest) res.getResource())
                .filter(res -> res.getNote().stream()
                        .anyMatch(note -> note.getText().startsWith("Additional instruction on one")))
                .findFirst()
                .orElse(null);
        Assert.assertEquals("Lorem ipsum0",
                            ((Medication) medicationRequestOne.getMedicationReference().getResource()).getCode()
                                    .getText());
        Assert.assertEquals("21.0",
                            ((Quantity) medicationRequestOne.getDosageInstructionFirstRep().getDoseAndRateFirstRep()
                                    .getDose()).getValue().toPlainString());
        Assert.assertEquals("mm",
                            ((Quantity) medicationRequestOne.getDosageInstructionFirstRep().getDoseAndRateFirstRep()
                                    .getDose()).getUnit());
        Assert.assertEquals(2, medicationRequestOne.getNote().size());
        Assert.assertTrue(medicationRequestOne.getNote().stream()
                                  .anyMatch(note -> note.getText().equals("Additional instruction on one first")));
        Assert.assertTrue(medicationRequestOne.getNote().stream()
                                  .anyMatch(note -> note.getText().equals("Additional instruction on one second")));
        Assert.assertNull(medicationRequestOne.getAuthoredOn());

        Assert.assertEquals("at0067",
                            medicationRequestOne.getDosageInstruction().get(0).getAdditionalInstructionFirstRep()
                                    .getCodingFirstRep().getCode());
        Assert.assertEquals("local",
                            medicationRequestOne.getDosageInstruction().get(0).getAdditionalInstructionFirstRep()
                                    .getCodingFirstRep().getSystem());

        final MedicationRequest medicationRequestTwo = (MedicationRequest) createdResources.getEntry().stream()
                .map(res -> (MedicationRequest) res.getResource())
                .filter(res -> res.getNote().stream()
                        .anyMatch(note -> note.getText().startsWith("Additional instruction on two")))
                .findFirst()
                .orElse(null);
        Assert.assertEquals("Lorem ipsum1",
                            ((Medication) medicationRequestTwo.getMedicationReference().getResource()).getCode()
                                    .getText());
        Assert.assertTrue(medicationRequestTwo.getDosageInstruction().isEmpty());
        Assert.assertEquals(3, medicationRequestTwo.getNote().size());
        Assert.assertTrue(medicationRequestTwo.getNote().stream()
                                  .anyMatch(note -> note.getText().equals("Additional instruction on two first")));
        Assert.assertTrue(medicationRequestTwo.getNote().stream()
                                  .anyMatch(note -> note.getText().equals("Additional instruction on two second")));
        Assert.assertTrue(medicationRequestTwo.getNote().stream()
                                  .anyMatch(note -> note.getText().equals("Additional instruction on two third")));

        Assert.assertEquals("2022-02-03T04:05:06",
                            openFhirMapperUtils.dateTimeToString(medicationRequestTwo.getAuthoredOn()));
    }

}
