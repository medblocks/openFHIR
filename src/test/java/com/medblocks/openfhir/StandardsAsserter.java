package com.medblocks.openfhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.gson.*;
import com.nedap.archie.rm.composition.Composition;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.hl7.fhir.r4.model.Bundle;
import org.json.JSONObject;
import org.junit.Assert;
import org.skyscreamer.jsonassert.JSONAssert;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StandardsAsserter {

    private static final Gson GSON = new Gson();

    public void assertComposition(Composition composition, String expectedClasspathJson) {
        JSONObject actual = new JSONObject(new CanonicalJson().marshal(composition));
        JSONObject expected = loadJsonObject(expectedClasspathJson);
        JSONAssert.assertEquals(expected, actual, true);
    }

    public void assertBundle(Bundle bundle, String expectedClasspathJson) {
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();
        JSONObject actual = new JSONObject(parser.encodeResourceToString(bundle));

        JSONObject expected = loadJsonObject(expectedClasspathJson);

        JSONAssert.assertEquals(expected, actual, true);

    }

    private JSONObject loadJsonObject(String classpathLocation) {
        InputStream is = getClass().getResourceAsStream(classpathLocation);
        try {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON file", e);
        }
    }

}
