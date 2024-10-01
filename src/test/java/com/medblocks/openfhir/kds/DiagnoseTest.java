package com.medblocks.openfhir.kds;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class DiagnoseTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/diagnose/";
    final String OPT = "KDS_Diagnose.opt";
    final String FLAT = "KDS_Diagnose.flat.json";
    final String FLAT_MULTIPLE = "KDS_Diagnose.multiples.flat.json";
    final String CONTEXT = "diagnose.context.yaml";
    final String BUNDLE = "KDS_Diagnose_bundle_whole.json";
    final String BUNDLE_SINGLE = "KDS_Diagnose_bundle.json";

    @Override
    protected void prepareState() {
        context = getContext(RESOURCES_ROOT + CONTEXT);
        repo.initRepository(context, getClass().getResource(RESOURCES_ROOT).getFile());
        operationaltemplate = getOperationalTemplate(RESOURCES_ROOT + OPT);
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allConditions = bundle.getEntry().stream().filter(en -> en.getResource() instanceof Condition).collect(Collectors.toList());
        Assert.assertEquals(1, allConditions.size());
        final Condition condition = (Condition) allConditions.get(0).getResource();

        //   - name: "date"
        final List<Extension> assertedExtensions = condition.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/condition-assertedDate");
        Assert.assertEquals(1, assertedExtensions.size());
        Assert.assertEquals("2002-02-03T04:05:06+01:00", ((DateTimeType) assertedExtensions.get(0).getValue()).getValueAsString());

        //  - name: "clinicalStatus"
        final CodeableConcept clinicalStatuses = condition.getClinicalStatus();
        Assert.assertEquals(1, clinicalStatuses.getCoding().size());
        Assert.assertEquals("active", clinicalStatuses.getCodingFirstRep().getCode());
        Assert.assertEquals("Active", clinicalStatuses.getText());

        final List<Coding> icd10Codes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/icd-10-gm"))
                .toList();
        Assert.assertEquals(1, icd10Codes.size());
        Assert.assertEquals("C34.1", icd10Codes.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icd10Codes.get(0).getSystem());
        final List<Extension> icd10ExtensionsMultipleCodingId = icd10Codes.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen");
        Assert.assertEquals(1, icd10ExtensionsMultipleCodingId.size());
        final CodeableConcept multipleIcd10Value = (CodeableConcept) icd10ExtensionsMultipleCodingId.get(0).getValue();
        Assert.assertEquals("Primary code in multiple coding: †", multipleIcd10Value.getText());
        Assert.assertEquals("P: at0002 This is internal coded in the template", multipleIcd10Value.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", multipleIcd10Value.getCodingFirstRep().getSystem());

        final List<Extension> icd10ExtensionsAnatomicalLocation = icd10Codes.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/seitenlokalisation");
        Assert.assertEquals(1, icd10ExtensionsAnatomicalLocation.size());
        final CodeableConcept anatomicalLocationValue = (CodeableConcept) icd10ExtensionsAnatomicalLocation.get(0).getValue();
        Assert.assertEquals("Upper lobe", anatomicalLocationValue.getText());
        Assert.assertEquals("U", anatomicalLocationValue.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", anatomicalLocationValue.getCodingFirstRep().getSystem());


        final List<Extension> icd10ExtensionsDiagnosesicherheit = icd10Codes.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit");
        Assert.assertEquals(1, icd10ExtensionsDiagnosesicherheit.size());
        final CodeableConcept diagnosesicherheitValue = (CodeableConcept) icd10ExtensionsDiagnosesicherheit.get(0).getValue();
        Assert.assertEquals("Suspected diagnosis", diagnosesicherheitValue.getText());
        Assert.assertEquals("S", diagnosesicherheitValue.getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT", diagnosesicherheitValue.getCodingFirstRep().getSystem());

        final List<Coding> icd3Codes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3Codes.size());
        Assert.assertEquals("ICD3C34.1", icd3Codes.get(0).getCode());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", icd3Codes.get(0).getSystem());


        final List<Coding> alphaCodes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/alpha-id"))
                .toList();
        Assert.assertEquals(1, alphaCodes.size());
        Assert.assertEquals("primärcode057E3", alphaCodes.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", alphaCodes.get(0).getSystem());


        final List<Coding> orphaCodes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://www.orpha.net"))
                .toList();
        Assert.assertEquals(1, orphaCodes.size());
        Assert.assertEquals("830", orphaCodes.get(0).getCode());
        Assert.assertEquals("http://www.orpha.net", orphaCodes.get(0).getSystem());


        final List<Coding> sctCodes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, sctCodes.size());
        Assert.assertEquals("254626006", sctCodes.get(0).getCode());
        Assert.assertEquals("http://snomed.info/sct", sctCodes.get(0).getSystem());

        final List<CodeableConcept> bodySites = condition.getBodySite();
        Assert.assertEquals(1, bodySites.size());
        final CodeableConcept bodySite = bodySites.get(0);
        Assert.assertEquals(2, bodySite.getCoding().size());
        final List<Coding> snomedBodySiteCodings = bodySite.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, snomedBodySiteCodings.size());
        Assert.assertEquals("368209003", snomedBodySiteCodings.get(0).getCode());

        final List<Coding> icd3BodySiteCodings = bodySite.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3BodySiteCodings.size());
        Assert.assertEquals("Primary Korperstelle C34.1", icd3BodySiteCodings.get(0).getCode());

        final List<Extension> startLebensphaseExtension = condition.getOnsetPeriod().getStartElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, startLebensphaseExtension.size());
        Assert.assertEquals("424144002", ((CodeableConcept) startLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) startLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("Adulthood", ((CodeableConcept) startLebensphaseExtension.get(0).getValue()).getText());

        final List<Extension> endLebensphaseExtension = condition.getOnsetPeriod().getEndElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, endLebensphaseExtension.size());
        Assert.assertEquals("367640001", ((CodeableConcept) endLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) endLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("Middle age", ((CodeableConcept) endLebensphaseExtension.get(0).getValue()).getText());

        Assert.assertEquals("2022-02-03T00:00:00+01:00", condition.getRecordedDateElement().getValueAsString());
        Assert.assertEquals("The patient has a history of high blood pressure, now presenting with severe hypertension.", condition.getNoteFirstRep().getText());

        Assert.assertEquals(2, condition.getExtension().size());
//
        final List<Extension> referencedConditionExtension = condition.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/condition-related");
        Assert.assertEquals(1, referencedConditionExtension.size());
        final Condition referencedCondition = (Condition) ((Reference) referencedConditionExtension.get(0).getValue()).getResource();
        Assert.assertNotNull(referencedCondition);

        // --------------- REFERENCED

        assertReferenced(referencedCondition);
    }

    private void assertReferenced(final Condition referencedCondition) {
        final List<Extension> assertedExtensionsReferenced = referencedCondition.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/condition-assertedDate");
        Assert.assertEquals(1, assertedExtensionsReferenced.size());
        Assert.assertEquals("2012-02-03T04:05:06+01:00", ((DateTimeType) assertedExtensionsReferenced.get(0).getValue()).getValueAsString());

        //  - name: "clinicalStatus"
        final CodeableConcept clinicalStatusesReferenced = referencedCondition.getClinicalStatus();
        Assert.assertEquals(1, clinicalStatusesReferenced.getCoding().size());
        Assert.assertEquals("sactive", clinicalStatusesReferenced.getCodingFirstRep().getCode());
        Assert.assertEquals("SActive", clinicalStatusesReferenced.getText());

        final List<Coding> icd10CodesReferenced = referencedCondition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/icd-10-gm"))
                .toList();
        Assert.assertEquals(1, icd10CodesReferenced.size());
        Assert.assertEquals("C77.0", icd10CodesReferenced.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icd10CodesReferenced.get(0).getSystem());
        final List<Extension> icd10ExtensionsMultipleCodingIdReferenced = icd10CodesReferenced.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen");
        Assert.assertEquals(1, icd10ExtensionsMultipleCodingIdReferenced.size());
        final CodeableConcept multipleIcd10ValueReferenced = (CodeableConcept) icd10ExtensionsMultipleCodingIdReferenced.get(0).getValue();
        Assert.assertEquals("Primary code in multiple coding: †", multipleIcd10ValueReferenced.getText());
        Assert.assertEquals("M: at0002 this will fail as it is internal coded int he template", multipleIcd10ValueReferenced.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", multipleIcd10ValueReferenced.getCodingFirstRep().getSystem());

        final List<Extension> icd10ExtensionsAnatomicalLocationReferenced = icd10CodesReferenced.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/seitenlokalisation");
        Assert.assertEquals(1, icd10ExtensionsAnatomicalLocationReferenced.size());
        final CodeableConcept anatomicalLocationValueReferenced = (CodeableConcept) icd10ExtensionsAnatomicalLocationReferenced.get(0).getValue();
        Assert.assertEquals("Left side", anatomicalLocationValueReferenced.getText());
        Assert.assertEquals("L", anatomicalLocationValueReferenced.getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_SEITENLOKALISATION", anatomicalLocationValueReferenced.getCodingFirstRep().getSystem());


        final List<Extension> icd10ExtensionsDiagnosesicherheitReferenced = icd10CodesReferenced.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit");
        Assert.assertEquals(1, icd10ExtensionsDiagnosesicherheitReferenced.size());
        final CodeableConcept diagnosesicherheitValueReferenced = (CodeableConcept) icd10ExtensionsDiagnosesicherheitReferenced.get(0).getValue();
        Assert.assertEquals("Confirmed diagnosis", diagnosesicherheitValueReferenced.getText());
        Assert.assertEquals("G", diagnosesicherheitValueReferenced.getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT", diagnosesicherheitValueReferenced.getCodingFirstRep().getSystem());

        final List<Coding> icd3CodesReferenced = referencedCondition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3CodesReferenced.size());
        Assert.assertEquals("C77.0", icd3CodesReferenced.get(0).getCode());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", icd3CodesReferenced.get(0).getSystem());


        final List<Coding> alphaCodesReferenced = referencedCondition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/alpha-id"))
                .toList();
        Assert.assertEquals(1, alphaCodesReferenced.size());
        Assert.assertEquals("057E3", alphaCodesReferenced.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", alphaCodesReferenced.get(0).getSystem());


        final List<Coding> orphaCodesReferenced = referencedCondition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://www.orpha.net"))
                .toList();
        Assert.assertEquals(1, orphaCodesReferenced.size());
        Assert.assertEquals("1777", orphaCodesReferenced.get(0).getCode());
        Assert.assertEquals("http://www.orpha.net", orphaCodesReferenced.get(0).getSystem());


        final List<Coding> sctCodesReferenced = referencedCondition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, sctCodesReferenced.size());
        Assert.assertEquals("128462008", sctCodesReferenced.get(0).getCode());
        Assert.assertEquals("http://snomed.info/sct", sctCodesReferenced.get(0).getSystem());

        final List<CodeableConcept> bodySitesReferenced = referencedCondition.getBodySite();
        Assert.assertEquals(1, bodySitesReferenced.size());
        final CodeableConcept bodySiteReferenced = bodySitesReferenced.get(0);
        Assert.assertEquals(2, bodySiteReferenced.getCoding().size());
        final List<Coding> snomedBodySiteCodingsReferenced = bodySiteReferenced.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, snomedBodySiteCodingsReferenced.size());
        Assert.assertEquals("321667001", snomedBodySiteCodingsReferenced.get(0).getCode());

        final List<Coding> icd3BodySiteCodingsReferenced = bodySiteReferenced.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3BodySiteCodingsReferenced.size());
        Assert.assertEquals("C34.1", icd3BodySiteCodingsReferenced.get(0).getCode());

        final List<Extension> startLebensphaseExtensionReferenced = referencedCondition.getOnsetPeriod().getStartElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, startLebensphaseExtensionReferenced.size());
        Assert.assertEquals("S424144002", ((CodeableConcept) startLebensphaseExtensionReferenced.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) startLebensphaseExtensionReferenced.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("Adulthood", ((CodeableConcept) startLebensphaseExtensionReferenced.get(0).getValue()).getText());

        final List<Extension> endLebensphaseExtensionReferenced = referencedCondition.getOnsetPeriod().getEndElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, endLebensphaseExtensionReferenced.size());
        Assert.assertEquals("S367640001", ((CodeableConcept) endLebensphaseExtensionReferenced.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) endLebensphaseExtensionReferenced.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("Middle age", ((CodeableConcept) endLebensphaseExtensionReferenced.get(0).getValue()).getText());

        Assert.assertEquals("SEKUNDAR Patient confirmed for secondary malignant neoplasm of lymph node.", referencedCondition.getNoteFirstRep().getText());

        Assert.assertEquals(1, referencedCondition.getExtension().size());
    }

    @Test
    public void toFhir_multiple() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT_MULTIPLE), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allConditions = bundle.getEntry().stream().filter(en -> en.getResource() instanceof Condition).collect(Collectors.toList());
        Assert.assertEquals(2, allConditions.size());
        final Condition condition = (Condition) allConditions.get(0).getResource();
        final Condition conditionSecond = (Condition) allConditions.get(1).getResource();

        //   - name: "date"
        final List<Extension> assertedExtensions = condition.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/condition-assertedDate");
        Assert.assertEquals(1, assertedExtensions.size());
        Assert.assertEquals("2002-02-03T04:05:06+01:00", ((DateTimeType) assertedExtensions.get(0).getValue()).getValueAsString());

        //  - name: "clinicalStatus"
        final CodeableConcept clinicalStatuses = condition.getClinicalStatus();
        Assert.assertEquals(1, clinicalStatuses.getCoding().size());
        Assert.assertEquals("active", clinicalStatuses.getCodingFirstRep().getCode());
        Assert.assertEquals("Active", clinicalStatuses.getText());

        final List<Coding> icd10Codes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/icd-10-gm"))
                .toList();
        Assert.assertEquals(1, icd10Codes.size());
        Assert.assertEquals("C34.1", icd10Codes.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icd10Codes.get(0).getSystem());
        final List<Extension> icd10ExtensionsMultipleCodingId = icd10Codes.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen");
        Assert.assertEquals(1, icd10ExtensionsMultipleCodingId.size());
        final CodeableConcept multipleIcd10Value = (CodeableConcept) icd10ExtensionsMultipleCodingId.get(0).getValue();
        Assert.assertEquals("Primary code in multiple coding: †", multipleIcd10Value.getText());
        Assert.assertEquals("P: at0002 This is internal coded in the template", multipleIcd10Value.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", multipleIcd10Value.getCodingFirstRep().getSystem());

        final List<Extension> icd10ExtensionsAnatomicalLocation = icd10Codes.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/seitenlokalisation");
        Assert.assertEquals(1, icd10ExtensionsAnatomicalLocation.size());
        final CodeableConcept anatomicalLocationValue = (CodeableConcept) icd10ExtensionsAnatomicalLocation.get(0).getValue();
        Assert.assertEquals("Upper lobe", anatomicalLocationValue.getText());
        Assert.assertEquals("U", anatomicalLocationValue.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", anatomicalLocationValue.getCodingFirstRep().getSystem());


        final List<Extension> icd10ExtensionsDiagnosesicherheit = icd10Codes.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit");
        Assert.assertEquals(1, icd10ExtensionsDiagnosesicherheit.size());
        final CodeableConcept diagnosesicherheitValue = (CodeableConcept) icd10ExtensionsDiagnosesicherheit.get(0).getValue();
        Assert.assertEquals("Suspected diagnosis", diagnosesicherheitValue.getText());
        Assert.assertEquals("S", diagnosesicherheitValue.getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT", diagnosesicherheitValue.getCodingFirstRep().getSystem());

        final List<Coding> icd3Codes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3Codes.size());
        Assert.assertEquals("ICD3C34.1", icd3Codes.get(0).getCode());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", icd3Codes.get(0).getSystem());


        final List<Coding> alphaCodes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/alpha-id"))
                .toList();
        Assert.assertEquals(1, alphaCodes.size());
        Assert.assertEquals("primärcode057E3", alphaCodes.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", alphaCodes.get(0).getSystem());


        final List<Coding> orphaCodes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://www.orpha.net"))
                .toList();
        Assert.assertEquals(1, orphaCodes.size());
        Assert.assertEquals("830", orphaCodes.get(0).getCode());
        Assert.assertEquals("http://www.orpha.net", orphaCodes.get(0).getSystem());


        final List<Coding> sctCodes = condition.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, sctCodes.size());
        Assert.assertEquals("254626006", sctCodes.get(0).getCode());
        Assert.assertEquals("http://snomed.info/sct", sctCodes.get(0).getSystem());

        final List<CodeableConcept> bodySites = condition.getBodySite();
        Assert.assertEquals(1, bodySites.size());
        final CodeableConcept bodySite = bodySites.get(0);
        Assert.assertEquals(2, bodySite.getCoding().size());
        final List<Coding> snomedBodySiteCodings = bodySite.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, snomedBodySiteCodings.size());
        Assert.assertEquals("368209003", snomedBodySiteCodings.get(0).getCode());

        final List<Coding> icd3BodySiteCodings = bodySite.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3BodySiteCodings.size());
        Assert.assertEquals("Primary Korperstelle C34.1", icd3BodySiteCodings.get(0).getCode());

        final List<Extension> startLebensphaseExtension = condition.getOnsetPeriod().getStartElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, startLebensphaseExtension.size());
        Assert.assertEquals("424144002", ((CodeableConcept) startLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) startLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("Adulthood", ((CodeableConcept) startLebensphaseExtension.get(0).getValue()).getText());

        final List<Extension> endLebensphaseExtension = condition.getOnsetPeriod().getEndElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, endLebensphaseExtension.size());
        Assert.assertEquals("367640001", ((CodeableConcept) endLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) endLebensphaseExtension.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("Middle age", ((CodeableConcept) endLebensphaseExtension.get(0).getValue()).getText());

        Assert.assertEquals("2022-02-03T00:00:00+01:00", condition.getRecordedDateElement().getValueAsString());
        Assert.assertEquals("The patient has a history of high blood pressure, now presenting with severe hypertension.", condition.getNoteFirstRep().getText());

        Assert.assertEquals(2, condition.getExtension().size());
//
        final List<Extension> referencedConditionExtension = condition.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/condition-related");
        Assert.assertEquals(1, referencedConditionExtension.size());
        final Condition referencedCondition = (Condition) ((Reference) referencedConditionExtension.get(0).getValue()).getResource();
        Assert.assertNotNull(referencedCondition);
//


        // --------------- REFERENCED

        assertReferenced(referencedCondition);

        // --------- SECOND CONDITION

        //   - name: "date"
        final List<Extension> assertedExtensionsSecond = conditionSecond.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/condition-assertedDate");
        Assert.assertEquals(1, assertedExtensionsSecond.size());
        Assert.assertEquals("2062-02-03T04:05:06+01:00", ((DateTimeType) assertedExtensionsSecond.get(0).getValue()).getValueAsString());

        //  - name: "clinicalStatus"
        final CodeableConcept clinicalStatusesSecond = conditionSecond.getClinicalStatus();
        Assert.assertEquals(1, clinicalStatusesSecond.getCoding().size());
        Assert.assertEquals("second_active", clinicalStatusesSecond.getCodingFirstRep().getCode());
        Assert.assertEquals("second_Active", clinicalStatusesSecond.getText());

        final List<Coding> icd10CodesSecond = conditionSecond.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/icd-10-gm"))
                .toList();
        Assert.assertEquals(1, icd10CodesSecond.size());
        Assert.assertEquals("second_C34.1", icd10CodesSecond.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icd10CodesSecond.get(0).getSystem());
        final List<Extension> icd10ExtensionsMultipleCodingIdSecond = icd10CodesSecond.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen");
        Assert.assertEquals(1, icd10ExtensionsMultipleCodingIdSecond.size());
        final CodeableConcept multipleIcd10ValueSecond = (CodeableConcept) icd10ExtensionsMultipleCodingIdSecond.get(0).getValue();
        Assert.assertEquals("second_Primary code in multiple coding: †", multipleIcd10ValueSecond.getText());
        Assert.assertEquals("second_P: at0002 This is internal coded in the template", multipleIcd10ValueSecond.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", multipleIcd10ValueSecond.getCodingFirstRep().getSystem());

        final List<Extension> icd10ExtensionsAnatomicalLocationSecond = icd10CodesSecond.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/seitenlokalisation");
        Assert.assertEquals(1, icd10ExtensionsAnatomicalLocationSecond.size());
        final CodeableConcept anatomicalLocationValueSecond = (CodeableConcept) icd10ExtensionsAnatomicalLocationSecond.get(0).getValue();
        Assert.assertEquals("second_Upper lobe", anatomicalLocationValueSecond.getText());
        Assert.assertEquals("second_U", anatomicalLocationValueSecond.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", anatomicalLocationValueSecond.getCodingFirstRep().getSystem());


        final List<Extension> icd10ExtensionsDiagnosesicherheitSecond = icd10CodesSecond.get(0).getExtensionsByUrl("http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit");
        Assert.assertEquals(1, icd10ExtensionsDiagnosesicherheitSecond.size());
        final CodeableConcept diagnosesicherheitValueSecond = (CodeableConcept) icd10ExtensionsDiagnosesicherheitSecond.get(0).getValue();
        Assert.assertEquals("second_Suspected diagnosis", diagnosesicherheitValueSecond.getText());
        Assert.assertEquals("second_S", diagnosesicherheitValueSecond.getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT", diagnosesicherheitValueSecond.getCodingFirstRep().getSystem());

        final List<Coding> icd3CodesSecond = conditionSecond.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3CodesSecond.size());
        Assert.assertEquals("second_ICD3C34.1", icd3CodesSecond.get(0).getCode());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", icd3CodesSecond.get(0).getSystem());


        final List<Coding> alphaCodesSecond = conditionSecond.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://fhir.de/CodeSystem/bfarm/alpha-id"))
                .toList();
        Assert.assertEquals(1, alphaCodesSecond.size());
        Assert.assertEquals("second_primärcode057E3", alphaCodesSecond.get(0).getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", alphaCodesSecond.get(0).getSystem());


        final List<Coding> orphaCodesSecond = conditionSecond.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://www.orpha.net"))
                .toList();
        Assert.assertEquals(1, orphaCodesSecond.size());
        Assert.assertEquals("second_830", orphaCodesSecond.get(0).getCode());
        Assert.assertEquals("http://www.orpha.net", orphaCodesSecond.get(0).getSystem());


        final List<Coding> sctCodesSecond = conditionSecond.getCode().getCoding().stream()
                .filter(code -> code.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, sctCodesSecond.size());
        Assert.assertEquals("second_254626006", sctCodesSecond.get(0).getCode());
        Assert.assertEquals("http://snomed.info/sct", sctCodesSecond.get(0).getSystem());

        final List<CodeableConcept> bodySitesSecond = conditionSecond.getBodySite();
        Assert.assertEquals(1, bodySitesSecond.size());
        final CodeableConcept bodySiteSecond = bodySitesSecond.get(0);
        Assert.assertEquals(2, bodySiteSecond.getCoding().size());
        final List<Coding> snomedBodySiteCodingsSecond = bodySiteSecond.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://snomed.info/sct"))
                .toList();
        Assert.assertEquals(1, snomedBodySiteCodingsSecond.size());
        Assert.assertEquals("second_368209003", snomedBodySiteCodingsSecond.get(0).getCode());

        final List<Coding> icd3BodySiteCodingsSecond = bodySiteSecond.getCoding().stream()
                .filter(bsite -> bsite.getSystem().equals("http://terminology.hl7.org/CodeSystem/icd-o-3"))
                .toList();
        Assert.assertEquals(1, icd3BodySiteCodingsSecond.size());
        Assert.assertEquals("second_Primary Korperstelle C34.1", icd3BodySiteCodingsSecond.get(0).getCode());

        final List<Extension> startLebensphaseExtensionSecond = conditionSecond.getOnsetPeriod().getStartElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, startLebensphaseExtensionSecond.size());
        Assert.assertEquals("second_424144002", ((CodeableConcept) startLebensphaseExtensionSecond.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) startLebensphaseExtensionSecond.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("second_Adulthood", ((CodeableConcept) startLebensphaseExtensionSecond.get(0).getValue()).getText());

        final List<Extension> endLebensphaseExtensionSecond = conditionSecond.getOnsetPeriod().getEndElement().getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase");
        Assert.assertEquals(1, endLebensphaseExtensionSecond.size());
        Assert.assertEquals("second_367640001", ((CodeableConcept) endLebensphaseExtensionSecond.get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de", ((CodeableConcept) endLebensphaseExtensionSecond.get(0).getValue()).getCodingFirstRep().getSystem());
        Assert.assertEquals("second_Middle age", ((CodeableConcept) endLebensphaseExtensionSecond.get(0).getValue()).getText());

        Assert.assertEquals("2022-02-03T00:00:00+01:00", conditionSecond.getRecordedDateElement().getValueAsString());
        Assert.assertEquals("second_The patient has a history of high blood pressure, now presenting with severe hypertension.", conditionSecond.getNoteFirstRep().getText());

        Assert.assertEquals(2, conditionSecond.getExtension().size()); // doe to the cover fhirConfig.condition

        final List<Extension> referencedConditionExtensionSecond = conditionSecond.getExtensionsByUrl("http://hl7.org/fhir/StructureDefinition/condition-related");
        Assert.assertEquals(1, referencedConditionExtensionSecond.size());
        Assert.assertNull(referencedConditionExtensionSecond.get(0).getValue());
    }

    @Test
    public void toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE_SINGLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("2025-02-03T05:05:06", jsonObject.get("diagnose/primärcode:0/feststellungsdatum").getAsString());
        Assert.assertEquals("active", jsonObject.get("diagnose/primärcode:0/klinischer_status/klinischer_status|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical", jsonObject.get("diagnose/primärcode:0/klinischer_status/klinischer_status|terminology").getAsString());
        Assert.assertEquals("Patient confirmed for secondary malignant neoplasm of lymph node.", jsonObject.get("diagnose/primärcode:0/diagnoseerläuterung").getAsString());
        Assert.assertEquals("424144002", jsonObject.get("diagnose/primärcode:0/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("Start of adulthood phase", jsonObject.get("diagnose/primärcode:0/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("367640001", jsonObject.get("diagnose/primärcode:0/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("End of middle age phase", jsonObject.get("diagnose/primärcode:0/lebensphase/ende|value").getAsString());
        Assert.assertEquals("321667001", jsonObject.get("diagnose/primärcode:0/körperstelle_-_snomed-ct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/körperstelle_-_snomed-ct|terminology").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/primärcode:0/körperstelle_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/primärcode:0/körperstelle_-_icd-o-3|terminology").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-10-gm|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-10-gm|terminology").getAsString());
        Assert.assertEquals("M", jsonObject.get("diagnose/primärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", jsonObject.get("diagnose/primärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology").getAsString());
        Assert.assertEquals("Primary code in multiple coding", jsonObject.get("diagnose/primärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value").getAsString());
        Assert.assertEquals("L", jsonObject.get("diagnose/primärcode:0/anatomical_location/laterality|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", jsonObject.get("diagnose/primärcode:0/anatomical_location/laterality|terminology").getAsString());
        Assert.assertEquals("Left side", jsonObject.get("diagnose/primärcode:0/anatomical_location/laterality|value").getAsString());
        Assert.assertEquals("G", jsonObject.get("diagnose/primärcode:0/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit", jsonObject.get("diagnose/primärcode:0/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("Confirmed diagnosis", jsonObject.get("diagnose/primärcode:0/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("057E3", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_alpha-id|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_alpha-id|terminology").getAsString());
        Assert.assertEquals("128462008", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_sct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_sct|terminology").getAsString());
        Assert.assertEquals("1777", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_orphanet|code").getAsString());
        Assert.assertEquals("http://www.orpha.net", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_orphanet|terminology").getAsString());
        Assert.assertEquals("C77.0", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-o-3|terminology").getAsString());
        Assert.assertEquals("2022-02-03T01:00:00", jsonObject.get("diagnose/context/start_time").getAsString());

    }

    @Test
    public void toOpenEhr_withReferenced() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("2025-02-03T05:05:06", jsonObject.get("diagnose/primärcode:0/feststellungsdatum").getAsString());
        Assert.assertEquals("active", jsonObject.get("diagnose/primärcode:0/klinischer_status/klinischer_status|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical", jsonObject.get("diagnose/primärcode:0/klinischer_status/klinischer_status|terminology").getAsString());
        Assert.assertEquals("Patient confirmed for secondary malignant neoplasm of lymph node.", jsonObject.get("diagnose/primärcode:0/diagnoseerläuterung").getAsString());
        Assert.assertEquals("424144002", jsonObject.get("diagnose/primärcode:0/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("Start of adulthood phase", jsonObject.get("diagnose/primärcode:0/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("367640001", jsonObject.get("diagnose/primärcode:0/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("End of middle age phase", jsonObject.get("diagnose/primärcode:0/lebensphase/ende|value").getAsString());
        Assert.assertEquals("321667001", jsonObject.get("diagnose/primärcode:0/körperstelle_-_snomed-ct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/körperstelle_-_snomed-ct|terminology").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/primärcode:0/körperstelle_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/primärcode:0/körperstelle_-_icd-o-3|terminology").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-10-gm|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-10-gm|terminology").getAsString());
        Assert.assertEquals("M", jsonObject.get("diagnose/primärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", jsonObject.get("diagnose/primärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology").getAsString());
        Assert.assertEquals("Primary code in multiple coding", jsonObject.get("diagnose/primärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value").getAsString());
        Assert.assertEquals("L", jsonObject.get("diagnose/primärcode:0/anatomical_location/laterality|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", jsonObject.get("diagnose/primärcode:0/anatomical_location/laterality|terminology").getAsString());
        Assert.assertEquals("Left side", jsonObject.get("diagnose/primärcode:0/anatomical_location/laterality|value").getAsString());
        Assert.assertEquals("G", jsonObject.get("diagnose/primärcode:0/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit", jsonObject.get("diagnose/primärcode:0/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("Confirmed diagnosis", jsonObject.get("diagnose/primärcode:0/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("057E3", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_alpha-id|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_alpha-id|terminology").getAsString());
        Assert.assertEquals("128462008", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_sct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_sct|terminology").getAsString());
        Assert.assertEquals("1777", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_orphanet|code").getAsString());
        Assert.assertEquals("http://www.orpha.net", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_orphanet|terminology").getAsString());
        Assert.assertEquals("C77.0", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/primärcode:0/kodierte_diagnose_-_icd-o-3|terminology").getAsString());
        Assert.assertEquals("2022-02-03T01:00:00", jsonObject.get("diagnose/context/start_time").getAsString());



        Assert.assertEquals("2125-02-03T05:05:06", jsonObject.get("diagnose/sekundärcode:0/feststellungsdatum").getAsString());
        Assert.assertEquals("ref_active", jsonObject.get("diagnose/sekundärcode:0/klinischer_status/klinischer_status|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical", jsonObject.get("diagnose/sekundärcode:0/klinischer_status/klinischer_status|terminology").getAsString());
        Assert.assertEquals("ref_The patient has a history of high blood pressure, now presenting with severe hypertension.", jsonObject.get("diagnose/sekundärcode:0/diagnoseerläuterung").getAsString());
        Assert.assertEquals("ref_424144002", jsonObject.get("diagnose/sekundärcode:0/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:0/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("ref_Start of adulthood phase", jsonObject.get("diagnose/sekundärcode:0/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("ref_367640001", jsonObject.get("diagnose/sekundärcode:0/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:0/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("ref_End of middle age phase", jsonObject.get("diagnose/sekundärcode:0/lebensphase/ende|value").getAsString());
        Assert.assertEquals("ref_368209003", jsonObject.get("diagnose/sekundärcode:0/körperstelle_-_snomed-ct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:0/körperstelle_-_snomed-ct|terminology").getAsString());
        Assert.assertEquals("ref_C34.1", jsonObject.get("diagnose/sekundärcode:0/körperstelle_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/sekundärcode:0/körperstelle_-_icd-o-3|terminology").getAsString());
        Assert.assertEquals("ref_C34.1", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_icd-10-gm|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_icd-10-gm|terminology").getAsString());
        Assert.assertEquals("ref_P", jsonObject.get("diagnose/sekundärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", jsonObject.get("diagnose/sekundärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology").getAsString());
        Assert.assertEquals("ref_Primary code in multiple coding", jsonObject.get("diagnose/sekundärcode:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value").getAsString());
        Assert.assertEquals("ref_U", jsonObject.get("diagnose/sekundärcode:0/anatomical_location/laterality|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", jsonObject.get("diagnose/sekundärcode:0/anatomical_location/laterality|terminology").getAsString());
        Assert.assertEquals("ref_Upper lobe", jsonObject.get("diagnose/sekundärcode:0/anatomical_location/laterality|value").getAsString());
        Assert.assertEquals("ref_S", jsonObject.get("diagnose/sekundärcode:0/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit", jsonObject.get("diagnose/sekundärcode:0/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("ref_Suspected diagnosis", jsonObject.get("diagnose/sekundärcode:0/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("ref_098H5", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_alpha-id|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_alpha-id|terminology").getAsString());
        Assert.assertEquals("ref_254626006", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_sct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_sct|terminology").getAsString());
        Assert.assertEquals("ref_830", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_orphanet|code").getAsString());
        Assert.assertEquals("http://www.orpha.net", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_orphanet|terminology").getAsString());
        Assert.assertEquals("ref_C34.1", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/sekundärcode:0/kodierte_diagnose_-_icd-o-3|terminology").getAsString());


        Assert.assertEquals("ref1_active", jsonObject.get("diagnose/sekundärcode:1/klinischer_status/klinischer_status|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical", jsonObject.get("diagnose/sekundärcode:1/klinischer_status/klinischer_status|terminology").getAsString());
        Assert.assertEquals("ref1_The patient has a history of high blood pressure, now presenting with severe hypertension.", jsonObject.get("diagnose/sekundärcode:1/diagnoseerläuterung").getAsString());
        Assert.assertEquals("ref1_424144002", jsonObject.get("diagnose/sekundärcode:1/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:1/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("ref1_Start of adulthood phase", jsonObject.get("diagnose/sekundärcode:1/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("ref1_367640001", jsonObject.get("diagnose/sekundärcode:1/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:1/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("ref1_End of middle age phase", jsonObject.get("diagnose/sekundärcode:1/lebensphase/ende|value").getAsString());
        Assert.assertEquals("ref1_368209003", jsonObject.get("diagnose/sekundärcode:1/körperstelle_-_snomed-ct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:1/körperstelle_-_snomed-ct|terminology").getAsString());
        Assert.assertEquals("ref1_C34.1", jsonObject.get("diagnose/sekundärcode:1/körperstelle_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/sekundärcode:1/körperstelle_-_icd-o-3|terminology").getAsString());
        Assert.assertEquals("ref1_C34.1", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_icd-10-gm|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_icd-10-gm|terminology").getAsString());
        Assert.assertEquals("ref1_P", jsonObject.get("diagnose/sekundärcode:1/multiple_coding_icd-10-gm/multiple_coding_identifier|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", jsonObject.get("diagnose/sekundärcode:1/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology").getAsString());
        Assert.assertEquals("ref1_Primary code in multiple coding", jsonObject.get("diagnose/sekundärcode:1/multiple_coding_icd-10-gm/multiple_coding_identifier|value").getAsString());
        Assert.assertEquals("ref1_U", jsonObject.get("diagnose/sekundärcode:1/anatomical_location/laterality|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", jsonObject.get("diagnose/sekundärcode:1/anatomical_location/laterality|terminology").getAsString());
        Assert.assertEquals("ref1_Upper lobe", jsonObject.get("diagnose/sekundärcode:1/anatomical_location/laterality|value").getAsString());
        Assert.assertEquals("ref1_S", jsonObject.get("diagnose/sekundärcode:1/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit", jsonObject.get("diagnose/sekundärcode:1/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("ref1_Suspected diagnosis", jsonObject.get("diagnose/sekundärcode:1/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("ref1_098H5", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_alpha-id|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/alpha-id", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_alpha-id|terminology").getAsString());
        Assert.assertEquals("ref1_254626006", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_sct|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_sct|terminology").getAsString());
        Assert.assertEquals("ref1_830", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_orphanet|code").getAsString());
        Assert.assertEquals("http://www.orpha.net", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_orphanet|terminology").getAsString());
        Assert.assertEquals("ref1_C34.1", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_icd-o-3|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/icd-o-3", jsonObject.get("diagnose/sekundärcode:1/kodierte_diagnose_-_icd-o-3|terminology").getAsString());

    }
}
