package com.medblocks.openfhir.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.tofhir.OpenEhrToFhir;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class OpenFhirStringUtilsTest {

    @Test
    @Ignore
    public void prepareParentPath() {
        // person/personendaten/person/pid:0|id
        // person/personendaten/person/pid:1|id
        // Patient.identifier.value
        // RESULT: should return index 0, because 0 and 1 need to be on the identifier

        // person/personendaten/person/geburtsname:0/vollständiger_name
        // person/personendaten/person/geburtsname:1/vollständiger_name
        // Patient.name.given
        // RESULT: should return index 0, because 0 and 1 need to be on the name
        Assert.assertEquals("dataset_poc_rso-zl/advance_care_directive/media_file",
                            new OpenFhirStringUtils().prepareParentOpenEhrPath(
                                    "dataset_poc_rso-zl/advance_care_directive/media_file/content.content",
                                    "dataset_poc_rso-zl/advance_care_directive/media_file/content_name"));

        Assert.assertEquals("person/personendaten/person/geburtsname:0",
                            new OpenFhirStringUtils().prepareParentOpenEhrPath(
                                    "person.personendaten.person.geburtsname",
                                    "person/personendaten/person/geburtsname:0/vollständiger_name"));

    }


    @Test
    public void getIndexOfElement() {
        final String path = "laborbericht/laborbefund/pro_laboranalyt:0/bezeichnung_des_analyts|terminology";
        final String element = "laborbericht/laborbefund/pro_laboranalyt";
        Assert.assertEquals(0, new OpenFhirStringUtils().getIndexOfElement(element, path));
        final String path1 = "laborbericht:1/laborbefund:2/pro_laboranalyt:0/bezeichnung_des_analyts|terminology";
        final String element1 = "laborbericht/laborbefund";
        Assert.assertEquals(2, new OpenFhirStringUtils().getIndexOfElement(element1, path1));
    }

    @Test
    public void getLastMostCommonIndex() {
        List<String> paths = Arrays.asList(
                "diagnose:1/diagnose:0/klinischer_status:1/diagnostic_status/abc|code",
                "diagnose:1/diagnose:0/klinischer_status:1/diagnostic_status/abc|terminology",
                "diagnose:1/diagnose:0/klinischer_status:1/diagnostic_status/abc|value",
                "diagnose:1/diagnose:0/klinischer_status:1/klinischer_status:0|terminology",
                "diagnose:1/diagnose:0/klinischer_status:1/klinischer_status:0|code",
                "diagnose:1/diagnose:0/klinischer_status:1/klinischer_status|value",
                "diagnose:1/diagnose:0/klinischer_status:1/klinischer_status2|value",
                "diagnose:1/diagnose:0/klinischer_status:2/klinischer_status2|terminology",
                "diagnose:1/diagnose:0/klinischer_status:2/klinischer_status2|code",
                "diagnose:1/diagnose:0/klinischer_status:2/diagnoserolle:0/ztsd/asdasd/aadd/aa:1/aaa|code",
                "diagnose:1/diagnose:0/klinischer_status:2/diagnoserolle:0/ztsd/asdasd/aadd/aa:1/aaa|terminology",
                "diagnose:1/diagnose:0/klinischer_status:2/diagnoserolle:0/ztsd/asdasd/aadd/aa:1/aaa|value",
                "diagnose:1/diagnose:0/klinischer_status:2/diagnoserolle2|value",
                "diagnose:1/diagnose:0/klinischer_status:2/diagnoserolle2|code",
                "diagnose:1/diagnose:0/klinischer_status:2/diagnoserolle2|terminology"
        );
        int lastMostCommonIndex = new OpenFhirStringUtils().getLastMostCommonIndex(paths);
        Assert.assertEquals(0, lastMostCommonIndex);

        paths = Arrays.asList(
                "diagnose:1/diagnose:0/klinischer_status:3/diagnostic_status/abc|code",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnostic_status/abc|terminology",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnostic_status/abc|value",
                "diagnose:1/diagnose:0/klinischer_status:3/klinischer_status:0|terminology",
                "diagnose:1/diagnose:0/klinischer_status:3/klinischer_status:0|code",
                "diagnose:1/diagnose:0/klinischer_status:3/klinischer_status|value",
                "diagnose:1/diagnose:0/klinischer_status:3/klinischer_status2|value",
                "diagnose:1/diagnose:0/klinischer_status:3/klinischer_status2|terminology",
                "diagnose:1/diagnose:0/klinischer_status:3/klinischer_status2|code",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnoserolle:0/ztsd/asdasd/aadd/aa:1/aaa|code",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnoserolle:0/ztsd/asdasd/aadd/aa:1/aaa|terminology",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnoserolle:0/ztsd/asdasd/aadd/aa:1/aaa|value",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnoserolle2|value",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnoserolle2|code",
                "diagnose:1/diagnose:0/klinischer_status:3/diagnoserolle2|terminology"
        );
        lastMostCommonIndex = new OpenFhirStringUtils().getLastMostCommonIndex(paths);
        Assert.assertEquals(3, lastMostCommonIndex);
    }


    @Test
    public void joinValuesThatAreOne_oneContainsTheOther() {
        final List<String> toJoin = Arrays.asList(
                "diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_der_genesung",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|code",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|terminology",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|terminology",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|code",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|terminology",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|terminology",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|value",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|value",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|terminology",
                "diagnose/diagnose:0/diagnosesicherheit|value",
                "diagnose/diagnose:0/diagnosesicherheit|terminology",
                "diagnose/diagnose:0/diagnosesicherheit|code",
                "diagnose/diagnose:0/diagnosesicherheit2|value",
                "diagnose/diagnose:0/diagnosesicherheit2|code",
                "diagnose/diagnose:0/diagnosesicherheit2|terminology",
                "diagnose/diagnose:0/diagnoseerläuterung",
                "diagnose/diagnose:0/letztes_dokumentationsdatum",
                "diagnose/diagnose:0/language|code",
                "diagnose/diagnose:0/language|terminology"
        );
        final Map<String, List<String>> stringListMap = new OpenEhrToFhir(null, null, null,
                                                                          null, null, null,
                                                                          null, null, null, null,
                                                                          null, null, null).joinValuesThatAreOne(
                toJoin);
        Assert.assertEquals(3, stringListMap.get("diagnose/diagnose:0/klinischer_status/klinischer_status").size());

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive("random")));

        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|code", new JsonPrimitive("44"));
        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de' available"));
        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value",
                           new JsonPrimitive("†"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code",
                           new JsonPrimitive("at0002"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_der_genesung",
                           new JsonPrimitive("2022-02-03T04:05:06"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|code", new JsonPrimitive("at0016"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|value",
                           new JsonPrimitive("Preliminary"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|code", new JsonPrimitive("at0026"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|value",
                           new JsonPrimitive("Active"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|value",
                           new JsonPrimitive("Active"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|code",
                           new JsonPrimitive("at0026"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role' available"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role' available"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT' available"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT' available"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT"));
        flatJsonObject.add("diagnose/diagnose:0/diagnoseerläuterung", new JsonPrimitive("Lorem ipsum"));
        flatJsonObject.add("diagnose/diagnose:0/letztes_dokumentationsdatum", new JsonPrimitive("2022-02-03T04:05:06"));
        flatJsonObject.add("diagnose/diagnose:0/language|code", new JsonPrimitive("en"));

        final List<String> matchingEntries = new OpenFhirStringUtils().getAllEntriesThatMatch(
                new OpenFhirStringUtils().addRegexPatternToSimplifiedFlatFormat(
                        "diagnose/diagnose/klinischer_status/klinischer_status"),
                flatJsonObject);
        Assert.assertEquals(3, matchingEntries.size());
    }

    @Test
    public void getAllEntriesThatMatchIgnoringPipe() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();

        final List<String> toJoin = Arrays.asList(
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|code",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                "stationärer_versorgungsfall/aufnahmedaten/abc|abc",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|value",
                "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|code",
                "stationärer_versorgungsfall/aufnahmedaten/kennung_vor_der_aufnahme",
                "stationärer_versorgungsfall/aufnahmedaten/datum_uhrzeit_der_aufnahme",
                "stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/campus"
        );

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive(tj)));

        Assert.assertEquals("stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                            openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                                    "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                                    flatJsonObject).get(0));
        Assert.assertEquals("stationärer_versorgungsfall/aufnahmedaten/abc|abc",
                            openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                                    "stationärer_versorgungsfall/aufnahmedaten/abc", flatJsonObject).get(0));
        Assert.assertTrue(openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                "stationärer_versorgungsfall/aufnahmedaten/abc/cde", flatJsonObject).isEmpty());
    }

    @Test
    public void joinValuesThatAreOne_dots() {
        final List<String> toJoin = Arrays.asList("stationärer_versorgungsfall/context/start_time",
                                                  "stationärer_versorgungsfall/context/setting|terminology",
                                                  "stationärer_versorgungsfall/context/setting|code",
                                                  "stationärer_versorgungsfall/context/setting|value",
                                                  "stationärer_versorgungsfall/context/_end_time",
                                                  "stationärer_versorgungsfall/context/_health_care_facility|name",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/kennung_vor_der_aufnahme",
                                                  "stationärer_versorgungsfall/aufnahmedaten/datum_uhrzeit_der_aufnahme",
                                                  "stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/campus"
        );

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive("random")));

        final String testingPath = "$archetype.aufnahmedaten.aufnahmegrund_-_1\\._und_2\\._stelle";

        final String prepared = new OpenFhirStringUtils().prepareOpenEhrSyntax(testingPath,
                                                                               "stationärer_versorgungsfall");

        final String withRegex = new OpenFhirStringUtils().addRegexPatternToSimplifiedFlatFormat(prepared);
        final List<String> matchingEntries = new OpenFhirStringUtils().getAllEntriesThatMatch(withRegex,
                                                                                              flatJsonObject);
        Assert.assertEquals(3, matchingEntries.size());

    }

    @Test
    public void getFhirPathWithConditions() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
        Condition condition = new Condition();

        Assert.assertEquals("MedicationStatement.effective.as(Period)",
                            openFhirStringUtils.getFhirPathWithConditions("$resource.effective.as(Period)",
                                                                          null,
                                                                          "MedicationStatement",
                                                                          null));

        Assert.assertEquals("MedicationStatement.medication.resolve().ingredient",
                            openFhirStringUtils.getFhirPathWithConditions(
                                    "MedicationStatement.medication.resolve().ingredient",
                                    null,
                                    "MedicationStatement",
                                    "MedicationStatement.medication.resolve()"));


        condition.setTargetRoot("$resource.location.physicalType.coding");
        condition.setTargetAttribute("code");
        condition.setCriteria("[bd]");
        condition.setOperator("one of");
        Assert.assertEquals("Encounter.location.where(physicalType.coding.code.toString().contains('bd'))",
                            openFhirStringUtils.getFhirPathWithConditions("Encounter.location",
                                                                          condition,
                                                                          "Encounter",
                                                                          null));


        condition.setTargetRoot("$resource.identifier");
        condition.setTargetAttribute("system");
        condition.setCriteria("[external identifier]");
        condition.setOperator("one of");
        Assert.assertEquals("MedicationStatement.identifier.where(system.toString().contains('external identifier'))",
                            openFhirStringUtils.getFhirPathWithConditions("MedicationStatement.identifier",
                                                                          condition,
                                                                          "MedicationStatement",
                                                                          null));

        Assert.assertEquals("MedicationStatement.dosage.text",
                            openFhirStringUtils.getFhirPathWithConditions("MedicationStatement.dosage.text",
                                                                          null,
                                                                          "MedicationStatement",
                                                                          "MedicationStatement.dosage"));

        condition = new Condition();
        condition.setTargetRoot("$resource.identifier");
        condition.setTargetAttribute("system");
        condition.setCriteria("[id]");
        condition.setOperator("one of");
        Assert.assertEquals("Patient.identifier.where(system.toString().contains('id'))",
                            openFhirStringUtils.getFhirPathWithConditions("Patient.identifier",
                                                                          condition,
                                                                          "Patient",
                                                                          "Patient"));

        condition = new Condition();
        condition.setTargetRoot("Procedure.code.coding.extension");
        condition.setTargetAttribute("url");
        condition.setCriteria("[http://fhir.de/StructureDefinition/seitenlokalisation]");
        condition.setOperator("one of");
        Assert.assertEquals(
                "Procedure.code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/ops')).extension.where(url.toString().contains('http://fhir.de/StructureDefinition/seitenlokalisation')).value",
                openFhirStringUtils.getFhirPathWithConditions("Procedure.code.coding.extension.value",
                                                              condition,
                                                              "Procedure",
                                                              "Procedure.code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/ops'))"));

        condition = new Condition();
        condition.setTargetRoot(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().code.coding");
        condition.setTargetAttribute("system");
        condition.setCriteria("[http://fhir.de/CodeSystem/bfarm/icd-10-gm]");
        condition.setOperator("one of");
        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/icd-10-gm')).code",
                openFhirStringUtils.getFhirPathWithConditions(
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().code.coding.code",
                        condition,
                        "Condition",
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().code"));


        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().onset.as(Period)",
                openFhirStringUtils.getFhirPathWithConditions(
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().onset.as(Period)",
                        null,
                        "Condition",
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve()"));

        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value",
                openFhirStringUtils.getFhirPathWithConditions("Condition.extension.value",
                                                              null,
                                                              "Condition",
                                                              "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related'))"));

        condition = new Condition();
        condition.setTargetRoot(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().extension");
        condition.setTargetAttribute("url");
        condition.setCriteria("[http://hl7.org/fhir/StructureDefinition/condition-assertedDate]");
        condition.setOperator("one of");
        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-assertedDate')).value.as(DateTimeType)",
                openFhirStringUtils.getFhirPathWithConditions(
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().extension.value.as(DateTimeType)",
                        condition,
                        "Condition",
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve()"));

        condition = new Condition();
        condition.setTargetRoot(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.as(Reference).resolve().code.coding.extension");
        condition.setTargetAttribute("url");
        condition.setCriteria("[http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen]");
        condition.setOperator("one of");
        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.as(Reference).resolve().code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/icd-10-gm')).extension.where(url.toString().contains('http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen'))",
                openFhirStringUtils.getFhirPathWithConditions(
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.as(Reference).resolve().code.coding.extension",
                        condition,
                        "Condition",
                        "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.as(Reference).resolve().code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/icd-10-gm'))"));


        condition = new Condition();
        condition.setTargetRoot("Condition.location");
        condition.setTargetAttribute("physicalType.coding.code");
        condition.setCriteria("[ro]");
        condition.setOperator("one of");
        Assert.assertEquals("Condition.location.where(physicalType.coding.code.toString().contains('ro')).id",
                            openFhirStringUtils.getFhirPathWithConditions("Condition.location.id",
                                                                          condition,
                                                                          "Condition",
                                                                          null));

        condition = new Condition();
        condition.setTargetRoot("$resource.extension");
        condition.setTargetAttribute("url");
        condition.setCriteria("[http://fhir.de/StructureDefinition/Aufnahmegrund]");
        condition.setOperator("one of");
        Assert.assertEquals(
                "Encounter.extension.where(url.toString().contains('http://fhir.de/StructureDefinition/Aufnahmegrund'))",
                openFhirStringUtils.getFhirPathWithConditions("$resource.extension",
                                                              condition,
                                                              "Encounter",
                                                              null));

        condition = new Condition();
        condition.setTargetRoot("Patient.address.line.extension");
        condition.setTargetAttribute("url");
        condition.setCriteria("[http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName]");
        condition.setOperator("one of");
        Assert.assertEquals(
                "Patient.address.where(type.toString().contains('both')).line.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName')).value",
                openFhirStringUtils.getFhirPathWithConditions("Patient.address.line.extension.value",
                                                              condition,
                                                              "Patient",
                                                              "Patient.address.where(type.toString().contains('both')).line"));

    }

    @Test
    public void testSettingParentsPath() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
        String s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace("Procedure.code.coding.extension.value",
                                                                            "Procedure.code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/ops'))");
        Assert.assertEquals(
                "Procedure.code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/ops')).extension.value",
                s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace("Condition.extension.value",
                                                                     "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related'))");
        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value",
                s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace(
                "Encounter.location.location.as(Reference).identifier.value",
                "Encounter.location.where(physicalType.coding.code.toString().contains('ro'))");
        Assert.assertEquals(
                "Encounter.location.where(physicalType.coding.code.toString().contains('ro')).location.as(Reference).identifier.value",
                s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace("MedicationStatement.effective.as(Period)",
                                                                     null);
        Assert.assertEquals("MedicationStatement.effective.as(Period)", s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace(
                "MedicationStatement.medication.resolve().ingredient",
                "MedicationStatement.medication.resolve()");
        Assert.assertEquals("MedicationStatement.medication.resolve().ingredient", s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace("MedicationStatement.dosage.text",
                                                                     "MedicationStatement.dosage");
        Assert.assertEquals("MedicationStatement.dosage.text", s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().onset.as(Period)",
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve()");
        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.resolve().onset.as(Period)",
                s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.as(Reference).resolve().code.coding.extension",
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.as(Reference).resolve().code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/icd-10-gm'))");
        Assert.assertEquals(
                "Condition.extension.where(url.toString().contains('http://hl7.org/fhir/StructureDefinition/condition-related')).value.as(Reference).resolve().code.coding.where(system.toString().contains('http://fhir.de/CodeSystem/bfarm/icd-10-gm')).extension",
                s);

        s = openFhirStringUtils.setParentsWherePathToTheCorrectPlace(
                "Encounter.location.physicalType.coding.system",
                "Encounter.location.where(physicalType.coding.code.toString().contains('ro')).location.as(Reference).identifier.value");
        Assert.assertEquals(
                "Encounter.location.where(physicalType.coding.code.toString().contains('ro')).physicalType.coding.system",
                s);
    }

}