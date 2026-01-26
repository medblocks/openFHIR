package com.medblocks.openfhir.kds;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import ca.uhn.fhir.parser.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.*;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.kds.ehrbase.EhrBaseTestClient;
import com.medblocks.openfhir.tofhir.IntermediateCacheProcessing;
import com.medblocks.openfhir.tofhir.OpenEhrToFhir;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhr;
import com.medblocks.openfhir.util.*;
import com.nedap.archie.rm.composition.Composition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.http.ResponseEntity;

@Slf4j
public abstract class KdsTest {

    // integration toggle
    protected boolean TEST_AGAINST_EHRBASE = false;
    protected String EHRBASE_BASIC_USERNAME = "ehrbase-user";
    protected String EHRBASE_BASIC_PASSWORD = "SuperSecretPassword";
    protected String EHRBASE_HOST = "http://localhost:8081";

    protected final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    protected final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
    protected final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();

    protected final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());
    protected final JsonParser jsonParser = (JsonParser) FhirContext.forR4().newJsonParser();

    protected TestOpenFhirMappingContext repo;
    protected OpenEhrToFhir openEhrToFhir;
    protected FhirToOpenEhr fhirToOpenEhr;

    protected FhirConnectContext context;
    protected OPERATIONALTEMPLATE operationaltemplate;
    protected String operationaltemplateSerialized;
    protected WebTemplate webTemplate;

    protected StandardsAsserter standardsAsserter = new StandardsAsserter();
    protected final Gson gson = new Gson();
    protected final FlatJsonUnmarshaller flatUnmarshaller = new FlatJsonUnmarshaller();
    protected final FlatJsonMarshaller flatMarshaller = new FlatJsonMarshaller();

    /** subclasses set context/template/webTemplate/etc here */
    protected abstract void prepareState();

    @Before
    public void initBase() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));

        repo = new TestOpenFhirMappingContext(fhirPath, openFhirStringUtils, fhirConnectModelMerger);

        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });

        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility =
                new FhirInstanceCreatorUtility(openFhirStringUtils);

        openEhrToFhir =
                new OpenEhrToFhir(
                        flatMarshaller,
                        repo,
                        new OpenEhrCachedUtils(null),
                        gson,
                        openFhirStringUtils,
                        new OpenEhrRmWorker(openFhirStringUtils, openFhirMapperUtils),
                        new OpenFhirMapperUtils(),
                        new FhirInstancePopulator(),
                        new FhirInstanceCreator(openFhirStringUtils, fhirInstanceCreatorUtility),
                        fhirInstanceCreatorUtility,
                        fhirPath,
                        new IntermediateCacheProcessing(openFhirStringUtils),
                        new OpenEhrConditionEvaluator(openFhirStringUtils));

        fhirToOpenEhr =
                new FhirToOpenEhr(
                        fhirPath,
                        new OpenFhirStringUtils(),
                        flatUnmarshaller,
                        gson,
                        new OpenEhrRmWorker(openFhirStringUtils, openFhirMapperUtils),
                        openFhirStringUtils,
                        repo,
                        new OpenEhrCachedUtils(null),
                        new OpenFhirMapperUtils(),
                        new OpenEhrPopulator(new OpenFhirMapperUtils()));

        prepareState();
    }

    protected boolean testAgainstEhrBase() {
        return TEST_AGAINST_EHRBASE;
    }

    protected void storeToEhrBaseOrFail(Composition composition) {
        if (!testAgainstEhrBase()) return;

        ResponseEntity<String> result =
                new EhrBaseTestClient(EHRBASE_HOST, EHRBASE_BASIC_USERNAME, EHRBASE_BASIC_PASSWORD)
                        .createComposition(composition, operationaltemplateSerialized);

        int code = result.getStatusCode().value();
        if (code != 204) {
            String body = result.getBody();
            if (body != null) Arrays.stream(body.split(", /")).forEach(log::error);
        } else {
            log.info("Successfully stored to EHRBase.");
        }
        org.junit.Assert.assertEquals(204, code);
    }

    protected String getFile(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        try {
            return IOUtils.toString(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected org.hl7.fhir.r4.model.Bundle getTestBundle(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        return (org.hl7.fhir.r4.model.Bundle) jsonParser.parseResource(is);
    }

    protected FhirConnectContext getContext(String path) {
        ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        InputStream is = getClass().getResourceAsStream(path);
        try {
            return yaml.readValue(is, FhirConnectContext.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected OPERATIONALTEMPLATE getOperationalTemplate() {
        try {
            return TemplateDocument.Factory.parse(operationaltemplateSerialized).getTemplate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void compareJsonObjects(JsonObject actual, JsonObject expected) {
        for (Map.Entry<String, JsonElement> e : expected.entrySet()) {
            String key = e.getKey();
            String expectedValue = e.getValue().getAsString();
            String actualValue = actual.getAsJsonPrimitive(key).getAsString();
            if (!expectedValue.equals(actualValue)) {
                System.out.println(key);
            }
            org.junit.Assert.assertEquals(expectedValue, actualValue);
        }
    }
}
