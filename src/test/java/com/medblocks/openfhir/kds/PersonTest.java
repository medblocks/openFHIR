package com.medblocks.openfhir.kds;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
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
    final String OPT = "KDS_Person.opt";
    final String FLAT = "KDS_Person.flat.json";
    final String CONTEXT = "person.context.yaml";
    final String BUNDLE = "kds_person_bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(RESOURCES_ROOT + CONTEXT);
        repo.initRepository(context, getClass().getResource(RESOURCES_ROOT).getFile());
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(RESOURCES_ROOT + OPT));
        operationaltemplate = getOperationalTemplate();
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);

        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        /**
         * fix blocking issues
         */
        jsonObject.addProperty("person/personendaten/person/name/namensart|value", "official");
        jsonObject.addProperty("person/personendaten/person/geburtsname/namensart|value", "official");
        jsonObject.addProperty("person/personendaten/person/postfach/bundesland|code", "DE-BE");
        jsonObject.addProperty("person/personendaten/person/postfach/land|code", "DE");
        jsonObject.addProperty("person/personendaten/person/postfach/art|code", "postal");
        jsonObject.addProperty("person/personendaten/person/postfach/art|terminology", "address-type");
        jsonObject.addProperty("person/personendaten/person/straßenanschrift:0/land|code", "DE");
        jsonObject.addProperty("person/personendaten/person/straßenanschrift:0/land|code", "DE");
        jsonObject.addProperty("person/personendaten/person/straßenanschrift:0/art|code", "postal");
        jsonObject.addProperty("person/personendaten/person/straßenanschrift:0/art|terminology", "address-type");
        jsonObject.addProperty("person/geschlecht/administratives_geschlecht|value", "Male");
        jsonObject.addProperty("person/personendaten/person/straßenanschrift:0/bundesland|code", "DE-BE");
        /**
         * End of: fix blocking issues
         */

        Assert.assertEquals("PID987654321", jsonObject.getAsJsonPrimitive("person/personendaten/person/pid:0|id").getAsString());
        Assert.assertEquals("Von Smith", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname/vollständiger_name").getAsString());
        Assert.assertEquals("maiden", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname/namensart|code").getAsString());
        Assert.assertEquals("Von Smith", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname/familienname").getAsString());
        Assert.assertEquals("Smith", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname/familienname-nachname").getAsString());
        Assert.assertEquals("Von", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname/familienname-namenszusatz").getAsString());
        Assert.assertEquals("zu", jsonObject.getAsJsonPrimitive("person/personendaten/person/geburtsname/familienname-vorsatzwort").getAsString());
        Assert.assertEquals("John Doe", jsonObject.getAsJsonPrimitive("person/personendaten/person/name/vollständiger_name").getAsString());
        Assert.assertEquals("official", jsonObject.getAsJsonPrimitive("person/personendaten/person/name/namensart|code").getAsString());
        Assert.assertEquals("John", jsonObject.getAsJsonPrimitive("person/personendaten/person/name/vorname:0").getAsString());
        Assert.assertEquals("John Doe", jsonObject.getAsJsonPrimitive("person/personendaten/person/name/familienname").getAsString());
        Assert.assertEquals("John", jsonObject.getAsJsonPrimitive("person/personendaten/person/name/familienname-nachname").getAsString());
        Assert.assertEquals("Doe", jsonObject.getAsJsonPrimitive("person/personendaten/person/name/familienname-namenszusatz").getAsString());
        Assert.assertEquals("zu", jsonObject.getAsJsonPrimitive("person/personendaten/person/name/familienname-vorsatzwort").getAsString());

        Assert.assertEquals("Hamburg", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/gemeindeschlüssel").getAsString());
        Assert.assertEquals("Hamburg", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/bundesland|value").getAsString());
        Assert.assertEquals("20095", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/postleitzahl").getAsString());
        Assert.assertEquals("Mitte", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/stadtteil").getAsString());
        Assert.assertEquals("Hamburg", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/stadt").getAsString());
        Assert.assertEquals("Germany", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/land|value").getAsString());
        Assert.assertEquals("both", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/art|value").getAsString());
        Assert.assertEquals("123 Main St", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/straße:0").getAsString());
        Assert.assertEquals("Apt 4B", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/hausnummer:0").getAsString());
        Assert.assertEquals("Wohnung 3", jsonObject.getAsJsonPrimitive("person/personendaten/person/straßenanschrift:0/adresszusatz:0").getAsString());

        Assert.assertEquals("Berlin", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/gemeindeschlüssel").getAsString());
        Assert.assertEquals("Berlin", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/bundesland|value").getAsString());
        Assert.assertEquals("Berlin", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/stadt").getAsString());
        Assert.assertEquals("10997", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/postleitzahl").getAsString());
        Assert.assertEquals("Kreuzberg", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/stadtteil").getAsString());
        Assert.assertEquals("Germany", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/land|value").getAsString());
        Assert.assertEquals("postal", jsonObject.getAsJsonPrimitive("person/personendaten/person/postfach/art|value").getAsString());

        Assert.assertEquals("GKV123456789", jsonObject.getAsJsonPrimitive("person/personendaten/person/versicherten_id_gkv|id").getAsString());
        Assert.assertEquals("PKV543210987", jsonObject.getAsJsonPrimitive("person/personendaten/person/versicherungsnummer_pkv|id").getAsString());

        Assert.assertEquals("1980-01-01", jsonObject.getAsJsonPrimitive("person/personendaten/daten_zur_geburt/geburtsdatum").getAsString());

        Assert.assertEquals("emergency", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/rolle_relationship:0|code").getAsString());
        Assert.assertEquals("http://hl7.org/fhir/ValueSet/contact-relationship", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/rolle_relationship:0|terminology").getAsString());
        Assert.assertEquals("Emergency Contact", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/rolle_relationship:0|value").getAsString());

        Assert.assertEquals("+1-555-1234", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/elektronische_kommunikation:0/daten/text_value").getAsString());
        Assert.assertEquals("jane.doe@example.com", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/elektronische_kommunikation:1/daten/text_value").getAsString());

        Assert.assertEquals("Example Health Clinic", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/organisation/namenszeile").getAsString());
        Assert.assertEquals("ORG-12345", jsonObject.getAsJsonPrimitive("person/personendaten/kontaktperson/organisation/identifier:0/identifier_value|id").getAsString());

        Assert.assertEquals("16100001", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|terminology").getAsString());
        Assert.assertEquals("Cause of death", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|value").getAsString());
        Assert.assertEquals("2024-08-24T02:00:00", jsonObject.getAsJsonPrimitive("person/personendaten/angaben_zum_tod/angaben_zum_tod/sterbedatum").getAsString());

        Assert.assertEquals("The patient is recorded Dead. Cause of death is based on the patient's medical history.", jsonObject.getAsJsonPrimitive("person/vitalstatus/vitalstatus").getAsString());
        Assert.assertEquals("final", jsonObject.getAsJsonPrimitive("person/vitalstatus/fhir_status_der_beobachtung/status").getAsString());
        Assert.assertEquals("2024-08-21T16:30:00", jsonObject.getAsJsonPrimitive("person/vitalstatus/zeitpunkt_der_feststellung").getAsString());

        Assert.assertEquals("male", jsonObject.getAsJsonPrimitive("person/geschlecht/administratives_geschlecht|code").getAsString());


        return jsonObject;

    }
}
