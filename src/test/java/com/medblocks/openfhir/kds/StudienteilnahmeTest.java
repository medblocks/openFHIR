package com.medblocks.openfhir.kds;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class StudienteilnahmeTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/studienteilnahme/";
    final String OPT = "Studienteilnahme.opt";
    final String FLAT = "studienteilnahme.flat.json";
    final String CONTEXT = "studienteilnahme.context.yaml";
    final String BUNDLE = "studienteilnahme_bundle.json";

    @Override
    protected void prepareState() {
        context = getContext(RESOURCES_ROOT + CONTEXT);
        repo.initRepository(context, getClass().getResource(RESOURCES_ROOT).getFile());
        operationaltemplate = getOperationalTemplate(RESOURCES_ROOT + OPT);
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(RESOURCES_ROOT + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);
        final List<Bundle.BundleEntryComponent> allConsents = bundle.getEntry().stream().filter(en -> en.getResource() instanceof Consent).collect(Collectors.toList());
        assertEquals(1, allConsents.size());

        final Consent consent = (Consent) allConsents.get(0).getResource();

        //  - name: "category"
        Assert.assertEquals("category coding code", consent.getCategoryFirstRep().getCodingFirstRep().getCode());

        //  - name: "period"
        final DateTimeType periodStart = consent.getProvision().getPeriod().getStartElement();
        final DateTimeType periodEnd = consent.getProvision().getPeriod().getEndElement();
        Assert.assertEquals("2020-02-03T04:05:06+01:00", periodStart.getValueAsString());
        Assert.assertEquals("2024-02-03T04:05:06+01:00", periodEnd.getValueAsString());
    }



    @Test
    public void toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);
        Assert.assertEquals("57016-8", jsonObject.getAsJsonPrimitive("studienteilnahme/einwilligungserklärung/studie_prüfung/studientyp").getAsString());
        Assert.assertEquals("2023-07-22T10:30:00", jsonObject.getAsJsonPrimitive("studienteilnahme/einwilligungserklärung/studienteilnahme/beginn_der_teilnahme").getAsString());
        Assert.assertEquals("2024-08-22T10:30:00", jsonObject.getAsJsonPrimitive("studienteilnahme/einwilligungserklärung/studienteilnahme/ende_der_teilnahme").getAsString());}
}
