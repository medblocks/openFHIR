package com.medblocks.openfhir.bloodpressure;

import com.google.gson.JsonObject;
import com.medblocks.openfhir.GenericTest;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datavalues.DvCodedText;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Test;

public class BloodPressureToOpenEhrTest extends GenericTest {

    final String MODEL_MAPPINGS = "/blood_pressure/";
    final String CONTEXT_MAPPING = "/blood_pressure/simple-blood-pressure.context.yml";
    final String HELPER_LOCATION = "/blood_pressure/";
    final String OPT = "Blood Pressure.opt";


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
    public void testBloodPressure_flat() {

        final JsonObject flat = fhirToOpenEhr.fhirToFlatJsonObject(context, testBloodPressureObservation(),
                                                                   operationaltemplate);

        Assert.assertEquals(11, flat.size());
        Assert.assertEquals("456.0",
                            flat.get("blood_pressure/blood_pressure/any_event:0/systolic|magnitude").getAsString());
        Assert.assertEquals("mm[Hg]",
                            flat.get("blood_pressure/blood_pressure/any_event:0/systolic|unit").getAsString());
        Assert.assertEquals("789.0",
                            flat.get("blood_pressure/blood_pressure/any_event:0/diastolic|magnitude").getAsString());
        Assert.assertEquals("mm[Hg2]",
                            flat.get("blood_pressure/blood_pressure/any_event:0/diastolic|unit").getAsString());
        Assert.assertEquals("at00256",
                            flat.get("blood_pressure/blood_pressure/location_of_measurement|code").getAsString());
        Assert.assertEquals("remotey", flat.get("blood_pressure/blood_pressure/location_of_measurement|terminology")
                .getAsString());
        Assert.assertEquals("description", flat.get("blood_pressure/blood_pressure/any_event:0/comment").getAsString());
        Assert.assertEquals("interpretation text",
                            flat.get("blood_pressure/blood_pressure/any_event:0/clinical_interpretation")
                                    .getAsString());

        // assert hardcoded paths
        Assert.assertEquals("at1000",
                            flat.get("blood_pressure/blood_pressure/a24_hour_average/position|code").getAsString());
        Assert.assertEquals("confounding factor",
                            flat.get("blood_pressure/blood_pressure/a24_hour_average/confounding_factors")
                                    .getAsString());
    }

    @Test
    public void testBloodPressure_RM() {
        final Composition composition = fhirToOpenEhr.fhirToCompositionRm(context,
                                                                          testBloodPressureObservation(),
                                                                          operationaltemplate);
        final String systolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value";
        final String diastolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0005]/value";

