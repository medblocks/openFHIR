package com.medblocks.openfhir.tofhir;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.TestOpenFhirMappingContext;
import com.medblocks.openfhir.util.FhirConnectModelMerger;
import com.medblocks.openfhir.util.FhirInstanceCreator;
import com.medblocks.openfhir.util.FhirInstanceCreatorUtility;
import com.medblocks.openfhir.util.FhirInstancePopulator;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.junit.Assert;
import org.junit.Test;

public class OpenEhrToFhirTest {

    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());
    final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();
    final TestOpenFhirMappingContext repo = new TestOpenFhirMappingContext(fhirPath, openFhirStringUtils,
                                                                           fhirConnectModelMerger);
    final OpenEhrToFhir openEhrToFhir;

    {
        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(
                openFhirStringUtils);
        openEhrToFhir = new OpenEhrToFhir(new FlatJsonMarshaller(),
                                          repo,
                                          new OpenEhrCachedUtils(null),
                                          new Gson(),
                                          openFhirStringUtils,
                                          new OpenEhrRmWorker(openFhirStringUtils),
                                          new OpenFhirMapperUtils(),
                                          new FhirInstancePopulator(),
                                          new FhirInstanceCreator(openFhirStringUtils, fhirInstanceCreatorUtility),
                                          fhirInstanceCreatorUtility,
                                          fhirPath,
                                          new IntermediateCacheProcessing(openFhirStringUtils));
    }


    @Test
    public void joinValuesThatAreOne() {
        final List<String> toJoin = Arrays.asList(
                "growth_chart/body_weight/any_event:1/weight|unit",
                "growth_chart/body_weight/any_event:1/comment",
                "growth_chart/body_weight/any_event:1/state_of_dress|code",
                "growth_chart/body_weight/any_event:1/state_of_dress|terminology",
                "growth_chart/body_weight/any_event:1/state_of_dress|value",
                "growth_chart/body_weight/any_event:1/confounding_factors:0",
                "growth_chart/body_weight/any_event:1/time",
                "growth_chart/body_weight/any_event:2/weight|unit",
                "growth_chart/body_weight/any_event:2/weight|magnitude",
                "growth_chart/body_weight/any_event:2/comment",
                "growth_chart/body_weight/any_event:2/state_of_dress|code",
                "growth_chart/body_weight/any_event:2/state_of_dress|value",
                "growth_chart/body_weight/any_event:2/state_of_dress|terminology",
                "growth_chart/body_weight/any_event:2/confounding_factors:0",
                "growth_chart/body_weight/any_event:2/time",
                "growth_chart/body_weight/any_event:2/width",
                "growth_chart/body_weight/any_event:2/math_function|terminology",
                "growth_chart/body_weight/any_event:2/math_function|code",
                "growth_chart/body_weight/any_event:2/math_function|value",
                "growth_chart/body_weight/language|code",
                "growth_chart/body_weight/language|terminology",
                "growth_chart/body_weight/encoding|code",
                "growth_chart/body_weight/encoding|terminology",
                "growth_chart/body_weight/_work_flow_id|id",
                "growth_chart/body_weight/_work_flow_id|id_scheme",
                "growth_chart/body_weight/_work_flow_id|namespace",
                "growth_chart/body_weight/_work_flow_id|type",
                "growth_chart/body_weight/_guideline_id|id"
        );
        final Map<String, List<String>> stringListMap = openEhrToFhir.joinValuesThatAreOne(toJoin);
        Assert.assertEquals(3, stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").size());
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|code",
                            stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(0));
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|terminology",
                            stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(1));
        Assert.assertEquals("growth_chart/body_weight/any_event:1/state_of_dress|value",
                            stringListMap.get("growth_chart/body_weight/any_event:1/state_of_dress").get(2));
        Assert.assertEquals(3, stringListMap.get("growth_chart/body_weight/any_event:2/math_function").size());
        Assert.assertEquals(2, stringListMap.get("growth_chart/body_weight/encoding").size());
        Assert.assertEquals(1, stringListMap.get("growth_chart/body_weight/any_event:1/confounding_factors:0").size());
    }

    @Test
    public void joinValuesThatAreOne_oneContainsTheOther() {
        final List<String> toJoin = Arrays.asList(
                "diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_der_genesung",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|code",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|terminology",
                "diagnose/diagnose:0/klinischer_status/diagnostic_status|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|terminology",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|code",
                "diagnose/diagnose:0/klinischer_status/klinischer_status|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|value",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|terminology",
                "diagnose/diagnose:0/klinischer_status/klinischer_status2|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|terminology",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle|value",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|value",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|code",
                "diagnose/diagnose:0/klinischer_status/diagnoserolle2|terminology",
                "diagnose/diagnose:0/diagnosesicherheit|value",
                "diagnose/diagnose:0/diagnosesicherheit|terminology",
                "diagnose/diagnose:0/diagnosesicherheit|code",
                "diagnose/diagnose:0/diagnosesicherheit2|value",
                "diagnose/diagnose:0/diagnosesicherheit2|code",
                "diagnose/diagnose:0/diagnosesicherheit2|terminology",
                "diagnose/diagnose:0/diagnoseerläuterung",
                "diagnose/diagnose:0/letztes_dokumentationsdatum",
                "diagnose/diagnose:0/language|code",
                "diagnose/diagnose:0/language|terminology"
        );
        final Map<String, List<String>> stringListMap = openEhrToFhir.joinValuesThatAreOne(toJoin);
        Assert.assertEquals(3, stringListMap.get("diagnose/diagnose:0/klinischer_status/klinischer_status").size());

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive("random")));

        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|code", new JsonPrimitive("44"));
        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de' available"));
        flatJsonObject.add("diagnose/diagnose:0/lebensphase/ende|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://fhir.de/ValueSet/lebensphase-de"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|value",
                           new JsonPrimitive("†"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|code",
                           new JsonPrimitive("at0002"));
        flatJsonObject.add("diagnose/diagnose:0/multiple_coding_icd-10-gm/multiple_coding_identifier|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_der_genesung",
                           new JsonPrimitive("2022-02-03T04:05:06"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|code", new JsonPrimitive("at0016"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnostic_status|value",
                           new JsonPrimitive("Preliminary"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|code", new JsonPrimitive("at0026"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status|value",
                           new JsonPrimitive("Active"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|value",
                           new JsonPrimitive("Active"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|terminology",
                           new JsonPrimitive("local"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/klinischer_status2|code",
                           new JsonPrimitive("at0026"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role' available"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role' available"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/klinischer_status/diagnoserolle2|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/diagnosis-role"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT' available"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|value", new JsonPrimitive(
                "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT' available"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|code", new JsonPrimitive("42"));
        flatJsonObject.add("diagnose/diagnose:0/diagnosesicherheit2|terminology", new JsonPrimitive(
                "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT"));
        flatJsonObject.add("diagnose/diagnose:0/diagnoseerläuterung", new JsonPrimitive("Lorem ipsum"));
        flatJsonObject.add("diagnose/diagnose:0/letztes_dokumentationsdatum", new JsonPrimitive("2022-02-03T04:05:06"));
        flatJsonObject.add("diagnose/diagnose:0/language|code", new JsonPrimitive("en"));

        final List<String> matchingEntries = openEhrToFhir.getAllEntriesThatMatch(
                openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(
                        "diagnose/diagnose/klinischer_status/klinischer_status"),
                flatJsonObject);
        Assert.assertEquals(3, matchingEntries.size());
    }

    @Test
    public void joinValuesThatAreOne_dots() {
        final List<String> toJoin = Arrays.asList("stationärer_versorgungsfall/context/start_time",
                                                  "stationärer_versorgungsfall/context/setting|terminology",
                                                  "stationärer_versorgungsfall/context/setting|code",
                                                  "stationärer_versorgungsfall/context/setting|value",
                                                  "stationärer_versorgungsfall/context/_end_time",
                                                  "stationärer_versorgungsfall/context/_health_care_facility|name",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_1._und_2._stelle|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_3._stelle|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmegrund_-_4._stelle|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|terminology",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|value",
                                                  "stationärer_versorgungsfall/aufnahmedaten/aufnahmeanlass|code",
                                                  "stationärer_versorgungsfall/aufnahmedaten/kennung_vor_der_aufnahme",
                                                  "stationärer_versorgungsfall/aufnahmedaten/datum_uhrzeit_der_aufnahme",
                                                  "stationärer_versorgungsfall/aufnahmedaten/vorheriger_patientenstandort_vor_aufnahme/campus"
        );

        final JsonObject flatJsonObject = new JsonObject();
        toJoin.forEach(tj -> flatJsonObject.add(tj, new JsonPrimitive("random")));

        final String testingPath = "$archetype.aufnahmedaten.aufnahmegrund_-_1\\._und_2\\._stelle";

        final String prepared = openFhirStringUtils.prepareOpenEhrSyntax(testingPath, "stationärer_versorgungsfall");

        final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(prepared);
        final List<String> matchingEntries = openEhrToFhir.getAllEntriesThatMatch(withRegex, flatJsonObject);
        Assert.assertEquals(3, matchingEntries.size());

    }

}