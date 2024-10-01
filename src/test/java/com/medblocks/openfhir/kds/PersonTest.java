package com.medblocks.openfhir.kds;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class PersonTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/person/";
    final String OPT = "KDS_Person_v2.opt";
    final String FLAT = "KDSPerson.flat.json";
    final String CONTEXT = "person.context.yaml";
    final String BUNDLE = "KDS_Person-Fhir-Bundle-input.json";

    @Override
    protected void prepareState() {
        context = getContext(RESOURCES_ROOT + CONTEXT);
        repo.initRepository(context, getClass().getResource(RESOURCES_ROOT).getFile());
        operationaltemplate = getOperationalTemplate(RESOURCES_ROOT + OPT);
        webTemplate = new OPTParser(operationaltemplate).parse();
    }


    @Test
    public void kdsPerson_toFhir() throws IOException {
        // openEHR to FHIR
        final String initialFlat = getFlat(RESOURCES_ROOT + FLAT);
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(initialFlat, webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<Patient> patients = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Patient)
                .map(en -> (Patient) en.getResource())
                .collect(Collectors.toList());

        final List<Condition> conditions = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Condition)
                .map(en -> (Condition) en.getResource())
                .collect(Collectors.toList());

        final List<Observation> observations = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Observation)
                .map(en -> (Observation) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, patients.size());
        final Patient thePatient = patients.get(0);
        Assert.assertEquals("D", ((Coding) thePatient.getExtensionByUrl("extension_url_to_be_defined").getValue()).getCode());
        Assert.assertEquals("divers", ((Coding) thePatient.getExtensionByUrl("extension_url_to_be_defined").getValue()).getDisplay());
        Assert.assertEquals("http://fhir.de/ValueSet/gender-other-de", ((Coding) thePatient.getExtensionByUrl("extension_url_to_be_defined").getValue()).getSystem());
        Assert.assertEquals("F", thePatient.getGenderElement().getValueAsString());
        final List<HumanName> names = thePatient.getName();
        Assert.assertEquals(2, names.size());
        Assert.assertEquals("Given1", names.get(0).getGivenAsSingleString());
        Assert.assertEquals("official", names.get(0).getUseElement().getCode());
        Assert.assertEquals("Given2", names.get(1).getGivenAsSingleString());
        Assert.assertEquals("maiden", names.get(1).getUseElement().getCode());
        Assert.assertEquals("family1", names.get(1).getFamily());
        Assert.assertEquals("family1 prefix", names.get(1).getPrefix().get(0).getValue());
        Assert.assertEquals("sufix0", names.get(0).getSuffix().get(0).getValue());
        Assert.assertEquals("sufix1", names.get(1).getSuffix().get(0).getValue());
        Assert.assertEquals(3, thePatient.getIdentifier().size());
        Assert.assertEquals("dev/null", thePatient.getIdentifier().stream().filter(id -> id.getSystem().equals("id")).findFirst().orElse(null).getValue());
        Assert.assertEquals("gkv id value", thePatient.getIdentifier().stream().filter(id -> id.getSystem().equals("gkv")).findFirst().orElse(null).getValue());
        Assert.assertEquals("pkv id value", thePatient.getIdentifier().stream().filter(id -> id.getSystem().equals("pkv")).findFirst().orElse(null).getValue());

        final Address strasseAddress = thePatient.getAddress().get(0);
        final Address postfachAddress = thePatient.getAddress().get(1);
        final List<StringType> lines = strasseAddress.getLine();
        Assert.assertEquals(5, lines.size());
        Assert.assertEquals(2, postfachAddress.getLine().size());
        Assert.assertEquals("postfach city", postfachAddress.getCity());
        Assert.assertTrue(lines.stream().allMatch(line ->
                line.getValue().equals("gemeindeschlüssel")
                        || line.getValue().equals("strasse")
                        || line.getValue().equals("hausnummer")
                        || line.getValue().equals("adresszusatz")
                        || line.getValue().equals("stadtteil")));
        Assert.assertTrue(postfachAddress.getLine().stream().allMatch(line ->
                line.getValue().equals("postfach town")
                        || line.getValue().equals("postfach line 0")));

        Assert.assertEquals("postal strasse", strasseAddress.getPostalCode());
        Assert.assertEquals("postal postfach", postfachAddress.getPostalCode());

        final Organization org = (Organization) thePatient.getManagingOrganization().getResource();
        Assert.assertEquals("org name", org.getName());
        Assert.assertEquals("org id val", org.getIdentifierFirstRep().getValue());

        Assert.assertEquals("Thu Feb 03 00:00:00 CET 2022", thePatient.getBirthDate().toString());
        Assert.assertEquals(true, ((BooleanType) thePatient.getBirthDateElement().getExtensionByUrl("isdeceased").getValue()).getValue());

        Assert.assertEquals("2022-02-03T04:05:06+01:00", thePatient.getDeceasedDateTimeType().getValueAsString());

        Assert.assertEquals(1, conditions.size());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/bfarm/icd-10-gm' available", conditions.get(0).getCode().getText());
        Assert.assertEquals("42", conditions.get(0).getCode().getCodingFirstRep().getCode());

        final Patient.ContactComponent zerothContact = thePatient.getContact().get(0);
        final CodeableConcept firstRelationshipCodeable = zerothContact.getRelationship().get(0);
        final CodeableConcept secondRelationshipCodeable = zerothContact.getRelationship().get(1);

        Assert.assertEquals("contact person name", zerothContact.getName().getText());

        Assert.assertEquals("No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/patient-contactrelationship' available", firstRelationshipCodeable.getText());
        Assert.assertEquals("42", firstRelationshipCodeable.getCodingFirstRep().getCode());


        Assert.assertEquals("another contact person role relationship", secondRelationshipCodeable.getText());
        Assert.assertEquals("43", secondRelationshipCodeable.getCodingFirstRep().getCode());


        Assert.assertEquals("contact person address", zerothContact.getAddress().getText());
        Assert.assertEquals("contact person telecom value", zerothContact.getTelecomFirstRep().getValue());

        Assert.assertEquals(1, observations.size());
        Assert.assertEquals("vital status text", observations.get(0).getNoteFirstRep().getText());
        Assert.assertEquals("final", observations.get(0).getStatusElement().getCode());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", observations.get(0).getEffectiveDateTimeType().getValueAsString());


        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, bundle, operationaltemplate);

        final JsonObject expected = new Gson()
                .fromJson(IOUtils.toString(getClass().getResourceAsStream(RESOURCES_ROOT + "Patient_expected-jsonobject-from-flat.json")),
                        JsonObject.class);

        compareJsonObjects(jsonObject, expected);
    }

    @Test
    public void kdsPerson_toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);

        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("Jane", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/vollständiger_name").getAsString());
        Assert.assertEquals("Doe", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/familienname-nachname").getAsString());
        Assert.assertEquals("Ms", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/familienname-vorsatzwort").getAsString());
        Assert.assertEquals("Jr1", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/suffix:0").getAsString());
        Assert.assertEquals("Jr2", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/suffix:1").getAsString());
        Assert.assertEquals("Emily", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/vollständiger_name").getAsString());
        Assert.assertEquals("MaidenDoe", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/familienname-nachname").getAsString());
        Assert.assertEquals("Jr3", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/suffix:0").getAsString());
        Assert.assertEquals("Jr4", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/suffix:1").getAsString());

        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/straße:0").getAsString());
        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/hausnummer:0").getAsString());
        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/adresszusatz:0").getAsString());
        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/stadtteil").getAsString());

        Assert.assertEquals("123 City", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/stadt").getAsString());
        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/gemeindeschlüssel").getAsString());
        Assert.assertEquals("CA123", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/bundesland").getAsString());
        Assert.assertEquals("12390210", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/postleitzahl").getAsString());

        Assert.assertEquals("PO Box 456", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/straße:0").getAsString());
        Assert.assertEquals("PO Box 456", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/hausnummer:0").getAsString());
        Assert.assertEquals("PO Box 456", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/adresszusatz:0").getAsString());
        Assert.assertEquals("PO Box 456", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/stadtteil").getAsString());

        Assert.assertEquals("456 City", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/stadt").getAsString());
        Assert.assertEquals("PO Box 456", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/gemeindeschlüssel").getAsString());
        Assert.assertEquals("CA456", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/bundesland").getAsString());
        Assert.assertEquals("45690210", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:1/postleitzahl").getAsString());
        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/postfach:0").getAsString());
        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/stadtteil").getAsString());
        Assert.assertEquals("123 City", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/stadt").getAsString());
        Assert.assertEquals("CA123", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/bundesland").getAsString());
        Assert.assertEquals("12390210", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/postleitzahl").getAsString());
        Assert.assertEquals("true", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/verstorben").getAsString());
        Assert.assertEquals("2022-02-03T04:05:06", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/sterbedatum").getAsString());
        Assert.assertEquals("1990-01-01", jsonObject.getAsJsonPrimitive("person/personendaten/daten_zur_geburt/geburtsdatum").getAsString());
        Assert.assertEquals("emergency", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/rolle_relationship:0|code").getAsString());
        Assert.assertEquals("http://hl7.org/fhir/ValueSet/contact-relationship", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/rolle_relationship:0|terminology").getAsString());
        Assert.assertEquals("+1-555-1234", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/elektronische_kommunikation:0/daten/text_value").getAsString());
        Assert.assertEquals("jane.doe@example.com", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/elektronische_kommunikation:1/daten/text_value").getAsString());

        Assert.assertEquals("dev/null", jsonObject.getAsJsonPrimitive("person/personendaten/person/pid:0|id").getAsString());
        Assert.assertEquals("pkv id value", jsonObject.getAsJsonPrimitive("person/personendaten/person/versicherungsnummer_pkv|id").getAsString());
        Assert.assertEquals("gkv id value", jsonObject.getAsJsonPrimitive("person/personendaten/person/versicherten_id_gkv|id").getAsString());
        Assert.assertEquals("female", jsonObject.getAsJsonPrimitive("person/geschlecht/administratives_geschlecht|code").getAsString());
        Assert.assertEquals("contact person address", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/adresse/adresszeile").getAsString());
        Assert.assertEquals("contact person name", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/name").getAsString());
        Assert.assertEquals("I60", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|code").getAsString());
        Assert.assertEquals("http://hl7.org/fhir/sid/icd-10", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|terminology").getAsString());
        Assert.assertEquals("Subarachnoid hemorrhage", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|value").getAsString());
        Assert.assertEquals("Example Administrative Organization", jsonObject.getAsJsonPrimitive("person/personendaten/person/verwaltungsorganisation/namenszeile").getAsString());
        Assert.assertEquals("ORG-98765", jsonObject.getAsJsonPrimitive("person/personendaten/person/verwaltungsorganisation/identifier:0/identifier_value|id").getAsString());
        Assert.assertEquals("vital status text", jsonObject.getAsJsonPrimitive("person/vitalstatus/vitalstatus").getAsString());
        Assert.assertEquals("final", jsonObject.getAsJsonPrimitive("person/vitalstatus/fhir_status_der_beobachtung/status").getAsString());
        Assert.assertEquals("2022-02-03T04:05:06", jsonObject.getAsJsonPrimitive("person/vitalstatus/zeitpunkt_der_feststellung").getAsString());

        // run this so you assert it properly "compiles" to Composition (so all flat paths are in fact valid)
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, testBundle, operationaltemplate);

    }
}
