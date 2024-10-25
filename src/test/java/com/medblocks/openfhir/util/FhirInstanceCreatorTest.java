package com.medblocks.openfhir.util;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

public class FhirInstanceCreatorTest {

    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    private FhirInstanceCreator fhirInstanceCreator = new FhirInstanceCreator(openFhirStringUtils, new FhirInstanceCreatorUtility(openFhirStringUtils));

    private FhirPathR4 fhirPathR4 = new FhirPathR4(FhirContext.forR4());

    @Test
    public void testInstantiation() {
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category.coding.code", null)).getReturning() instanceof CodeType);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category.coding.display", null)).getReturning() instanceof StringType);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category", null)).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category.coding", null)).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.status", null)).getReturning() instanceof Enumeration);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.statusReason", null)).getReturning() instanceof CodeableConcept);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.doNotPerform", null)).getReturning() instanceof BooleanType);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.subject", null)).getReturning() instanceof Reference);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.groupIdentifier", null)).getReturning() instanceof Identifier);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.dosageInstruction", null)).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.note", null)).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.dispenseRequest", null)).getReturning() instanceof MedicationRequest.MedicationRequestDispenseRequestComponent);
    }

    @Test
    public void testInstantiationAndSetting_note() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.note";
        final Object returning = fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null).getReturning();
        final Annotation annotation = (Annotation) ((List) returning).get(0);
        annotation.setText("annotation text");
        final Optional<Annotation> evaluate = fhirPathR4.evaluateFirst(resource, fhirPath, Annotation.class);
        Assert.assertEquals("annotation text", evaluate.get().getText());

        // since this is actually a list, see if the first one is deleted when adding another one
        final Object returning1 = fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null).getReturning();
        final Annotation secondAnnotation = (Annotation) ((List) returning1).get(1);
        secondAnnotation.setText("2annotation text2");
        final List<Annotation> evaluatedAll = fhirPathR4.evaluate(resource, fhirPath, Annotation.class);
        Assert.assertEquals(2, evaluatedAll.size());
        Assert.assertEquals("annotation text", evaluatedAll.get(0).getText());
        Assert.assertEquals("2annotation text2", evaluatedAll.get(1).getText());
    }

    @Test
    public void testInstantiationAndSetting_primitive() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.doNotPerform";
        final BooleanType doNotPerform = (BooleanType) fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null).getReturning();
        doNotPerform.setValue(true);
        final Optional<BooleanType> evaluate = fhirPathR4.evaluateFirst(resource, fhirPath, BooleanType.class);
        Assert.assertEquals(true, evaluate.get().getValue());
    }

    @Test
    public void testInstantiationAndSetting_chainedFhirPath() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.category.coding.code";
        final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn = fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null);
        final CodeType categoryDodingCode = (CodeType) getLastReturn(instantiateAndSetReturn).getReturning();
        categoryDodingCode.setValue("category coding code value");
        final Optional<CodeType> evaluate = fhirPathR4.evaluateFirst(resource, fhirPath, CodeType.class);
        Assert.assertEquals("category coding code value", evaluate.get().getCode());
    }

    @Test
    public void testInstantiationAndSetting_chainedFhirPath_resolve() {

        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.medication.resolve().code.text";
        final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn = fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null, "Medication");
        StringType medicationText = (StringType) getLastReturn(instantiateAndSetReturn).getReturning();
        medicationText.setValue("This is medication text");

        final Medication medication = (Medication) resource.getMedicationReference().getResource();
        Assert.assertEquals("This is medication text", medication.getCode().getText());
    }

    private FhirInstanceCreator.InstantiateAndSetReturn getLastReturn(final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn) {
        if (instantiateAndSetReturn.getInner() == null) {
            return instantiateAndSetReturn;
        }
        return getLastReturn(instantiateAndSetReturn.getInner());
    }
}