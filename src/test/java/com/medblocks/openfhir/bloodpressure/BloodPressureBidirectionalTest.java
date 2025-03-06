package com.medblocks.openfhir.bloodpressure;

import com.medblocks.openfhir.GenericTest;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import java.io.IOException;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.junit.Test;

public class BloodPressureBidirectionalTest extends GenericTest {

    final String MODEL_MAPPINGS = "/blood_pressure/";
    final String CONTEXT_MAPPING = "/blood_pressure/simple-blood-pressure.context.yml";
    final String HELPER_LOCATION = "/blood_pressure/";
    final String OPT = "Blood Pressure.opt";
    final String FLAT = "blood-pressure_flat.json";


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
    public void bloodPressureToFhirToOpenEhr() throws IOException {
        // have a composition from config
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             webTemplate);

        // transform it to FHIR
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        BloodPressureToFhirTest.assertBloodPressureFhir(
                bundle); // this is being tested elsewhere but whatever.., why not

        // transform it back to openEHR
        final Composition rmComposition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationaltemplate);
        final String systolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value";
        final String diastolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0005]/value";

        final String interpretationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at1059]";
        final String descriptionPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0033]";
        final String locationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/protocol[at0011]/items[at0014]";

        final List<Object> objects = composition.itemsAtPath(systolicPath);
        if (objects.isEmpty()) {
            Assert.fail();
        }
        for (Object systolicValues : objects) {
            final Double systolicMagnitude = ((DvQuantity) systolicValues).getMagnitude();
            Assert.assertTrue(rmComposition.itemsAtPath(systolicPath).stream()
                                      .anyMatch(item -> ((DvQuantity) item).getMagnitude().equals(systolicMagnitude)));
        }
        final List<Object> objects1 = composition.itemsAtPath(diastolicPath);
        if (objects1.isEmpty()) {
            Assert.fail();
        }
        for (Object diastolicValues : objects1) {
            final Double systolicMagnitude = ((DvQuantity) diastolicValues).getMagnitude();
            Assert.assertTrue(rmComposition.itemsAtPath(diastolicPath).stream()
                                      .anyMatch(item -> ((DvQuantity) item).getMagnitude().equals(systolicMagnitude)));
        }
        final List<Object> objects2 = composition.itemsAtPath(interpretationPath);
        if (objects2.isEmpty()) {
            Assert.fail();
        }
        for (Object interpretationValues : objects2) {
            final String interpretationValue = ((DvText) ((Element) interpretationValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(interpretationPath).stream().anyMatch(
                    item -> ((DvText) ((Element) item).getValue()).getValue().equals(interpretationValue)));
        }
        final List<Object> objects3 = composition.itemsAtPath(descriptionPath);
        if (objects3.isEmpty()) {
            Assert.fail();
        }
        for (Object descriptionValues : objects3) {
            final String descriptionValue = ((DvText) ((Element) descriptionValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(descriptionPath).stream().anyMatch(
                    item -> ((DvText) ((Element) item).getValue()).getValue().equals(descriptionValue)));
        }
        final List<Object> objects4 = composition.itemsAtPath(locationPath);
        if (objects4.isEmpty()) {
            Assert.fail();
        }
        for (Object locationValues : objects4) {
            final String locationValue = ((DvText) ((Element) locationValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(locationPath).stream().anyMatch(
                    item -> ((DvText) ((Element) item).getValue()).getValue().equals(locationValue)));
        }

    }

    @Test
    public void testLocationMappingBidirectional() throws IOException {
        // Create a flat JSON with location_of_measurement mapping
        String flatJson = "{\n" +
                "  \"blood_pressure/blood_pressure/location_of_measurement|code\": \"at0025\",\n" +
                "  \"blood_pressure/blood_pressure/location_of_measurement|value\": \"Right arm\",\n" +
                "  \"blood_pressure/blood_pressure/location_of_measurement|terminology\": \"local\",\n" +
                "  \"blood_pressure/blood_pressure/location_of_measurement/_mapping:0/match\": \"=\",\n" +
                "  \"blood_pressure/blood_pressure/location_of_measurement/_mapping:0/target|preferred_term\": \"Left arm\",\n" +
                "  \"blood_pressure/blood_pressure/location_of_measurement/_mapping:0/target|code\": \"at0026\",\n" +
                "  \"blood_pressure/blood_pressure/location_of_measurement/_mapping:0/target|terminology\": \"local\",\n" +
                "  \"blood_pressure/blood_pressure/any_event:0/systolic|magnitude\": \"120.0\",\n" +
                "  \"blood_pressure/blood_pressure/any_event:0/systolic|unit\": \"mm[Hg]\",\n" +
                "  \"blood_pressure/blood_pressure/any_event:0/diastolic|magnitude\": \"80.0\",\n" +
                "  \"blood_pressure/blood_pressure/any_event:0/diastolic|unit\": \"mm[Hg]\"\n" +
                "}";
        
        // Parse the flat JSON to a Composition
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(
                IOUtils.toInputStream(flatJson, "UTF-8"),
                webTemplate);
        
        // Transform to FHIR
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, composition, operationaltemplate);
        
        // Transform back to OpenEHR
        final Composition rmComposition = fhirToOpenEhr.fhirToCompositionRm(context, bundle, operationaltemplate);
        
        // Path to location_of_measurement
        final String locationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/protocol[at0011]/items[at0014]";
        
        // Verify the location value is preserved
        final List<Object> locationObjects = composition.itemsAtPath(locationPath);
        if (locationObjects.isEmpty()) {
            Assert.fail("No location objects found in original composition");
        }
        
        for (Object locationValue : locationObjects) {
            final String code = ((DvCodedText) ((Element) locationValue).getValue()).getDefiningCode().getCodeString();
            final String terminology = ((DvCodedText) ((Element) locationValue).getValue()).getDefiningCode().getTerminologyId().getValue();
            final String value = ((DvCodedText) ((Element) locationValue).getValue()).getValue();
            
            // Verify the same values exist in the round-tripped composition
            Assert.assertTrue(rmComposition.itemsAtPath(locationPath).stream()
                    .anyMatch(item -> {
                        DvCodedText dvText = (DvCodedText) ((Element) item).getValue();
                        return dvText.getDefiningCode().getCodeString().equals(code) &&
                               dvText.getDefiningCode().getTerminologyId().getValue().equals(terminology) &&
                               dvText.getValue().equals(value);
                    }));
        }
    }

}
