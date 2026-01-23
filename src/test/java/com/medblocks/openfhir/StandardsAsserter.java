package com.medblocks.openfhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class StandardsAsserter {

    private static final Gson GSON = new Gson();

    // sensible defaults (tune these once)
    private static final Set<String> DEFAULT_IGNORE_KEYS = Set.of(
            "id",
            "meta",
            "type",
            "timestamp"
    );

    private static final List<Pattern> DEFAULT_IGNORE_PATTERNS = List.of(
            // NOTE: These patterns apply to *flat keys* in your JsonObject.
            // Keep/adjust only if your keys really look like this.
            Pattern.compile("^entry\\[\\d+\\]\\.resource\\.id$"),
            Pattern.compile("^.*\\|time$"),
            Pattern.compile("^.*\\|date_time$")
    );

    JsonObject loadJsonObject(String classpathLocation) {
        InputStream is = getClass().getResourceAsStream(classpathLocation);
        if (is == null) {
            throw new IllegalArgumentException("File not found on classpath: " + classpathLocation);
        }
        return GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
    }

    public void assertFlatComposition(Composition composition, String filePath) {
        JsonObject actual = GSON.fromJson(new CanonicalJson().marshal(composition), JsonObject.class);
        JsonObject expected = loadJsonObject(filePath);
        assertFlatJsonReadable(actual, expected); // defaults
    }

    public void assertBundle(Bundle bundle, String filePath) {
        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();
        String serialized = parser.encodeResourceToString(bundle);

        JsonObject actual = GSON.fromJson(serialized, JsonObject.class);
        JsonObject expected = loadJsonObject(filePath);

        assertFlatJsonReadable(actual, expected); // defaults
    }

    // -------------------------
    // Overloads (clean API)
    // -------------------------

    /** Default ignores, extra keys allowed (recommended for bidirectional tests). */
    public void assertFlatJsonReadable(JsonObject actual, JsonObject expected) {
        assertFlatJsonReadable(actual, expected, DEFAULT_IGNORE_KEYS, DEFAULT_IGNORE_PATTERNS, false);
    }

    /** Default ignores, choose whether extra keys should fail. */
    public void assertFlatJsonReadable(JsonObject actual, JsonObject expected, boolean strictExtras) {
        assertFlatJsonReadable(actual, expected, DEFAULT_IGNORE_KEYS, DEFAULT_IGNORE_PATTERNS, strictExtras);
    }

    /** Custom ignores, extra keys allowed. */
    public void assertFlatJsonReadable(JsonObject actual,
                                       JsonObject expected,
                                       Set<String> ignoreKeys,
                                       List<Pattern> ignoreKeyPatterns) {
        assertFlatJsonReadable(actual, expected, ignoreKeys, ignoreKeyPatterns, false);
    }

    public void assertFlatJsonReadable(JsonObject actual,
                                       JsonObject expected,
                                       Set<String> ignoreKeys,
                                       List<Pattern> ignoreKeyPatterns,
                                       boolean strictExtras) {

        Objects.requireNonNull(actual, "actual JsonObject must not be null");
        Objects.requireNonNull(expected, "expected JsonObject must not be null");

        Set<String> ignoreKeySet = (ignoreKeys == null) ? Collections.emptySet() : ignoreKeys;
        List<Pattern> ignorePatterns = (ignoreKeyPatterns == null) ? Collections.emptyList() : ignoreKeyPatterns;

        Predicate<String> ignored = k ->
                ignoreKeySet.contains(k) ||
                        ignorePatterns.stream().anyMatch(p -> p.matcher(k).matches());

        // filter keys first (so missing/extra logic respects ignores)
        Set<String> aKeys = new TreeSet<>(actual.keySet());
        Set<String> eKeys = new TreeSet<>(expected.keySet());
        aKeys.removeIf(ignored);
        eKeys.removeIf(ignored);

        Set<String> missing = new TreeSet<>(eKeys);
        missing.removeAll(aKeys);

        Set<String> extra = new TreeSet<>(aKeys);
        extra.removeAll(eKeys);

        StringBuilder sb = new StringBuilder();

        Set<String> common = new TreeSet<>(aKeys);
        common.retainAll(eKeys);

        for (String key : common) {
            JsonElement av = actual.get(key);
            JsonElement ev = expected.get(key);

            boolean mismatch =
                    (av == null && ev != null) ||
                            (av != null && ev == null) ||
                            (av != null && ev != null && !av.equals(ev));

            if (mismatch) {
                sb.append("\nValue mismatch:\n")
                        .append("  key: ").append(key).append("\n")
                        .append("  expected: ").append(ev).append("\n")
                        .append("  actual:   ").append(av).append("\n");
            }
        }

        if (!missing.isEmpty()) {
            sb.append("\nMissing keys (expected but not present):\n");
            missing.forEach(k -> sb.append("  - ").append(k).append("\n"));
        }

        if (strictExtras && !extra.isEmpty()) {
            sb.append("\nExtra keys (present in actual but not expected):\n");
            extra.forEach(k -> sb.append("  + ").append(k).append("\n"));
        }

        if (sb.length() > 0) {
            Assert.fail("JSON comparison failed:" + sb);
        }
    }
}
