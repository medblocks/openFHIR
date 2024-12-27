package com.medblocks.openfhir.kds;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@Ignore
public class LaborauftragTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/laborauftrag/";
    final String OPT = "KDS_Laborauftrag.opt";
    final String FLAT = "KDS_Laborauftrag.flat.json";
    final String FLAT_2 = "KDS_Laborauftrag_2.flat.json";
    final String CONTEXT = "laborauftrag.context.yaml";
    final String BUNDLE = "KDS_Laborauftrag_bundle.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(RESOURCES_ROOT + CONTEXT);
        repo.initRepository(context, getClass().getResource(RESOURCES_ROOT).getFile());
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(RESOURCES_ROOT + OPT));
        operationaltemplate = getOperationalTemplate();
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void kdsServiceRequest_toFhir_toOpenEhr() throws IOException {

        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT_2), webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<ServiceRequest> requests = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof ServiceRequest)
                .map(en -> (ServiceRequest) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, requests.size());
        final ServiceRequest serviceRequest = requests.get(0);
        Assert.assertEquals("42", serviceRequest.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=https://www.medizininformatik-initiative.de/fhir/core/modul-labor/ValueSet/ValueSetOrderCodes' available", serviceRequest.getCode().getText());

        Assert.assertEquals("Laboratory", serviceRequest.getCategoryFirstRep().getText());
        Assert.assertEquals("laboratory code", serviceRequest.getCategoryFirstRep().getCodingFirstRep().getCode());

        Assert.assertEquals("order", serviceRequest.getIntentElement().getValueAsString());
        Assert.assertEquals("SR comment", serviceRequest.getNoteFirstRep().getText());

        Assert.assertEquals("sr id", serviceRequest.getIdentifierFirstRep().getValue());
        Assert.assertEquals("dev/null", serviceRequest.getIdentifierFirstRep().getType().getCodingFirstRep().getCode());
        Assert.assertEquals("completed", serviceRequest.getStatusElement().getValueAsString());

        // Specimen
        final List<Reference> specimenReferences = serviceRequest.getSpecimen();
        Assert.assertEquals(2, specimenReferences.size());
        final List<Specimen> specimens = specimenReferences.stream().map(spec -> (Specimen) spec.getResource()).toList();

        final Specimen specimen1 = specimens.get(0);
        Assert.assertEquals("spec_id_1", specimen1.getIdentifierFirstRep().getValue());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", specimen1.getCollection().getCollectedDateTimeType().getValueAsString());
        Assert.assertEquals("TheCollector", specimen1.getCollection().getCollector().getIdentifier().getValue());

        final Specimen specimen2 = specimens.get(1);
        Assert.assertEquals("spec_id_2", specimen2.getIdentifierFirstRep().getValue());
        Assert.assertEquals("2023-02-03T04:05:06+01:00", specimen2.getCollection().getCollectedDateTimeType().getValueAsString());
        Assert.assertEquals("TheCollector2", specimen2.getCollection().getCollector().getIdentifier().getValue());


        // Requester
        final Organization org = (Organization) serviceRequest.getRequester().getResource();
        Assert.assertEquals("Einsender name", org.getName());
        Assert.assertEquals("Einsender id", org.getIdentifierFirstRep().getValue());


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
                    toRunMappingOn.addEntry(new Bundle.BundleEntryComponent().setFullUrl(reqId).setResource((Resource) requester));
                }
            }
            toRunMappingOn.addEntry(bundleEntryComponent);
        }

        final JsonObject jsonObject2 = fhirToOpenEhr.fhirToFlatJsonObject(context, toRunMappingOn, operationaltemplate);


        final JsonObject expected = new Gson().fromJson(IOUtils.toString(getClass().getResourceAsStream("/kds/laborauftrag/Laborauftrag_expected-jsonobject-from-flat.json")),
                JsonObject.class);


        compareJsonObjects(jsonObject2, expected);
        compareJsonObjects(expected, jsonObject2);

        // do this just to assert all flat paths are legit
        new FlatJsonUnmarshaller().unmarshal(new Gson().toJson(jsonObject2), webTemplate);

    }

    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("123456-0_KH", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/receiver_order_identifier/text_value").getAsString());
        Assert.assertEquals("completed", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/request_status|code").getAsString());
        Assert.assertEquals("2345-7", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/name_der_laborleistung|code").getAsString());
        Assert.assertEquals("http://loinc.org", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/name_der_laborleistung|terminology").getAsString());
        Assert.assertEquals("Blood Glucose Test", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/name_der_laborleistung|value").getAsString());
        Assert.assertEquals("laboratory", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/art_der_laborleistung_kategorie|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/ValueSet/observation-category", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/art_der_laborleistung_kategorie|terminology").getAsString());
        Assert.assertEquals("order", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/intent|code").getAsString());
        Assert.assertEquals("SP-987654", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/specimen:0/laboratory_specimen_identifier|id").getAsString());
        Assert.assertEquals("2024-08-24T11:00:00", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/current_activity/specimen:0/collection_date_time/date_time_value").getAsString());
        Assert.assertEquals("Example Hospital", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/einsender/name").getAsString());
        Assert.assertEquals("ORG-001", jsonObject.getAsJsonPrimitive("request_for_service/laborleistung/einsender/identifier|id").getAsString());

        return jsonObject;
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allServiceRequests = bundle.getEntry().stream().filter(en -> en.getResource() instanceof ServiceRequest).collect(Collectors.toList());
        assertEquals(1, allServiceRequests.size());

        final ServiceRequest serviceRequest = (ServiceRequest) allServiceRequests.get(0).getResource();

        //  - name: "identifier"
        Assert.assertEquals("Medical record identifier", serviceRequest.getIdentifierFirstRep().getValue());

        //  - name: "status"
        Assert.assertEquals("completed", serviceRequest.getStatusElement().getValueAsString());

        //  - name: "code"
        Assert.assertEquals("2345-7", serviceRequest.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("Blood Glucose Test", serviceRequest.getCode().getText());

        //  - name: "category"
        Assert.assertEquals("Laboratory", serviceRequest.getCategoryFirstRep().getText());
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
        final List<Specimen> specimens = specimenReferences.stream().map(spec -> (Specimen) spec.getResource()).toList();

        //  - name: "specimen identifier"
        //  - name: "specimen collection date time"
        //  - name: "specimen collector"
        final Specimen specimen1 = specimens.get(0);
        Assert.assertEquals("SP-987654", specimen1.getIdentifierFirstRep().getValue());
        Assert.assertEquals("2024-08-24T09:00:00+02:00", specimen1.getCollection().getCollectedDateTimeType().getValueAsString());
        Assert.assertEquals("Dr. John Doe1", specimen1.getCollection().getCollector().getIdentifier().getValue());

        final Specimen specimen2 = specimens.get(1);
        Assert.assertEquals("1_SP-987654", specimen2.getIdentifierFirstRep().getValue());
        Assert.assertEquals("2025-08-24T09:00:00+02:00", specimen2.getCollection().getCollectedDateTimeType().getValueAsString());
        Assert.assertEquals("Dr. John Doe2", specimen2.getCollection().getCollector().getIdentifier().getValue());

    }

}
