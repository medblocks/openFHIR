package com.medblocks.openfhir.kds;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.Assert;
import org.junit.Test;

public class StudienteilnahmeTest extends KdsBidirectionalTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT = "/kds_new/projects/org.highmed/KDS/studienteilnahme/studienteilnahme.context.yaml";
    final String HELPER_LOCATION = "/kds/studienteilnahme/";
    final String OPT = "Studienteilnahme.opt";
    final String FLAT = "studienteilnahme.flat.json";
    final String BUNDLE = "studienteilnahme_bundle.json";

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
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                                     new OPTParser(
                                                                                             operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allConsents = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Consent).collect(Collectors.toList());
        assertEquals(1, allConsents.size());

        final Consent consent = (Consent) allConsents.get(0).getResource();

        //  - name: "period"
        final DateTimeType periodStart = consent.getProvision().getPeriod().getStartElement();
        final DateTimeType periodEnd = consent.getProvision().getPeriod().getEndElement();
        Assert.assertEquals("2020-02-03T04:05:06+01:00", periodStart.getValueAsString());
        Assert.assertEquals("2024-02-03T04:05:06+01:00", periodEnd.getValueAsString());
    }


    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);
        Assert.assertEquals("2024-08-22T10:30:00", jsonObject.getAsJsonPrimitive(
                "studienteilnahme/context/start_time").getAsString());
        Assert.assertEquals("2023-07-22T10:30:00", jsonObject.getAsJsonPrimitive(
                "studienteilnahme/einwilligungserklärung/studienteilnahme/beginn_der_teilnahme").getAsString());
        Assert.assertEquals("2024-08-22T10:30:00", jsonObject.getAsJsonPrimitive(
                "studienteilnahme/einwilligungserklärung/studienteilnahme/ende_der_teilnahme").getAsString());
        return jsonObject;
    }
}
