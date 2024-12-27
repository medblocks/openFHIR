package com.medblocks.openfhir.util;

import com.medblocks.openfhir.fc.schema.model.Condition;
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