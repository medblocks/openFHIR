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
import com.medblocks.openfhir.util.OpenEhrConditionEvaluator;
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
                                          new IntermediateCacheProcessing(openFhirStringUtils),
                                          new OpenEhrConditionEvaluator(openFhirStringUtils));
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

}