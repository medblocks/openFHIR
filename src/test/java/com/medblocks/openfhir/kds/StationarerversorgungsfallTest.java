package com.medblocks.openfhir.kds;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class StationarerversorgungsfallTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/stationarerversorgungsfall/";
    final String OPT = "Stationärer Versorgungsfall.opt";
    final String FLAT = "KDS_StationarerVersorgungsfall.flat.json";
    final String CONTEXT = "kds_fall_einfach.context.yaml";
    final String BUNDLE = "KDS_StationarerVersorgungsfall_bundle.json";

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

        // Identifier
        Assert.assertEquals("encounter-id-12345", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/context/fall-kennung").getAsString());

        // "Fachabteilungsschluessel-erweitert"
        Assert.assertEquals("dept", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/vorherige_verantwortliche_organisationseinheit_vor_aufnahme/typ|code").getAsString());
        Assert.assertEquals("local_terms", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/vorherige_verantwortliche_organisationseinheit_vor_aufnahme/typ|terminology").getAsString());

        // "discharge disposition" todo: fix this once mapping is confirmed to not be the same as the extension one
//        Assert.assertEquals("home", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/entlassungsdaten/entlassungsgrund|code").getAsString());
//        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/discharge-disposition", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/entlassungsdaten|terminology").getAsString());

        // discharge disposition extension
        Assert.assertEquals("01", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/entlassungsdaten/entlassungsgrund|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/entlassungsgrund", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/entlassungsdaten/entlassungsgrund|terminology").getAsString());

        // period start, end
        Assert.assertEquals("2022-02-03T05:05:06", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/datum_uhrzeit_der_aufnahme").getAsString());
        Assert.assertEquals("2022-04-03T06:05:06", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/entlassungsdaten/datum_uhrzeit_der_entlassung").getAsString());

        // service type coding
        Assert.assertEquals("dept", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/vorherige_verantwortliche_organisationseinheit_vor_aufnahme/organisationsschlüssel|code").getAsString());
        Assert.assertEquals("local_terms", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/vorherige_verantwortliche_organisationseinheit_vor_aufnahme/organisationsschlüssel|terminology").getAsString());


        Assert.assertEquals("zimmer-1", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/zimmer").getAsString());
        Assert.assertEquals("bett-1", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/bettstellplatz").getAsString());
        Assert.assertEquals("station-1", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/station").getAsString());

        Assert.assertEquals("01", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundErsteUndZweiteStelle", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|terminology").getAsString());

        Assert.assertEquals("0", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundDritteStelle", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|terminology").getAsString());

        Assert.assertEquals("1", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle", jsonObject.getAsJsonPrimitive("stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|terminology").getAsString());


        return jsonObject;
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allEncounters = bundle.getEntry().stream().filter(en -> en.getResource() instanceof Encounter).collect(Collectors.toList());
        Assert.assertEquals(1, allEncounters.size());
        final Encounter encounter = (Encounter) allEncounters.get(0).getResource();
        final List<Identifier> identifiersOfSmartHealthIt = encounter.getIdentifier().stream().filter(id -> id.getSystem().equals("http://hospital.smarthealthit.org")).collect(Collectors.toList());
        Assert.assertEquals(1, identifiersOfSmartHealthIt.size());
        Assert.assertEquals("encounter-id-12345", identifiersOfSmartHealthIt.get(0).getValue());

        final Extension aufnahmegrundExtension = encounter.getExtensionByUrl("http://fhir.de/StructureDefinition/Aufnahmegrund");
        final List<Extension> aufnahmegrundExtensionExtensions = aufnahmegrundExtension.getExtension();
        Assert.assertEquals(3, aufnahmegrundExtensionExtensions.size());

        final List<Extension> ersteUndZweiteStelle = aufnahmegrundExtensionExtensions.stream().filter(ext -> ext.getUrl().equals("ErsteUndZweiteStelle"))
                .toList();
        Assert.assertEquals(1, ersteUndZweiteStelle.size());
        Assert.assertEquals("01", ((Coding) ersteUndZweiteStelle.get(0).getValue()).getCode());
        Assert.assertEquals("Krankenhausbehandlung, vollstationär", ((Coding) ersteUndZweiteStelle.get(0).getValue()).getDisplay());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/dkgev/AufnahmegrundErsteUndZweiteStelle", ((Coding) ersteUndZweiteStelle.get(0).getValue()).getSystem());

        final List<Extension> dritteStelle = aufnahmegrundExtensionExtensions.stream().filter(ext -> ext.getUrl().equals("DritteStelle"))
                .toList();
        Assert.assertEquals(1, dritteStelle.size());
        Assert.assertEquals("0", ((Coding) dritteStelle.get(0).getValue()).getCode());
        Assert.assertEquals("Anderes", ((Coding) dritteStelle.get(0).getValue()).getDisplay());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/dkgev/AufnahmegrundDritteStelle", ((Coding) dritteStelle.get(0).getValue()).getSystem());


        final List<Extension> vierteStelle = aufnahmegrundExtensionExtensions.stream().filter(ext -> ext.getUrl().equals("VierteStelle"))
                .toList();
        Assert.assertEquals(1, vierteStelle.size());
        Assert.assertEquals("1", ((Coding) vierteStelle.get(0).getValue()).getCode());
        Assert.assertEquals("Normalfall", ((Coding) vierteStelle.get(0).getValue()).getDisplay());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/dkgev/AufnahmegrundVierteStelle", ((Coding) vierteStelle.get(0).getValue()).getSystem());

        final List<Coding> serviceTypeCodings = encounter.getServiceType().getCoding();
        Assert.assertEquals(2, serviceTypeCodings.size());
        Assert.assertEquals("42", serviceTypeCodings.stream().filter(stc -> stc.getSystem().equals("local_terms")).map(sct -> sct.getCode()).findFirst().orElse(null));
        Assert.assertEquals("No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/dkgev/Fachabteilungsschluessel-erweitert' available", serviceTypeCodings.stream().filter(stc -> stc.getSystem().equals("local_terms")).map(sct -> sct.getDisplay()).findFirst().orElse(null));
        Assert.assertEquals("E", serviceTypeCodings.stream().filter(stc -> stc.getSystem().equals("local_terms")).map(sct -> sct.getCode()).collect(Collectors.toList()).get(1));
        Assert.assertEquals("Einweisung durch einen Arzt", serviceTypeCodings.stream().filter(stc -> stc.getSystem().equals("local_terms")).map(sct -> sct.getDisplay()).collect(Collectors.toList()).get(1));

        Assert.assertEquals("2022-04-03T04:05:06+02:00", encounter.getPeriod().getStartElement().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", encounter.getPeriod().getEndElement().getValueAsString());

        final Encounter.EncounterHospitalizationComponent hospitalization = encounter.getHospitalization();
        final List<Coding> admitSource = hospitalization.getAdmitSource().getCoding();
        Assert.assertEquals(1, admitSource.size());
        Assert.assertEquals("E", admitSource.get(0).getCode());
        Assert.assertEquals("Einweisung durch einen Arzt", admitSource.get(0).getDisplay());


        final List<Coding> dischargeDispositionCodings = hospitalization.getDischargeDisposition().getCoding();
        final Extension dischargeDispositionExtension = hospitalization.getDischargeDisposition().getExtensionByUrl("http://fhir.de/StructureDefinition/Entlassungsgrund");

        Assert.assertEquals(1, dischargeDispositionCodings.size());
        Assert.assertEquals("42", dischargeDispositionCodings.get(0).getCode());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/dkgev/EntlassungsgrundErsteUndZweiteStelle' available", dischargeDispositionCodings.get(0).getDisplay());

        final Coding dischargeDispositionExtensionValue = (Coding) dischargeDispositionExtension.getValue();
        Assert.assertEquals("42", dischargeDispositionExtensionValue.getCode());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/dkgev/EntlassungsgrundErsteUndZweiteStelle' available", dischargeDispositionExtensionValue.getDisplay());

        final List<Encounter.EncounterLocationComponent> locations = encounter.getLocation();
        Assert.assertEquals(3, locations.size());
        final List<String> roomLocationIds = locations.stream().filter(loc -> "ro".equals(loc.getPhysicalType().getCodingFirstRep().getCode()))
                .map(loc -> loc.getId())
                .collect(Collectors.toList());
        final List<String> bedLocationIds = locations.stream().filter(loc -> "bd".equals(loc.getPhysicalType().getCodingFirstRep().getCode()))
                .map(loc -> loc.getId())
                .collect(Collectors.toList());
        final List<String> wardLocationIds = locations.stream().filter(loc -> "wa".equals(loc.getPhysicalType().getCodingFirstRep().getCode()))
                .map(loc -> loc.getId())
                .collect(Collectors.toList());

        Assert.assertEquals(1, roomLocationIds.size());
        Assert.assertEquals(1, bedLocationIds.size());
        Assert.assertEquals(1, wardLocationIds.size());

        Assert.assertEquals("zimmer-1", roomLocationIds.get(0));
        Assert.assertEquals("bett-1", bedLocationIds.get(0));
        Assert.assertEquals("station-1", wardLocationIds.get(0));

    }

}
