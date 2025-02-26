package com.medblocks.openfhir.kds;

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
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.junit.Assert;
import org.junit.Test;

public class FallTest extends KdsBidirectionalTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT_MAPPING = "/kds_new/projects/org.highmed/KDS/fall/KDS_fall_einfach.context.yaml";
    final String HELPER_LOCATION = "/kds/fall/";
    final String OPT = "KDS_Fall_einfach.opt";
    final String FLAT = "KDS_Fall_einfach.flat.json";
    final String BUNDLE = "KDS_Fall_einfach_Bundle.json";

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
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allEncounters = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Encounter).collect(Collectors.toList());
        Assert.assertEquals(1, allEncounters.size());

        final Encounter encounter = (Encounter) allEncounters.get(0).getResource();

        // falltyp
        Assert.assertEquals("42", encounter.getTypeFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("No example for termínology 'http://fhir.de/ValueSet/kontaktebene-de?subset=' available",
                            encounter.getTypeFirstRep().getCodingFirstRep().getDisplay());
        Assert.assertEquals("http://fhir.de/ValueSet/kontaktebene-de?subset=",
                            encounter.getTypeFirstRep().getCodingFirstRep().getSystem());

        // fallklasse
        Assert.assertEquals("43", encounter.getClass_().getCode());
        Assert.assertEquals("No example for termínology 'http://fhir.de/ValueSet/EncounterClassDE?subset=' available",
                            encounter.getClass_().getDisplay());
        Assert.assertEquals("http://fhir.de/ValueSet/EncounterClassDE?subset=", encounter.getClass_().getSystem());

        // fallart
        Assert.assertEquals("42", encounter.getStatusElement().getValueAsString());

        // fallId
        Assert.assertEquals("FallId-Id", encounter.getIdentifier().stream()
                .filter(id -> id.getType().getCodingFirstRep().getCode().equals("VN"))
                .map(id -> id.getValue())
                .findFirst().orElse(null));

        // serviceProvider
        final Organization serviceProvider = (Organization) encounter.getServiceProvider().getResource();
        Assert.assertEquals("Org Name", serviceProvider.getName());
        Assert.assertEquals("Org Id", serviceProvider.getIdentifierFirstRep().getValue());

        // aufnahmegrundExtension
        final List<Extension> aufnahmegrundExtension = encounter.getExtensionsByUrl(
                "http://fhir.de/StructureDefinition/Aufnahmegrund");
        Assert.assertEquals(1, aufnahmegrundExtension.size());
        Assert.assertEquals(3, aufnahmegrundExtension.get(0).getExtension().size());

        final Extension firstAndSecond = aufnahmegrundExtension.get(0).getExtensionByUrl("ErsteUndZweiteStelle");
        final Extension third = aufnahmegrundExtension.get(0).getExtensionByUrl("DritteStelle");
        final Extension fourth = aufnahmegrundExtension.get(0).getExtensionByUrl("VierteStelle");
        Assert.assertEquals("12", ((Coding) firstAndSecond.getValue()).getCode());
        Assert.assertEquals("3", ((Coding) third.getValue()).getCode());
        Assert.assertEquals("4", ((Coding) fourth.getValue()).getCode());

        final CodeableConcept admitSource = encounter.getHospitalization().getAdmitSource();
        Assert.assertEquals("admitSource", admitSource.getCodingFirstRep().getCode());

        final Extension entlassungsgrundExtension = encounter.getHospitalization().getDischargeDisposition()
                .getExtensionByUrl("http://fhir.de/StructureDefinition/Entlassungsgrund");
        Assert.assertEquals("outcome", ((Coding) entlassungsgrundExtension.getValue()).getCode());

        final DiagnosisComponent diagnosisComponent = encounter.getDiagnosisFirstRep();
        final Condition condition = (Condition) diagnosisComponent.getCondition().getResource();
        Assert.assertEquals("No example for termínology 'http://fhir.de/ValueSet/DiagnoseTyp?subset=' available", condition.getCode().getCodingFirstRep().getCode());

        Assert.assertEquals(2, diagnosisComponent.getUse().getCoding().size());
        Assert.assertEquals("type", diagnosisComponent.getUse().getCoding().stream()
                .filter(cod -> cod.getSystem().equals("http://fhir.de/CodeSystem/dki-diagnosetyp"))
                .map(Coding::getCode)
                .findFirst().orElse(null));
        Assert.assertEquals("subtype", diagnosisComponent.getUse().getCoding().stream()
                .filter(cod -> cod.getSystem().equals("http://fhir.de/CodeSystem/dki-diagnosesubtyp"))
                .map(Coding::getCode)
                .findFirst().orElse(null));

        //   - name: "period"
        Assert.assertEquals("2020-02-03T04:05:06+01:00", encounter.getPeriod().getStartElement().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", encounter.getPeriod().getEndElement().getValueAsString());

        final List<EncounterLocationComponent> locations = encounter.getLocation();
        Assert.assertEquals(3, locations.size());

        final List<Identifier> roomLocationIds = locations.stream()
                .filter(loc -> "ro".equals(loc.getPhysicalType().getCodingFirstRep().getCode()))
                .map(el -> el.getLocation().getIdentifier())
                .toList();
        final List<Identifier> bedLocationIds = locations.stream()
                .filter(loc -> "bd".equals(loc.getPhysicalType().getCodingFirstRep().getCode()))
                .map(el -> el.getLocation().getIdentifier())
                .toList();
        final List<Identifier> wardLocationIds = locations.stream()
                .filter(loc -> "wa".equals(loc.getPhysicalType().getCodingFirstRep().getCode()))
                .map(el -> el.getLocation().getIdentifier())
                .toList();

        Assert.assertTrue(locations.stream().allMatch(loc -> loc.getPhysicalType().getCodingFirstRep().getSystem()
                .equals("http://terminology.hl7.org/CodeSystem/location-physical-type")));

        Assert.assertEquals(1, roomLocationIds.size());
        Assert.assertEquals(1, bedLocationIds.size());
        Assert.assertEquals(1, wardLocationIds.size());

        Assert.assertEquals("zimmer-1", roomLocationIds.get(0).getValue());
        Assert.assertEquals("bett-1", bedLocationIds.get(0).getValue());
        Assert.assertEquals("station-1", wardLocationIds.get(0).getValue());
    }


    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);

        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("einrichtungskontakt",
                            jsonObject.get("kds_fall_einfach/context/falltyp|code").getAsString());
        Assert.assertEquals("local_terms",
                            jsonObject.get("kds_fall_einfach/context/falltyp|terminology").getAsString());
        Assert.assertEquals("AMB", jsonObject.get("kds_fall_einfach/context/fallklasse|code").getAsString());
        Assert.assertEquals("AMB", jsonObject.get("kds_fall_einfach/context/fallklasse|value").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/v3-ActCode",
                            jsonObject.get("kds_fall_einfach/context/fallklasse|terminology").getAsString());
        Assert.assertEquals("finished", jsonObject.get("kds_fall_einfach/context/fallart|code").getAsString());
        Assert.assertEquals("VN-encounter-id-12345", jsonObject.get("kds_fall_einfach/context/fall-id").getAsString());
        Assert.assertEquals("Example Hospital",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/überweiser/namenszeile")
                                    .getAsString());
        Assert.assertEquals("ORG-001", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/überweiser/identifier:0/identifier_value|id").getAsString());
        Assert.assertEquals("01", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_ersteundzweitestelle|code").getAsString());
        Assert.assertEquals("01", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_ersteundzweitestelle|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundErsteUndZweiteStelle", jsonObject.get(
                        "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_ersteundzweitestelle|terminology")
                .getAsString());
        Assert.assertEquals("0",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_dritte_stelle|code")
                                    .getAsString());
        Assert.assertEquals("0", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_dritte_stelle|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundDritteStelle", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_dritte_stelle|terminology").getAsString());
        Assert.assertEquals("1",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_vierte_stelle|code")
                                    .getAsString());
        Assert.assertEquals("1", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_vierte_stelle|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_vierte_stelle|terminology").getAsString());
        Assert.assertEquals("E", jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmekategorie|code")
                .getAsString());
        Assert.assertEquals("E", jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmekategorie|value")
                .getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dgkev/Aufnahmeanlass",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmekategorie|terminology")
                                    .getAsString());
        Assert.assertEquals("01",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/outcome:0|code").getAsString());
        Assert.assertEquals("01",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/outcome:0|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/entlassungsgrund",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/outcome:0|terminology")
                                    .getAsString());
        Assert.assertEquals("referral-diagnosis",
                            jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosetyp|code").getAsString());
        Assert.assertEquals("referral-diagnosis",
                            jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosetyp|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dki-diagnosetyp",
                            jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosetyp|terminology").getAsString());
        Assert.assertEquals("surgery-diagnosis",
                            jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosesubtyp|code").getAsString());
        Assert.assertEquals("surgery-diagnosis",
                            jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosesubtyp|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dki-diagnosesubtyp",
                            jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosesubtyp|terminology")
                                    .getAsString());
        Assert.assertEquals("Organization",
                            jsonObject.get("kds_fall_einfach/context/_health_care_facility|name").getAsString());
        Assert.assertEquals("2022-02-03T05:05:06", jsonObject.get("kds_fall_einfach/context/start_time").getAsString());
        Assert.assertEquals("2022-04-03T06:05:06", jsonObject.get("kds_fall_einfach/context/_end_time").getAsString());
        Assert.assertEquals("Organization", jsonObject.get("kds_fall_einfach/composer|name").getAsString());
        Assert.assertEquals("2022-02-03T05:05:06",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmedatum").getAsString());
        Assert.assertEquals("2022-04-03T06:05:06",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/entlassungsdatum").getAsString());
        Assert.assertEquals("zimmer-1-identifier",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/standort:0/zimmer").getAsString());
        Assert.assertEquals("station-1-identifier",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/standort:0/station").getAsString());
        Assert.assertEquals("bett-1-identifier",
                            jsonObject.get("kds_fall_einfach/institutionsaufenthalt/standort:0/bett").getAsString());

        return jsonObject;
    }
}
