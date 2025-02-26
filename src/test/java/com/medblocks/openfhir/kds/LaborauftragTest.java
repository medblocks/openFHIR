package com.medblocks.openfhir.kds;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.Assert;
import org.junit.Test;

public class LaborauftragTest extends KdsBidirectionalTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT = "/kds_new/projects/org.highmed/KDS/laborauftrag/KDS_laborauftrag.context.yaml";
    final String HELPER_LOCATION = "/kds/laborauftrag/";
    final String OPT = "KDS_Laborauftrag.opt";
    final String FLAT = "KDS_Laborauftrag.flat.json";

    final String BUNDLE = "KDS_Laborauftrag_bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void kdsServiceRequest_toFhir_toOpenEhr() throws IOException {

        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                                     webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<ServiceRequest> requests = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof ServiceRequest)
                .map(en -> (ServiceRequest) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, requests.size());
        final ServiceRequest serviceRequest = requests.get(0);

        assertServiceRequest(serviceRequest);


        final Bundle toRunMappingOn = new Bundle();
        for (Bundle.BundleEntryComponent bundleEntryComponent : bundle.getEntry()) {
            final Resource resource = bundleEntryComponent.getResource();
            if (resource instanceof ServiceRequest) {
                final Reference requesterReference = ((ServiceRequest) resource).getRequester();
                final IBaseResource requester = requesterReference.getResource();
                if (requester != null) {
                    final String reqId = UUID.randomUUID().toString();
                    requesterReference.setReference(reqId);
                    requester.setId(reqId);
                    toRunMappingOn.addEntry(
                            new Bundle.BundleEntryComponent().setFullUrl(reqId).setResource((Resource) requester));
                }
            }
            toRunMappingOn.addEntry(bundleEntryComponent);
        }

        final JsonObject jsonObject2 = fhirToOpenEhr.fhirToFlatJsonObject(context, toRunMappingOn, operationaltemplate);


        final JsonObject expected = new Gson().fromJson(IOUtils.toString(
                                                                getClass().getResourceAsStream(HELPER_LOCATION + "Laborauftrag_expected-jsonobject-from-flat.json")),
                                                        JsonObject.class);


        // todo: enable comaprison once https://github.com/medblocks/openFHIR/pull/79/files#r1956650118 is solved
//        compareJsonObjects(jsonObject2, expected);
//        compareJsonObjects(expected, jsonObject2);

        // do this just to assert all flat paths are legit
        new FlatJsonUnmarshaller().unmarshal(new Gson().toJson(jsonObject2), webTemplate);

    }

    @SneakyThrows
    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);


        Assert.assertEquals("123456-0_KH",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/auftrags-id_des_anfordernden_einsendenden_systems_plac|id").getAsString());
        Assert.assertEquals("completed",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/status_der_anfrage|code").getAsString());
        Assert.assertEquals("2345-7",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/name_der_laborleistung|code").getAsString());
        Assert.assertEquals("http://loinc.org",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/name_der_laborleistung|terminology").getAsString());
        Assert.assertEquals("Blood Glucose Test",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/name_der_laborleistung|value").getAsString());
//        Assert.assertEquals("order",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/art_der_laborleistung_kategorie|code").getAsString());
//        Assert.assertEquals("http://terminology.hl7.org/ValueSet/observation-category",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/art_der_laborleistung_kategorie|terminology").getAsString());
//        Assert.assertEquals("Laboratory",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/art_der_laborleistung_kategorie|value").getAsString());
        Assert.assertEquals("order",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/intention|code").getAsString());
        Assert.assertEquals("Sample collected in the morning.",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/kommentar").getAsString());
        Assert.assertEquals("Example Hospital",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/einsender/namenszeile").getAsString());
        Assert.assertEquals("ORG-001",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/einsender/identifier|id").getAsString());

        Assert.assertEquals("SP-987654",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/probe:0/laborprobenidentifikator|id").getAsString());
        Assert.assertEquals("2024-08-24T11:00:00",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/probe:0/zeitpunkt_der_probenentnahme/date_time_value").getAsString());
        Assert.assertEquals("example-practitioner",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/probe:0/identifikator_des_probenehmers|id").getAsString());


        return jsonObject;
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                                     new OPTParser(
                                                                                             operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allServiceRequests = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof ServiceRequest).collect(Collectors.toList());
        assertEquals(1, allServiceRequests.size());

        final ServiceRequest serviceRequest = (ServiceRequest) allServiceRequests.get(0).getResource();

        assertServiceRequest(serviceRequest);
    }

    private void assertServiceRequest(final ServiceRequest serviceRequest) {

        //  - name: "identifier"
        Assert.assertEquals("Medical record identifier", serviceRequest.getIdentifierFirstRep().getValue());

        //  - name: "status"
        Assert.assertEquals("completed", serviceRequest.getStatusElement().getValueAsString());

        //  - name: "code"
        Assert.assertEquals("2345-7", serviceRequest.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("Blood Glucose Test", serviceRequest.getCode().getText());

        //  - name: "category"
        Assert.assertEquals("laboratory", serviceRequest.getCategoryFirstRep().getCodingFirstRep().getCode());

        //  - name: "intent"
        Assert.assertEquals("order", serviceRequest.getIntentElement().getValueAsString());

        //  - name: "note"
        Assert.assertEquals("Sample collected in the morning.", serviceRequest.getNoteFirstRep().getText());

        //  - name: "organisation"
        //  - name: "org name"
        //  - name: "org id"
        final Organization org = (Organization) serviceRequest.getRequester().getResource();
        Assert.assertEquals("Einsender name", org.getName());
        Assert.assertEquals("Example Hospital", org.getIdentifierFirstRep().getValue());

        //  - name: "specimen"
        final List<Reference> specimenReferences = serviceRequest.getSpecimen();
        Assert.assertEquals(2, specimenReferences.size());
        final List<Specimen> specimens = specimenReferences.stream().map(spec -> (Specimen) spec.getResource())
                .toList();

        //  - name: "specimen identifier"
        //  - name: "specimen collection date time"
        //  - name: "specimen collector"
        final Specimen specimen1 = specimens.get(0);
        Assert.assertEquals("spec1", specimen1.getAccessionIdentifier().getValue());
//        Assert.assertEquals("2022-02-03T04:05:06+01:00",
//                            specimen1.getCollection().getCollectedPeriod().getStartElement().getValueAsString());
        Assert.assertEquals("probenehmers_id1", specimen1.getCollection().getCollector().getIdentifier().getValue());

        final Specimen specimen2 = specimens.get(1);
        Assert.assertEquals("spec2", specimen2.getAccessionIdentifier().getValue());
//        Assert.assertEquals("3022-02-03T04:05:06+01:00",
//                            specimen2.getCollection().getCollectedDateTimeType().getValueAsString());
        Assert.assertEquals("probenehmers_id2", specimen2.getCollection().getCollector().getIdentifier().getValue());

    }

}
