package com.medblocks.openfhir.rso;

import ca.uhn.fhir.context.FhirContext;
import com.medblocks.openfhir.GenericTest;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datastructures.Cluster;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.encapsulated.DvMultimedia;
import com.nedap.archie.rm.generic.PartyIdentified;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Test;

public class RsoPocBidirectionalTest extends GenericTest {

    final String MODEL_MAPPINGS = "/rso_poc_acp/";
    final String CONTEXT_MAPPING = "/rso_poc_acp/acp-poc.context.yml";
    final String HELPER_LOCATION = "/rso_poc_acp/";
    final String OPT = "ACP_POC.opt";


    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toOpenEhrToFhir() {
        final Bundle testBundle = testAcp();
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context, testBundle, operationaltemplate);

        final String typeOfDirectiveCodePath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/data[at0001]/items[at0005]/value";
        final String statusPath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/data[at0001]/items[at0004]/value";
        final String commentPath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/data[at0001]/items[at0038]/value";
        final String mediaNamePath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/protocol[at0010]/items[openEHR-EHR-CLUSTER.media_file.v1]/items[at0002]/value";
        final String mediaPath = "/content[openEHR-EHR-EVALUATION.advance_care_directive.v2]/protocol[at0010]/items[openEHR-EHR-CLUSTER.media_file.v1]";
        final String treatmentCodePath = "/content[openEHR-EHR-EVALUATION.advance_intervention_decisions.v1]/data[at0001]/items[at0014]/items[at0015]/value";
        final String decisionCodePath = "/content[openEHR-EHR-EVALUATION.advance_intervention_decisions.v1, 'Advance intervention decisions']/data[at0001]/items[at0014]/items[at0034]/value";
        final String commentInterventionPath = "/content[openEHR-EHR-EVALUATION.advance_intervention_decisions.v1]/data[at0001]/items[at0014]/items[at0040]/value/value";

