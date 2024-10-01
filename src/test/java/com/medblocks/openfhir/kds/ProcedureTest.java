package com.medblocks.openfhir.kds;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Procedure;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class ProcedureTest extends KdsBidirectionalTest {

    final String RESOURCES_ROOT = "/kds/procedure/";
    final String OPT = "KDS_Prozedur.opt";
    final String FLAT = "KDS_Prozedur.flat.json";
    final String CONTEXT = "procedure.context.yaml";
    final String BUNDLE = "KDS_Prozedur_bundle.json";

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
        final List<Bundle.BundleEntryComponent> allProcedures = bundle.getEntry().stream().filter(en -> en.getResource() instanceof Procedure).collect(Collectors.toList());
        Assert.assertEquals(1, allProcedures.size());

        final Procedure theProcedure = (Procedure) allProcedures.get(0).getResource();

        // - name: "case identification"
        Assert.assertEquals("encounter-id-1245", theProcedure.getId());

//        - name: "ISM Transition"
        Assert.assertEquals("532", theProcedure.getStatusElement().getValueAsString());

//        - name: "Name"
        Assert.assertEquals("80146002", theProcedure.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/ops", theProcedure.getCode().getCodingFirstRep().getSystem());

        // - name: "seitenlokalisation"
        final List<Extension> codeExtensions = theProcedure.getCode().getCodingFirstRep().getExtension();
        Assert.assertEquals(1, codeExtensions.size());
        final Extension extension = codeExtensions
                .stream().filter(ex -> "http://fhir.de/StructureDefinition/seitenlokalisation".equals(ex.getUrl()))
                .findAny().orElse(null);
        Assert.assertEquals("B", ((Coding) codeExtensions.get(0).getValue()).getCode());
        Assert.assertEquals("//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_SEITENLOKALISATION", ((Coding) codeExtensions.get(0).getValue()).getSystem());

//        - name: "Kategorie"
        Assert.assertEquals("Diagnostic procedure", theProcedure.getCategory().getText());
        Assert.assertEquals("103693007", theProcedure.getCategory().getCodingFirstRep().getCode());

//        - name: "Body site"
        Assert.assertEquals("Abdomen", theProcedure.getBodySite().get(0).getText());
        Assert.assertEquals("818981001", theProcedure.getBodySite().get(0).getCodingFirstRep().getCode());

//        - name: "Durchführungsabsicht" todo:
        Assert.assertNotNull(theProcedure.getExtensionByUrl("durchfuehrungsabsicht"));

//        - name: "Comment"
        Assert.assertEquals("Procedure completed successfully with no complications.", theProcedure.getNoteFirstRep().getText());

//        - name: "time"
        Assert.assertEquals("2022-02-03T04:05:06+01:00", theProcedure.getPerformedDateTimeType().getValueAsString());
    }

    @Test
    public void toOpenEhr() {
        final Bundle testBundle = getTestBundle(RESOURCES_ROOT + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        // - name: "case identification"
        Assert.assertEquals("example-procedure", jsonObject.getAsJsonPrimitive("kds_prozedur/context/case_identification/case_identifier").getAsString());

//        - name: "ISM Transition"
        Assert.assertEquals("completed", jsonObject.getAsJsonPrimitive("kds_prozedur/procedure/ism_transition/current_state|code").getAsString());

//        - name: "Name"
        Assert.assertEquals("5-470", jsonObject.getAsJsonPrimitive("kds_prozedur/procedure/name_der_prozedur|code").getAsString());

        // - name: "seitenlokalisation"
        Assert.assertEquals("B", jsonObject.getAsJsonPrimitive("kds_prozedur/procedure/seitenlokalisation:0|code").getAsString());

//        - name: "Kategorie"
        Assert.assertEquals("103693007", jsonObject.getAsJsonPrimitive("kds_prozedur/procedure/kategorie_der_prozedur|code").getAsString());

//        - name: "Body site"
        Assert.assertEquals("818981001", jsonObject.getAsJsonPrimitive("kds_prozedur/procedure/körperstelle:0|code").getAsString());

//        - name: "Durchführungsabsicht" todo:
//        Assert.assertNotNull(theProcedure.getExtensionByUrl("durchfuehrungsabsicht"));

//        - name: "Comment"
        Assert.assertEquals("Procedure completed successfully with no complications.", jsonObject.getAsJsonPrimitive("kds_prozedur/procedure/comment").getAsString());

//        - name: "time"
        Assert.assertEquals("2024-08-20T16:00:00", jsonObject.getAsJsonPrimitive("kds_prozedur/procedure/time").getAsString());
    }
}
