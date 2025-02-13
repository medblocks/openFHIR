package com.medblocks.openfhir.toopenehr;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.TestOpenFhirMappingContext;
import com.medblocks.openfhir.util.FhirConnectModelMerger;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenEhrPopulator;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FhirToOpenEhrTest {

    final FhirPathR4 fhirPathR4 = new FhirPathR4(FhirContext.forR4());
    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();
    final TestOpenFhirMappingContext repo = new TestOpenFhirMappingContext(fhirPathR4, openFhirStringUtils,
                                                                           fhirConnectModelMerger);
    FhirToOpenEhr fhirToOpenEhr;

    @Before
    public void init() {
        fhirToOpenEhr = new FhirToOpenEhr(fhirPathR4,
                                          new OpenFhirStringUtils(),
                                          new FlatJsonUnmarshaller(),
                                          new Gson(),
                                          new OpenEhrRmWorker(openFhirStringUtils, new OpenFhirMapperUtils()),
                                          openFhirStringUtils,
                                          repo,
                                          new OpenEhrCachedUtils(null),
                                          new OpenFhirMapperUtils(),
                                          new OpenEhrPopulator(new OpenFhirMapperUtils()));
    }


    @Test
    public void testFlattenning() {
        final Patient patient = new Patient();
        patient.addName(new HumanName().setUse(HumanName.NameUse.MAIDEN).setText("text0").setFamily("family0")
                                .addSuffix("sufix00").addSuffix("sufix01").addGiven("given0_0").addGiven("given0_1"));
        patient.addName(new HumanName().setUse(HumanName.NameUse.NICKNAME).setText("text1").setFamily("family1")
                                .addSuffix("sufix10").addSuffix("suffix11").addGiven("given1_0").addGiven("given1_1"));
        final String fhirToOpenEhrHelperS = "{\n" +
                "  \"fhirPath\": \"Patient.name\",\n" +
                "  \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "  \"openEhrPath\": \"person/personendaten/person/geburtsname[n]\",\n" +
                "  \"openEhrType\": \"NONE\",\n" +
                "  \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "  \"multiple\": false,\n" +
                "  \"fhirToOpenEhrHelpers\": [\n" +
                "    {\n" +
                "      \"fhirPath\": \"given\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/vollständiger_name\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"use.code\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/namensart\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"family\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/familienname-nachname\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"prefix\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/familienname-vorsatzwort\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    },\n" +
                "    {\n" +
                "      \"fhirPath\": \"suffix\",\n" +
                "      \"limitingCriteria\": \"Bundle.entry.resource.ofType(Patient)\",\n" +
                "      \"openEhrPath\": \"person/personendaten/person/geburtsname[n]/suffix[n]\",\n" +
                "      \"openEhrType\": \"DV_TEXT\",\n" +
                "      \"archetype\": \"openEHR-EHR-ADMIN_ENTRY.person_data.v0\",\n" +
                "      \"multiple\": false\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        final FhirToOpenEhrHelper helper = new Gson().fromJson(fhirToOpenEhrHelperS, FhirToOpenEhrHelper.class);
        final JsonObject flattenning = new JsonObject();
        fhirToOpenEhr.addDataPoints(helper, flattenning, patient);
        Assert.assertEquals("given0_0", flattenning.getAsJsonPrimitive(
                "person/personendaten/person/geburtsname:0/vollständiger_name").getAsString());
        Assert.assertEquals("given1_0", flattenning.getAsJsonPrimitive(
                "person/personendaten/person/geburtsname:1/vollständiger_name").getAsString());
        Assert.assertEquals("family1", flattenning.getAsJsonPrimitive(
                "person/personendaten/person/geburtsname:1/familienname-nachname").getAsString());
        Assert.assertEquals("family0", flattenning.getAsJsonPrimitive(
                "person/personendaten/person/geburtsname:0/familienname-nachname").getAsString());
        Assert.assertEquals("sufix00",
                            flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/suffix:0")
                                    .getAsString());
        Assert.assertEquals("sufix01",
                            flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:0/suffix:1")
                                    .getAsString());
        Assert.assertEquals("sufix10",
                            flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/suffix:0")
                                    .getAsString());
        Assert.assertEquals("suffix11",
                            flattenning.getAsJsonPrimitive("person/personendaten/person/geburtsname:1/suffix:1")
                                    .getAsString());
    }

    @Test
    public void testReplacePattern() {
        final String original = "kds_prozedur/procedure/seitenlokalisation[n]";
        final String newParent = "kds_prozedur/procedure/name_der_prozedur";
        final String s = openFhirStringUtils.replacePattern(original, newParent);
        Assert.assertEquals("kds_prozedur/procedure/seitenlokalisation[n]", s);

        final String original1 = "kds_prozedur/procedure[n]/seitenlokalisation[n]";
        final String newParent1 = "kds_prozedur/procedure[n]/name_der_prozedur/abc";
        final String s1 = openFhirStringUtils.replacePattern(original1, newParent1);
        Assert.assertEquals("kds_prozedur/procedure[n]/seitenlokalisation[n]", s1);
    }

    @Test
    public void childHasParentRecurring() {
        final String child = "medikamentenliste/medikationseintrag[n]/verabreichungsweg[n]";
        final String parent = "medikamentenliste/medikationseintrag:0/dosierung2[n]";
        Assert.assertTrue(openFhirStringUtils.childHasParentRecurring(child, parent));

        final String child2 = "medikamentenliste/medikationseintrag[n]/dosierung2[n]";
        final String parent2 = "medikamentenliste/medikationseintrag:0/dosierung2[n]";
        Assert.assertTrue(openFhirStringUtils.childHasParentRecurring(child2, parent2));

        final String child1 = "medikamentenliste/medikatiNOonseintrag[n]/verabreichungsweg[n]";
        final String parent1 = "medikamentenliste/medikationseintrag:0/dosierung2[n]";
        Assert.assertFalse(openFhirStringUtils.childHasParentRecurring(child1, parent1));
    }

    @Test
    public void replaceMultipleOccurrenceSyntax_singles() {
        String openEhrPath = "medication_order/medication_order/order[n]/medication_item";
        final JsonObject finalFlat = new JsonObject();
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING", Arrays.asList(new StringType("haa")), finalFlat);
        Assert.assertEquals("medication_order/medication_order/order:0/medication_item",
                            new ArrayList<>(finalFlat.entrySet()).get(0).getKey());

        openEhrPath = "medication_order/medication_order/order[n]/medication_item[n]";
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING", Arrays.asList(new StringType("haa")), finalFlat);
        Assert.assertEquals("medication_order/medication_order/order:0/medication_item:0",
                            new ArrayList<>(finalFlat.entrySet()).get(1).getKey());
    }

    @Test
    public void replaceMultipleOccurrenceSyntax_multiples() {
        String openEhrPath = "medication_order/medication_order/order[n]/medication_item";
        JsonObject finalFlat = new JsonObject();
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING",
                                              Arrays.asList(new StringType("1"), new StringType("2")), finalFlat);
        Assert.assertEquals(2, new ArrayList<>(finalFlat.entrySet()).size());
        Assert.assertEquals("1",
                            finalFlat.get("medication_order/medication_order/order:0/medication_item").getAsString());
        Assert.assertEquals("2",
                            finalFlat.get("medication_order/medication_order/order:1/medication_item").getAsString());

        finalFlat = new JsonObject();
        openEhrPath = "medication_order/medication_order/order[n]/medication_item[n]";
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING",
                                              Arrays.asList(new StringType("1"), new StringType("2"),
                                                            new StringType("3")), finalFlat);
        Assert.assertEquals(3, new ArrayList<>(finalFlat.entrySet()).size());
        Assert.assertEquals("1",
                            finalFlat.get("medication_order/medication_order/order:0/medication_item:0").getAsString());
        Assert.assertEquals("2",
                            finalFlat.get("medication_order/medication_order/order:0/medication_item:1").getAsString());
        Assert.assertEquals("3",
                            finalFlat.get("medication_order/medication_order/order:0/medication_item:2").getAsString());

        finalFlat = new JsonObject();
        openEhrPath = "medication_order/medication_order[n]/order[n]/medication_item[n]";
        fhirToOpenEhr.handleOccurrenceResults(openEhrPath, "STRING",
                                              Arrays.asList(new StringType("1"), new StringType("2"),
                                                            new StringType("3")), finalFlat);
        Assert.assertEquals(3, new ArrayList<>(finalFlat.entrySet()).size());
        Assert.assertEquals("1", finalFlat.get("medication_order/medication_order:0/order:0/medication_item:0")
                .getAsString());
        Assert.assertEquals("2", finalFlat.get("medication_order/medication_order:0/order:0/medication_item:1")
                .getAsString());
        Assert.assertEquals("3", finalFlat.get("medication_order/medication_order:0/order:0/medication_item:2")
                .getAsString());
    }

    @Test
    public void removeTypesTest() {
        String withType = "medication_order/medication_order[TYPE:INSTRUCTION]/order[n][TYPE:ACTIVITY]/medication_item[TYPE:ELEMENT]";
        String removed = openFhirStringUtils.removeTypes(withType);
        Assert.assertEquals("medication_order/medication_order/order[n]/medication_item", removed);

        withType = "medication_order/medication_order/order[n]/medication_item";
        removed = openFhirStringUtils.removeTypes(withType);
        Assert.assertEquals("medication_order/medication_order/order[n]/medication_item", removed);

        withType = "medication_order/medication_order[TYPE:INSTRUCTION]/order[0][TYPE:ACTIVITY]/medication_item[1][TYPE:ELEMENT]";
        removed = openFhirStringUtils.removeTypes(withType);
        Assert.assertEquals("medication_order/medication_order/order[0]/medication_item[1]", removed);
    }

    @Test
    public void getLatTypeTest() {
        String withType = "medication_order/medication_order[TYPE:INSTRUCTION]/order[n][TYPE:ACTIVITY]/medication_item[TYPE:ELEMENT]";
        String lastType = openFhirStringUtils.getLastType(withType);

        Assert.assertEquals("ELEMENT", lastType);
    }


}