        Assert.assertEquals("NR", ((DvCodedText) composition.itemAtPath(typeOfDirectiveCodePath)).getDefiningCode()
                .getCodeString());
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4.14.1",
                            ((DvCodedText) composition.itemAtPath(typeOfDirectiveCodePath)).getDefiningCode()
                                    .getTerminologyId().getValue());
        Assert.assertEquals("active", ((DvText) composition.itemAtPath(statusPath)).getValue());
        Assert.assertEquals("Comment of this thing which is nice",
                            ((DvText) composition.itemAtPath(commentPath)).getValue());
        final Element mediaElement = (Element) ((Cluster) composition.itemAtPath(mediaPath)).getItems().get(0);
        final DvMultimedia multimedia = (DvMultimedia) mediaElement.getValue();
        Assert.assertTrue(new String(multimedia.getData()).startsWith("JVBER"));
        Assert.assertEquals("application/pdf", multimedia.getMediaType().getCodeString());
        Assert.assertEquals("Voorbeeld voorpagina wilsverklaringen - PDF.pdf",
                            composition.itemAtPath(mediaNamePath + "/value"));
        Assert.assertEquals("Cardiopulmonary resuscitation (procedure)",
                            ((DvCodedText) composition.itemAtPath(treatmentCodePath)).getValue());
        Assert.assertEquals("89666000", ((DvCodedText) composition.itemAtPath(treatmentCodePath)).getDefiningCode()
                .getCodeString());
        Assert.assertEquals("http://snomed.info/sct",
                            ((DvCodedText) composition.itemAtPath(treatmentCodePath)).getDefiningCode()
                                    .getTerminologyId().getValue());
        Assert.assertEquals("Comment of this treatment directive", composition.itemAtPath(commentInterventionPath));
        Assert.assertEquals("Yes, but", ((DvCodedText) composition.itemAtPath(decisionCodePath)).getValue());
        Assert.assertEquals("JA_MAAR",
                            ((DvCodedText) composition.itemAtPath(decisionCodePath)).getDefiningCode().getCodeString());
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4",
                            ((DvCodedText) composition.itemAtPath(decisionCodePath)).getDefiningCode()
                                    .getTerminologyId().getValue());

        Assert.assertEquals("en", composition.getLanguage().getCodeString());
        Assert.assertEquals("ISO_639-1", composition.getLanguage().getTerminologyId().getValue());
        Assert.assertEquals("ISO_3166-1", composition.getTerritory().getTerminologyId().getValue());
        Assert.assertEquals("DE", composition.getTerritory().getCodeString());
        Assert.assertEquals("Max Muuster", ((PartyIdentified) composition.getComposer()).getName());


        // now back to fhir
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        Assert.assertEquals(2, bundle.getEntry().size());

        final List<Consent> treatmentDirectives = bundle.getEntry().stream()
                .map(en -> ((Consent) en.getResource()))
                .filter(en -> en.getMeta().getProfile().stream().anyMatch(
                        prof -> "http://nictiz.nl/fhir/StructureDefinition/zib-TreatmentDirective".equals(
                                prof.getValue())))
                .collect(Collectors.toList());

        final List<Consent> advanceDirectives = bundle.getEntry().stream()
                .map(en -> ((Consent) en.getResource()))
                .filter(en -> en.getMeta().getProfile().stream().anyMatch(
                        prof -> "http://nictiz.nl/fhir/StructureDefinition/zib-AdvanceDirective".equals(
                                prof.getValue())))
                .collect(Collectors.toList());

        Assert.assertEquals(1, treatmentDirectives.size());
        Assert.assertEquals(1, advanceDirectives.size());

        // assert treatment directives
        final Consent treatment = treatmentDirectives.get(0);
        Assert.assertEquals("JA_MAAR",
                            ((CodeableConcept) treatment.getModifierExtension().get(0).getValue()).getCodingFirstRep()
                                    .getCode());
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4",
                            ((CodeableConcept) treatment.getModifierExtension().get(0).getValue()).getCodingFirstRep()
                                    .getSystem());

        Assert.assertEquals("89666000", (treatment.getExtension().stream()
                .filter(ext -> ext.getUrl()
                        .equals("http://nictiz.nl/fhir/StructureDefinition/zib-TreatmentDirective-Treatment"))
                .map(ext -> ((CodeableConcept) ext.getValue()))
                .findAny().orElse(null).getCodingFirstRep().getCode()));

        Assert.assertEquals("http://snomed.info/sct", (treatment.getExtension().stream()
                .filter(ext -> ext.getUrl()
                        .equals("http://nictiz.nl/fhir/StructureDefinition/zib-TreatmentDirective-Treatment"))
                .map(ext -> ((CodeableConcept) ext.getValue()))
                .findAny().orElse(null).getCodingFirstRep().getSystem()));

        Assert.assertEquals("Comment of this treatment directive", (treatment.getExtension().stream()
                .filter(ext -> ext.getUrl().equals("http://nictiz.nl/fhir/StructureDefinition/Comment"))
                .map(ext -> ((StringType) ext.getValue()))
                .findAny().orElse(null).getValue()));


        // assert advance directives
        final Consent advanced = advanceDirectives.get(0);
        Assert.assertEquals("urn:oid:2.16.840.1.113883.2.4.3.11.60.40.4.14.1",
                            advanced.getCategoryFirstRep().getCodingFirstRep().getSystem());
        Assert.assertEquals("NR", advanced.getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("active", advanced.getStatusElement().getValueAsString());

        Assert.assertEquals("Comment of this thing which is nice", (advanced.getExtension().stream()
                .filter(ext -> ext.getUrl().equals("http://nictiz.nl/fhir/StructureDefinition/Comment"))
                .map(ext -> ((StringType) ext.getValue()))
                .findAny().orElse(null).getValue()));

        // media now :o
        final Attachment sourceAttachment = advanced.getSourceAttachment();
        Assert.assertEquals("Voorbeeld voorpagina wilsverklaringen - PDF.pdf", sourceAttachment.getTitle());
        Assert.assertEquals("application/pdf", sourceAttachment.getContentType());
        Assert.assertTrue(new String(sourceAttachment.getData()).startsWith("JVBER")); // todo
    }

    public Bundle testAcp() {
        final Bundle bundle = new Bundle();
        final Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
        final Consent consent = FhirContext.forR4().newXmlParser()
                .parseResource(Consent.class,
                               getClass().getResourceAsStream(HELPER_LOCATION + "zib-AdvanceDirective - example.xml"));
        consent.setPatient(new Reference().setDisplay("Max Muuster"));
        entry.setResource(consent);
        bundle.addEntry(entry);

        final Bundle.BundleEntryComponent entry2 = new Bundle.BundleEntryComponent();
        entry2.setResource(FhirContext.forR4().newXmlParser()
                                   .parseResource(Consent.class, getClass().getResourceAsStream(
                                           HELPER_LOCATION + "zib-TreatmentDirective - example.xml")));
        bundle.addEntry(entry2);
        return bundle;
    }


}
