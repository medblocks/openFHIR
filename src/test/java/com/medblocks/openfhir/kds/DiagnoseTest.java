package com.medblocks.openfhir.kds;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class DiagnoseTest extends KdsBidirectionalTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT_MAPPING = "/kds_new/projects/org.highmed/KDS/diagnose/KDS_diagnose.context.yaml";
    final String HELPER_LOCATION = "/kds/diagnose/";
    final String OPT = "KDS_Diagnose.opt";
    final String FLAT = "KDS_Diagnose_Composition.flat.json";
    final String FLAT_MULTIPLE = "KDS_Diagnose_multiple_Composition.flat.json"; // todo change to multiple
    final String BUNDLE = "KDS_Diagnose_bundle_whole.json";
    final String BUNDLE_SINGLE = "KDS_Diagnose_bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    private void assertCondition(final Condition condition, final boolean second) {
        // - name: "contextStartTime"
        final String expectedTime = second ? "2023-02-03T04:05:06+01:00" : "2022-02-03T04:05:06+01:00";
//        Assert.assertEquals(expectedTime, condition.getRecordedDateElement().getValueAsString());


        // - name: "fallIdentifikationIdentifier"
        Assert.assertEquals("VN", condition.getEncounter().getIdentifier().getType().getCodingFirstRep().getCode());
        Assert.assertEquals("Encounter/123", condition.getEncounter().getIdentifier().getValue());

        // - name: "status"
        Assert.assertEquals("registriert", condition.getVerificationStatus().getText());

        //   - name: "date"
        final List<Extension> assertedExtensions = condition.getExtensionsByUrl(
                "http://hl7.org/fhir/StructureDefinition/condition-assertedDate");
        Assert.assertEquals(1, assertedExtensions.size());
        final String expectedAssertedTime = second ? "3022-02-03T04:05:06+01:00" : "2022-02-03T04:05:06+01:00";
        Assert.assertEquals(expectedAssertedTime,
                            ((DateTimeType) assertedExtensions.get(0).getValue()).getValueAsString());

        // dateTime, onset
        final String expectedOnsetStartTime = second ? "3022-02-03T04:05:06+01:00" : "2022-02-03T04:05:06+01:00";
        Assert.assertEquals(expectedOnsetStartTime, condition.getOnsetPeriod().getStartElement().getValueAsString());

        //- name: "clinicalStatus"
        Assert.assertEquals((second ? "referenced_" : "") + "Active", condition.getClinicalStatus().getText());
        Assert.assertEquals((second ? "referenced_" : "") + "at0026",
                            condition.getClinicalStatus().getCodingFirstRep().getCode());

        //  - name: "lebensphase"
        Assert.assertEquals((second ? "referenced_":"") +"43", ((CodeableConcept) condition.getOnsetPeriod().getStartElement()
                .getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase")
                .get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals((second ? "referenced_":"") +"44", ((CodeableConcept) condition.getOnsetPeriod().getEndElement()
                .getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase")
                .get(0).getValue()).getCodingFirstRep().getCode());

//         - name: "severity"
        Assert.assertEquals((second ? "referenced_":"") +"42", condition.getSeverity().getCodingFirstRep().getCode());
        Assert.assertEquals(
                (second ? "referenced_":"") +"No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/condition-severity' available",
                condition.getSeverity().getText());

        // bodySite
        final List<CodeableConcept> bodySites = condition.getBodySite();
        Assert.assertEquals(1, bodySites.size());
        final CodeableConcept bodySite = bodySites.get(0);
        Assert.assertEquals(1, bodySite.getCoding().size());
        final List<Coding> snomedBodySiteCodings = bodySite.getCoding().stream()
                .filter(bsite -> bsite.getSystem()
                        .equals((second ? "referenced_":"") +"//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/body-site"))
                .toList();
        Assert.assertEquals(1, snomedBodySiteCodings.size());
        Assert.assertEquals((second ? "referenced_":"") +"42", snomedBodySiteCodings.get(0).getCode());

        // bodySiteCluster; nothing to do here because the cluster is overwritten to be a unidiretional toopenehr only

//          - name: "problemDiagnose", - name: "problemDiagnoseNameCode"
        Assert.assertEquals(1, condition.getCode().getCoding().size());
        final Coding icd10code = condition.getCode().getCodingFirstRep();
        Assert.assertEquals((second ? "referenced_":"") +"kodierte_diagnose value", icd10code.getCode());
//      - name: "problemDiagnoseText"
        Assert.assertEquals((second ? "referenced_":"") +"freitextbeschreibung value", condition.getCode().getText());
//         - name: "icd10ProblemDiagnose"
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icd10code.getSystem());

//        - name: "codeIcd10Diagnosesicherheit"
        final CodeableConcept diagnosessicherheit = (CodeableConcept) icd10code.getExtensionByUrl(
                "http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit").getValue();
        Assert.assertEquals((second ? "referenced_":"") +"diagnosesicherheit", diagnosessicherheit.getCodingFirstRep().getCode());
        Assert.assertEquals(
                (second ? "referenced_":"") +"//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT",
                diagnosessicherheit.getCodingFirstRep().getSystem());

        // - name: "mehrfachcodierung"
        final CodeableConcept mehrfachcodierung = (CodeableConcept) icd10code.getExtensionByUrl(
                "http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen").getValue();
        Assert.assertEquals((second ? "referenced_":"") +"at0002", mehrfachcodierung.getCodingFirstRep().getCode());
        Assert.assertEquals((second ? "referenced_":"") +"local", mehrfachcodierung.getCodingFirstRep().getSystem());
        Assert.assertEquals((second ? "referenced_":"") +"†", mehrfachcodierung.getCodingFirstRep().getDisplay());

        // - name: "seitenlokalisation"
        final CodeableConcept seitenlokalisation = (CodeableConcept) icd10code.getExtensionByUrl(
                "http://fhir.de/StructureDefinition/seitenlokalisation").getValue();
        Assert.assertEquals((second ? "referenced_":"") +"at0003", seitenlokalisation.getCodingFirstRep().getCode());
        Assert.assertEquals((second ? "referenced_":"") +"local", seitenlokalisation.getCodingFirstRep().getSystem());
        Assert.assertEquals((second ? "referenced_":"") +"Left", seitenlokalisation.getCodingFirstRep().getDisplay());


        Assert.assertEquals(3, icd10code.getExtension().size());
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT_MULTIPLE), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allConditions = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Condition).collect(Collectors.toList());
        Assert.assertEquals(2, allConditions.size());
        final Condition condition = (Condition) allConditions.get(0).getResource(); // first condition
        final Condition conditionSecond = (Condition) allConditions.get(1).getResource(); // second condition

        assertCondition(condition, false);
        assertCondition(conditionSecond, true);

        // todo: assert referenced (not second, but the one actually referenced)
    }

    @Test
    public void toOpenEhr_single() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE_SINGLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("2022-02-03T01:00:00", jsonObject.get("diagnose/context/start_time").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0|code").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/diagnose:0|terminology").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose").getAsString());
        Assert.assertEquals("G", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("Confirmed diagnosis", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("M", jsonObject.get("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", jsonObject.get("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology").getAsString());
        Assert.assertEquals("Primary code in multiple coding", jsonObject.get("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value").getAsString());
        Assert.assertEquals("321667001", jsonObject.get("diagnose/diagnose:0/anatomical_location/body_site_name|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/diagnose:0/anatomical_location/body_site_name|terminology").getAsString());
        Assert.assertEquals("Respiratory tract, Upper lobe, bronchus or lung", jsonObject.get("diagnose/diagnose:0/anatomical_location/body_site_name|value").getAsString());
        Assert.assertEquals("L", jsonObject.get("diagnose/diagnose:0/anatomical_location/laterality|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", jsonObject.get("diagnose/diagnose:0/anatomical_location/laterality|terminology").getAsString());
        Assert.assertEquals("Left side", jsonObject.get("diagnose/diagnose:0/anatomical_location/laterality|value").getAsString());
        Assert.assertEquals("Secondary malignant neoplasm of lymph node", jsonObject.get("diagnose/diagnose:0/freitextbeschreibung").getAsString());
        Assert.assertEquals("Patient confirmed for secondary malignant neoplasm of lymph node.", jsonObject.get("diagnose/diagnose:0/diagnoseerläuterung").getAsString());
        Assert.assertEquals("2024-12-24T16:13:43", jsonObject.get("diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_des_auftretens").getAsString());
        Assert.assertEquals("424144002", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("Start of adulthood phase", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("367640001", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("End of middle age phase", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|value").getAsString());
        Assert.assertEquals("24484000", jsonObject.get("diagnose/diagnose:0/severity|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-severity", jsonObject.get("diagnose/diagnose:0/severity|terminology").getAsString());
        Assert.assertEquals("Severe", jsonObject.get("diagnose/diagnose:0/severity|value").getAsString());
        Assert.assertEquals("ENC123456", jsonObject.get("diagnose/context/case_identification/case_identifier").getAsString());
        Assert.assertEquals("Confirmed", jsonObject.get("diagnose/context/status").getAsString());
        Assert.assertEquals("2025-02-03T05:05:06", jsonObject.get("diagnose/diagnose:0/feststellungsdatum").getAsString());
        Assert.assertEquals("active", jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical", jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|terminology").getAsString());


    }

    @Ignore
    @Test
    public void toOpenEhr_withReference() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0|code").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/diagnose:0|terminology").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose").getAsString());
        Assert.assertEquals("G", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("Confirmed diagnosis", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("M", jsonObject.get("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm-mc", jsonObject.get("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology").getAsString());
        Assert.assertEquals("Primary code in multiple coding", jsonObject.get("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value").getAsString());
        Assert.assertEquals("321667001", jsonObject.get("diagnose/diagnose:0/anatomical_location/body_site_name|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/diagnose:0/anatomical_location/body_site_name|terminology").getAsString());
        Assert.assertEquals("Respiratory tract, Upper lobe, bronchus or lung", jsonObject.get("diagnose/diagnose:0/anatomical_location/body_site_name|value").getAsString());
        Assert.assertEquals("L", jsonObject.get("diagnose/diagnose:0/anatomical_location/laterality|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation", jsonObject.get("diagnose/diagnose:0/anatomical_location/laterality|terminology").getAsString());
        Assert.assertEquals("Left side", jsonObject.get("diagnose/diagnose:0/anatomical_location/laterality|value").getAsString());
        Assert.assertEquals("Secondary malignant neoplasm of lymph node", jsonObject.get("diagnose/diagnose:0/freitextbeschreibung").getAsString());
        Assert.assertEquals("Patient confirmed for secondary malignant neoplasm of lymph node.", jsonObject.get("diagnose/diagnose:0/diagnoseerläuterung").getAsString());
        Assert.assertEquals("2024-12-24T16:13:43", jsonObject.get("diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_des_auftretens").getAsString());
        Assert.assertEquals("424144002", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("Start of adulthood phase", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("367640001", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("End of middle age phase", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|value").getAsString());
        Assert.assertEquals("24484000", jsonObject.get("diagnose/diagnose:0/severity|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-severity", jsonObject.get("diagnose/diagnose:0/severity|terminology").getAsString());
        Assert.assertEquals("Severe", jsonObject.get("diagnose/diagnose:0/severity|value").getAsString());
        Assert.assertEquals("ENC123456", jsonObject.get("diagnose/context/case_identification/case_identifier").getAsString());
        Assert.assertEquals("Confirmed", jsonObject.get("diagnose/context/status").getAsString());
        Assert.assertEquals("2025-02-03T05:05:06", jsonObject.get("diagnose/diagnose:0/feststellungsdatum").getAsString());
        Assert.assertEquals("active", jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical", jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|terminology").getAsString());


    }

    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);
        final JsonObject jsonObject = new JsonObject();
        // todo: once we figure stuff out how to handle referenced conditions etc
        return jsonObject;
    }
}
