package com.medblocks.openfhir.kds.diagnose;

import com.google.gson.JsonObject;
import com.medblocks.openfhir.kds.KdsBidirectionalTest;
import com.medblocks.openfhir.kds.KdsTest;
import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Composition;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DiagnoseToFHIRTest extends KdsTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT_MAPPING = "/kds_new/projects/org.highmed/KDS/diagnose/KDS_diagnose.context.yaml";
    final String OPT = "/kds/diagnose/KDS_Diagnose.opt";
    //INPUT
    final String FLAT = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition.flat.json";
    final String COMPOSITION_SINGLE = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition_bundle.json";
    final String COMPOSITION_MULTIPLE = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition_bundle_whole.json";
    final String FLAT_MULTIPLE = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_multiple_Composition.flat.json"; // todo change to multiple
    final String OPENEHR_COMPOSITION_1  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-diagnose-1.json";
    final String OPENEHR_COMPOSITION_2  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-diagnose-1.json";
    final String OPENEHR_COMPOSITION_3  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-diagnose-1.json";
    final String OPENEHR_COMPOSITION_4  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-diagnose-1.json";
    final String OPENEHR_COMPOSITION_5  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-diagnose-1.json";
    final String OPENEHR_COMPOSITION_6  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-diagnose-1.json";
    final String OPENEHR_COMPOSITION_7  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-diagnose-1.json";
    final String OPENEHR_COMPOSITION_8  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-diagnose-1.json";
    final String OPENEHR_COMPOSITION_9  = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-diagnose-1.json";
    final String OPENEHR_COMPOSITION_10 = "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-diagnose-1.json";
   //OUTPUT
   final String BUNDLE = "/kds/diagnose/toFHIR/output/KDS_Diagnose_bundle_whole.json";
    final String BUNDLE_SINGLE = "/kds/diagnose/toFHIR/output/KDS_Diagnose_bundle.json";
    final String FHIR_CONDITION_1  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-1-diagnose-1.json";
    final String FHIR_CONDITION_2  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-2-diagnose-1.json";
    final String FHIR_CONDITION_3  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-3-diagnose-1.json";
    final String FHIR_CONDITION_4  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-4-diagnose-1.json";
    final String FHIR_CONDITION_5  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-5-diagnose-1.json";
    final String FHIR_CONDITION_6  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-6-diagnose-1.json";
    final String FHIR_CONDITION_7  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-7-diagnose-1.json";
    final String FHIR_CONDITION_8  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-8-diagnose-1.json";
    final String FHIR_CONDITION_9  = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-9-diagnose-1.json";
    final String FHIR_CONDITION_10 = "/kds/diagnose/toFHIR/output/Condition-mii-exa-test-data-patient-10-diagnose-1.json";


    @SneakyThrows
    @Override
    public void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @SneakyThrows
    @Test
    public void assertToFHIR1(){
        Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITION_1), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_1);


    }

    @SneakyThrows
    @Test
    public void assertToFHIR2(){
        Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITION_2), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_2);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR3(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_3), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_3);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR4(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_4), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_4);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR5(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_5), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_5);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR6(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_6), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_6);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR7(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_7), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_7);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR8(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_8), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_8);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR9(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_9), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_9);
    }

    @SneakyThrows
    @Test
    public void assertToFHIR10(){
        Composition composition = JacksonUtil.getObjectMapper()
                .readValue(getFile(OPENEHR_COMPOSITION_10), Composition.class);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONDITION_10);
    }

    private void assertCondition(final Condition condition, final boolean second) {
        // - name: "contextStartTime"
        final String expectedTime = second ? "2023-02-03T04:05:06+01:00" : "2022-02-03T04:05:06+01:00";
//        Assert.assertEquals(expectedTime, condition.getRecordedDateElement().getValueAsString());


        // - name: "fallIdentifikationIdentifier"
        if (!second) {
            Assert.assertEquals("VN", ((Encounter) condition.getEncounter().getResource()).getIdentifier().get(0).getType().getCodingFirstRep().getCode());
            Assert.assertEquals("Encounter/123", ((Encounter) condition.getEncounter().getResource()).getIdentifier().get(0).getValue());
        }

        // - name: "status"
        if (!second) {
            Assert.assertEquals("unconfirmed", condition.getVerificationStatus().getCodingFirstRep().getCode());
            Assert.assertEquals("http://hl7.org/fhir/ValueSet/condition-ver-status",
                                condition.getVerificationStatus().getCodingFirstRep().getSystem());
        }

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
        Assert.assertEquals((second ? "referenced_" : "") + "43",
                            ((CodeableConcept) condition.getOnsetPeriod().getStartElement()
                                    .getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase")
                                    .get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals((second ? "referenced_" : "") + "44",
                            ((CodeableConcept) condition.getOnsetPeriod().getEndElement()
                                    .getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase")
                                    .get(0).getValue()).getCodingFirstRep().getCode());

//         - name: "schweregrad"
        Assert.assertEquals((second ? "referenced_" : "") + "42",
                            condition.getSeverity().getCodingFirstRep().getCode());
        Assert.assertEquals(
                (second ? "referenced_" : "")
                        + "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/condition-severity' available",
                condition.getSeverity().getText());

        // bodySite
        final List<CodeableConcept> bodySites = condition.getBodySite();
        Assert.assertEquals(1, bodySites.size());
        final CodeableConcept bodySite = bodySites.get(0);
        Assert.assertEquals(1, bodySite.getCoding().size());
        final List<Coding> snomedBodySiteCodings = bodySite.getCoding().stream()
                .filter(bsite -> bsite.getSystem()
                        .equals((second ? "referenced_" : "")
                                        + "//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/body-site"))
                .toList();
        Assert.assertEquals(1, snomedBodySiteCodings.size());
        Assert.assertEquals((second ? "referenced_" : "") + "42", snomedBodySiteCodings.get(0).getCode());

        // bodySiteCluster; nothing to do here because the cluster is overwritten to be a unidiretional toopenehr only

//          - name: "problemDiagnose", - name: "problemDiagnoseNameCode"
        Assert.assertEquals(2, condition.getCode().getCoding().size());
        Coding icd10code = condition.getCode().getCoding().get(0);
        Assert.assertEquals((second ? "referenced_" : "") + "kodierte_diagnose value", icd10code.getCode());
//      - name: "problemDiagnoseText"
//        Assert.assertEquals((second ? "referenced_" : "") + "freitextbeschreibung value",
//                            condition.getCode().getText());
//         - name: "icd10ProblemDiagnose"

        icd10code = condition.getCode().getCoding().get(1);
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icd10code.getSystem());

//        - name: "codeIcd10Diagnosesicherheit"
        final Coding diagnosessicherheit = (Coding) icd10code.getExtensionsByUrl(
                "http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit").stream()
                .map(Extension::getValue).filter(value -> !value.isEmpty()).findAny().orElse(null);
        Assert.assertEquals((second ? "referenced_" : "") + "diagnosesicherheit",
                            diagnosessicherheit.getCode());
        Assert.assertEquals(
                (second ? "referenced_" : "")
                        + "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT",
                diagnosessicherheit.getSystem());

        // - name: "mehrfachcodierung"
        final Coding mehrfachcodierung = (Coding) icd10code.getExtensionByUrl(
                "http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen").getValue();
        Assert.assertEquals("!", mehrfachcodierung.getCode());
        Assert.assertEquals("http://fhir.de/ValueSet/icd-10-gm-mehrfachcodierungs-kennzeichen", mehrfachcodierung.getSystem());
        Assert.assertEquals("!", mehrfachcodierung.getDisplay());

        // - name: "seitenlokalisation"
        final Coding seitenlokalisation = (Coding) icd10code.getExtensionByUrl(
                "http://fhir.de/StructureDefinition/seitenlokalisation").getValue();
        Assert.assertEquals((second ? "referenced_" : "") + "at0003", seitenlokalisation.getCode());
        Assert.assertEquals((second ? "referenced_" : "") + "local",
                            seitenlokalisation.getSystem());
        Assert.assertEquals((second ? "referenced_" : "") + "Left",
                            seitenlokalisation.getDisplay());


        Assert.assertEquals(3, icd10code.getExtension().size());
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFile(FLAT_MULTIPLE), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allConditions = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Condition).collect(Collectors.toList());
        Assert.assertEquals(2, allConditions.size());
        final Condition condition = (Condition) allConditions.get(0).getResource(); // first condition
        final Condition conditionSecond = (Condition) allConditions.get(1).getResource(); // second condition

        assertCondition(condition, false);
//        assertCondition(conditionSecond, true);

        final Type referencedExtensionCondition = condition.getExtensionByUrl(
                        "http://hl7.org/fhir/StructureDefinition/condition-related")
                .getValue();
        Assert.assertNotNull(referencedExtensionCondition);
        Assert.assertTrue(conditionSecond.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/condition-related")
                                  .getValue().isEmpty());

        assertCondition((Condition) ((Reference) referencedExtensionCondition).getResource(), true);

    }

}
