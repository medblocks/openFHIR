package com.medblocks.openfhir.kds.medikationseintrag;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.kds.KdsBidirectionalTest;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Test;

import java.io.InputStream;
import java.util.SortedSet;
import java.util.TreeSet;

public class DebugMedikationseintragPrintTest extends KdsBidirectionalTest {

    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT = "/kds_new/projects/org.highmed/KDS/medikationseintrag/KDS_medikationseintrag.context.yaml";
    final String HELPER_LOCATION = "/kds/medikationseintrag/";
    final String BUNDLE = "KDS_Medikationseintrag_v1-Fhir-Bundle-input.json";

    @Override
    public void prepareState() {
        context = getContext(CONTEXT);
        try {
            operationaltemplateSerialized = new String(
                    getClass().getResourceAsStream(HELPER_LOCATION + "KDS_Medikationseintrag.opt").readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
    }

    @Test
    public void printFlatForDebug() {
        InputStream is = getClass().getResourceAsStream(HELPER_LOCATION + BUNDLE);
        Bundle bundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, is);

        JsonObject flat = fhirToOpenEhr.fhirToFlatJsonObject(context, bundle, operationaltemplate);

        SortedSet<String> keys = new TreeSet<>();
        flat.entrySet().forEach(e -> keys.add(e.getKey() + " = " + e.getValue().toString()));

        // Print only medication-related paths for brevity
        keys.stream()
            .filter(k -> k.contains("/arzneimittel/"))
            .forEach(System.out::println);
    }

    @Override
    public JsonObject toOpenEhr() {
        return null; // not used in this debug test
    }
}
