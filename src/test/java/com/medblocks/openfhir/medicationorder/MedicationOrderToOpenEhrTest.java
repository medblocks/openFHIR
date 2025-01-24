package com.medblocks.openfhir.medicationorder;

import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import com.medblocks.openfhir.GenericTest;
import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Test;

public class MedicationOrderToOpenEhrTest extends GenericTest {

    final String MODEL_MAPPINGS = "/medication_order/";
    final String CONTEXT_MAPPING = "/medication_order/medication-order.context.yml";
    final String HELPER_LOCATION = "/medication_order/";
    final String OPT = "medication order.opt";


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
    public void medicationOrder_flat() {
        final List<FhirToOpenEhrHelper> helpers = new ArrayList<>();
        final String templateId = context.getContext().getTemplateId().toLowerCase().replace(" ", "_");
        final ArrayList<FhirToOpenEhrHelper> coverHelpers = new ArrayList<>();
        final OpenFhirFhirConnectModelMapper mapper = repo.getMapperForArchetype("medication order",
                                                                                 "openEHR-EHR-INSTRUCTION.medication_order.v2")
                .get(0);
        fhirToOpenEhr.createHelpers(mapper.getOpenEhrConfig().getArchetype(), mapper, templateId, templateId,
                                    mapper.getMappings(), null, helpers, coverHelpers, true, false, false);
        Assert.assertEquals("medication_order/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[at0070]",
                            findOpenEhrPathByFhirPath(new ArrayList<>(helpers),
                                                      "MedicationRequest.medication.resolve().code.text"));
        Assert.assertEquals(
                "medication_order/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[openEHR-EHR-CLUSTER.dosage.v1]/items[at0144]",
                findOpenEhrPathByFhirPath(new ArrayList<>(helpers),
                                          "MedicationRequest.dosageInstruction.doseAndRate.dose"));
    }

    @Test
    public void medicationOrder_RM() {
        final Bundle bundle = testMedicationMedicationRequestBundle();
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });
        final List<Base> medicationText = fhirPath.evaluate(bundle,
                                                            "Bundle.entry.resource.ofType(MedicationRequest).medication.resolve().code.text",
                                                            Base.class);
        Assert.assertEquals("medication text", medicationText.get(0).toString());

        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationaltemplate);
        final String medicationTextPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[at0070]/value";
        final String doseAmountPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[openEHR-EHR-CLUSTER.dosage.v1]/items[at0144]/value";
        Assert.assertEquals("medication text", ((DvText) composition.itemAtPath(medicationTextPath)).getValue());
        Assert.assertEquals(Double.valueOf(111.0),
                            ((DvQuantity) composition.itemAtPath(doseAmountPath)).getMagnitude());
    }

    private String findOpenEhrPathByFhirPath(final List<FhirToOpenEhrHelper> helpers, final String fhirPath) {
        for (FhirToOpenEhrHelper helper : helpers) {
            final String found = findOpenEhrPathByFhirPath(helper, fhirPath);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String findOpenEhrPathByFhirPath(final FhirToOpenEhrHelper helper, final String fhirPath) {
        if (fhirPath.equals(helper.getFhirPath())) {
            return helper.getOpenEhrPath();
        }
        final String toCheckFurther =
                StringUtils.isEmpty(helper.getFhirPath()) ? fhirPath : fhirPath.replace(helper.getFhirPath() + ".", "");
        if (helper.getFhirToOpenEhrHelpers() != null) {
            for (FhirToOpenEhrHelper innerHelper : helper.getFhirToOpenEhrHelpers()) {
                return findOpenEhrPathByFhirPath(innerHelper, toCheckFurther);
            }
        }
        return null;
    }

    public static Bundle testMedicationMedicationRequestBundle() {
        final Bundle bundle = new Bundle();
        final Bundle.BundleEntryComponent medicationEntry = new Bundle.BundleEntryComponent();
        final Medication medication = new Medication();
        final String medicationUuid = UUID.randomUUID().toString();
        medication.setId(medicationUuid);
        medication.setCode(new CodeableConcept().setText("medication text"));
        medicationEntry.setResource(medication);
        medicationEntry.setFullUrl("Medication/" + medicationUuid);
        bundle.addEntry(medicationEntry);

        final Bundle.BundleEntryComponent medicationRequestEntry = new Bundle.BundleEntryComponent();
        final MedicationRequest medicationRequest = new MedicationRequest();
        final Dosage dosage = new Dosage();
        final Dosage.DosageDoseAndRateComponent doseAndRate = new Dosage.DosageDoseAndRateComponent();
        doseAndRate.setDose(new Quantity(111).setUnit("unit"));
        dosage.addDoseAndRate(doseAndRate);
        medicationRequest.addDosageInstruction(dosage);
        final Reference value = new Reference("Medication/" + medicationUuid);
        value.setResource(medication);
        medicationRequest.setMedication(value);
        medicationRequestEntry.setResource(medicationRequest);
        bundle.addEntry(medicationRequestEntry);
        return bundle;
    }

}