        final String interpretationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at1059]";
        final String descriptionPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0033]";
        final String locationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/protocol[at0011]/items[at0014]";

        Assert.assertEquals(Double.valueOf(456), ((DvQuantity) composition.itemAtPath(systolicPath)).getMagnitude());
        Assert.assertEquals("mm[Hg]", ((DvQuantity) composition.itemAtPath(systolicPath)).getUnits());
        Assert.assertEquals(Double.valueOf(789), ((DvQuantity) composition.itemAtPath(diastolicPath)).getMagnitude());
        Assert.assertEquals("mm[Hg2]", ((DvQuantity) composition.itemAtPath(diastolicPath)).getUnits());
        Assert.assertEquals("at00256", ((DvCodedText) ((Element) composition.itemAtPath(
                locationPath)).getValue()).getDefiningCode().getCodeString());
        Assert.assertEquals("remotey", ((DvCodedText) ((Element) composition.itemAtPath(
                locationPath)).getValue()).getDefiningCode().getTerminologyId().getValue());
        Assert.assertEquals("interpretation text",
                            ((DvText) ((Element) composition.itemAtPath(interpretationPath)).getValue()).getValue());
        Assert.assertEquals("description",
                            ((DvText) ((Element) composition.itemAtPath(descriptionPath)).getValue()).getValue());
    }

    @Test
    public void testLocationMappingToOpenEhr() {
        // Create a FHIR Observation with multiple bodySite codings
        org.hl7.fhir.r4.model.Observation observation = new org.hl7.fhir.r4.model.Observation();
        observation.setSubject(new Reference("Patient/123"));
        
        // Add systolic component
        org.hl7.fhir.r4.model.Observation.ObservationComponentComponent systolic = 
                new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        systolic.setValue(new Quantity(120).setUnit("mm[Hg]"));
        systolic.setCode(new CodeableConcept().addCoding(new Coding("loinc", "8480-6", null)));
        observation.addComponent(systolic);
        
        // Add diastolic component
        org.hl7.fhir.r4.model.Observation.ObservationComponentComponent diastolic = 
                new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        diastolic.setValue(new Quantity(80).setUnit("mm[Hg]"));
        diastolic.setCode(new CodeableConcept().addCoding(new Coding("loinc", "8462-4", null)));
        observation.addComponent(diastolic);
        
        // Set bodySite with multiple codings
        CodeableConcept bodySite = new CodeableConcept();
        bodySite.addCoding(new Coding("local", "at0025", "Right arm"));
        bodySite.addCoding(new Coding("local", "at0026", "Left arm"));
        bodySite.setText("Multiple measurement locations");
        observation.setBodySite(bodySite);
        
        // Convert to flat JSON
        JsonObject flat = fhirToOpenEhr.fhirToFlatJsonObject(context, observation, operationaltemplate);
        
        // Verify the location_of_measurement fields
        Assert.assertEquals("at0025", flat.get("blood_pressure/blood_pressure/location_of_measurement|code").getAsString());
        Assert.assertEquals("local", flat.get("blood_pressure/blood_pressure/location_of_measurement|terminology").getAsString());
        Assert.assertEquals("Right arm", flat.get("blood_pressure/blood_pressure/location_of_measurement|value").getAsString());
        
        // Verify the mapping fields
        Assert.assertEquals("=", flat.get("blood_pressure/blood_pressure/location_of_measurement/_mapping:0/match").getAsString());
        Assert.assertEquals("at0026", flat.get("blood_pressure/blood_pressure/location_of_measurement/_mapping:0/target|code").getAsString());
        Assert.assertEquals("local", flat.get("blood_pressure/blood_pressure/location_of_measurement/_mapping:0/target|terminology").getAsString());
        Assert.assertEquals("Left arm", flat.get("blood_pressure/blood_pressure/location_of_measurement/_mapping:0/target|preferred_term").getAsString());
    }

    public static org.hl7.fhir.r4.model.Observation testBloodPressureObservation() {
        final org.hl7.fhir.r4.model.Observation resource = new org.hl7.fhir.r4.model.Observation();
        resource.setCode(new CodeableConcept().setText("description"));
        resource.setSubject(new Reference("Patient/123"));

        final org.hl7.fhir.r4.model.Observation.ObservationComponentComponent component1 = new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        component1.setValue(new Quantity(123));
        resource.addComponent(component1);

        final org.hl7.fhir.r4.model.Observation.ObservationComponentComponent component2 = new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        component2.setValue(new Quantity(456).setUnit("mm[Hg]"));
        resource.addComponent(component2);
        component2.setCode(new CodeableConcept().addCoding(new Coding("loinc", "8480-6", null)));

        final org.hl7.fhir.r4.model.Observation.ObservationComponentComponent component3 = new org.hl7.fhir.r4.model.Observation.ObservationComponentComponent();
        component3.setValue(new Quantity(789).setUnit("mm[Hg2]"));
        component3.setCode(new CodeableConcept().addCoding(new Coding("loinc", "8462-4", null)));

        resource.addComponent(component3);

        resource.addComponent(new Observation.ObservationComponentComponent().addInterpretation(
                new CodeableConcept().setText("interpretation text")));

        resource.setBodySite(
                new CodeableConcept(new Coding("remotey", "at00256", null)).setText("THIS IS LOCATION OF MEASUREMENT"));
        return resource;
    }

}
