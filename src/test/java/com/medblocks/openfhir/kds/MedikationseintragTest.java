package com.medblocks.openfhir.kds;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;

public class MedikationseintragTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/medikationseintrag/";
    final String OPT = "KDS_Medikationseintrag_v1.opt";
    final String FLAT = "KDSMedicationRequest.flat.json";
    final String CONTEXT = "medikation.context.yaml";
    final String BUNDLE = "KDS_Medikationseintrag_v1-Fhir-Bundle-input.json";

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
        final Bundle testBundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, getClass().getResourceAsStream(RESOURCES_ROOT + BUNDLE));

        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("Take 1 tablet every 6 hours as needed for pain", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung2:0/dosierung_freitext").getAsString());
        Assert.assertEquals("500.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung2:0/dosis/quantity_value|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung2:0/dosis/quantity_value|unit").getAsString());
        Assert.assertEquals("Take 1 tablet every 6 hours as needed for pain", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/dosierung:0").getAsString());
        Assert.assertEquals("Paracetamol 500mg tablet", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/arzneimittel-name").getAsString());
        Assert.assertEquals("385055001", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/darreichungsform|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/darreichungsform|terminology").getAsString());
        Assert.assertEquals("Paracetamol", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("500.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/zähler|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/zähler|unit").getAsString());
        Assert.assertEquals("1.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/nenner|magnitude").getAsString());
        Assert.assertEquals("tablet", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:0/bestandteil-menge/nenner|unit").getAsString());
        Assert.assertEquals("11Paracetamol", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("1500.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/zähler|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/zähler|unit").getAsString());
        Assert.assertEquals("11.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/nenner|magnitude").getAsString());
        Assert.assertEquals("tablet", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:1/bestandteil-menge/nenner|unit").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:2/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:0/arzneimittel/bestandteil:2/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("Take 1 capsule daily", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung2:0/dosierung_freitext").getAsString());
        Assert.assertEquals("5.0", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung2:0/dosis/quantity_value|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung2:0/dosis/quantity_value|unit").getAsString());
        Assert.assertEquals("Take 1 capsule daily", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/dosierung:0").getAsString());
        Assert.assertEquals("Ramipril 5mg capsule", jsonObject.getAsJsonPrimitive("medikamentenliste_v1/medikationseintrag:1/arzneimittel/arzneimittel-name").getAsString());

        return jsonObject;
    }
}
