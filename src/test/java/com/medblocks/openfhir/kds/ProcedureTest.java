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
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Procedure;
import org.junit.Assert;
import org.junit.Test;

public class ProcedureTest extends KdsBidirectionalTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT = "/kds_new/projects/org.highmed/KDS/procedure/procedure.context.yaml";
    final String HELPER_LOCATION = "/kds/procedure/";
    final String OPT = "KDS_Prozedur.opt";
    final String FLAT = "KDS_Prozedur.flat.json";

    final String BUNDLE = "KDS_Prozedur_bundle.json";

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
        final List<Bundle.BundleEntryComponent> allProcedures = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Procedure).collect(Collectors.toList());
        Assert.assertEquals(1, allProcedures.size());

        final Procedure theProcedure = (Procedure) allProcedures.get(0).getResource();

        // -performed
        Assert.assertEquals("2020-02-03T04:05:06+01:00",
                            theProcedure.getPerformedPeriod().getStartElement().getValueAsString());

//        - name: "ISM Transition"
//        Assert.assertEquals("530", theProcedure.getStatusElement().getValueAsString());

//        - name: "Name"
        Assert.assertEquals("80146002", theProcedure.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/ops",
                            theProcedure.getCode().getCodingFirstRep().getSystem());
        Assert.assertEquals("freitextbeschreibung", theProcedure.getCode().getText());

//        - name: "Comment"
        Assert.assertEquals("Procedure completed successfully with no complications.",
                            theProcedure.getNoteFirstRep().getText());

//        - name: "Kategorie"
        Assert.assertEquals("Diagnostic procedure", theProcedure.getCategory().getText());
        Assert.assertEquals("103693007", theProcedure.getCategory().getCodingFirstRep().getCode());

//        - name: "Body site"
        Assert.assertEquals("Abdomen", theProcedure.getBodySite().get(0).getText());
        Assert.assertEquals("818981001", theProcedure.getBodySite().get(0).getCodingFirstRep().getCode());

        // - name: "berichtId"
        Assert.assertEquals("bericht_idqa", theProcedure.getIdentifierFirstRep().getValue());

//        - name: "Durchführungsabsicht"
        final Extension durchuhrungsabsicht = theProcedure.getExtensionByUrl(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/Durchfuehrungsabsicht");
        Assert.assertNotNull(durchuhrungsabsicht);
        Assert.assertEquals("durchführungsabsicht", ((Coding) durchuhrungsabsicht.getValue()).getCode());
        Assert.assertEquals("valuedurchführungsabsicht", ((Coding) durchuhrungsabsicht.getValue()).getDisplay());

    }

    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(HELPER_LOCATION + BUNDLE);
        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);


//        Assert.assertEquals("completed",
//                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/ism_transition/current_state")
//                                    .getAsString());
        Assert.assertEquals("5-470", jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/name_der_prozedur|code")
                .getAsString());
        Assert.assertEquals("5-470", jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/name_der_prozedur|value")
                .getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/ops",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/name_der_prozedur|terminology")
                                    .getAsString());
        Assert.assertEquals("Appendectomy",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/freitextbeschreibung")
                                    .getAsString());
        Assert.assertEquals("Procedure completed successfully with no complications.",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kommentar").getAsString());
        Assert.assertEquals("103693007",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kategorie_der_prozedur|code")
                                    .getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kategorie_der_prozedur|terminology")
                                    .getAsString());
        Assert.assertEquals("Diagnostic procedure",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kategorie_der_prozedur|value")
                                    .getAsString());
        Assert.assertEquals("durchführungsabsicht",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/durchführungsabsicht|code")
                                    .getAsString());
        Assert.assertEquals("durchführungsabsicht",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/durchführungsabsicht|value")
                                    .getAsString());
        Assert.assertEquals("Durchfuehrungsabsicht",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/durchführungsabsicht|terminology")
                                    .getAsString());
        Assert.assertEquals("818981001", jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/körperstelle:0|code")
                .getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/körperstelle:0|terminology")
                                    .getAsString());
        Assert.assertEquals("Abdomen", jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/körperstelle:0|value")
                .getAsString());


        return jsonObject;
    }
}
