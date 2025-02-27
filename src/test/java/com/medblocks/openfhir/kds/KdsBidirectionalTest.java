package com.medblocks.openfhir.kds;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import ca.uhn.fhir.parser.JsonParser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.TestOpenFhirMappingContext;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.kds.ehrbase.EhrBaseTestClient;
import com.medblocks.openfhir.tofhir.IntermediateCacheProcessing;
import com.medblocks.openfhir.tofhir.OpenEhrToFhir;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhr;
import com.medblocks.openfhir.util.FhirConnectModelMerger;
import com.medblocks.openfhir.util.FhirInstanceCreator;
import com.medblocks.openfhir.util.FhirInstanceCreatorUtility;
import com.medblocks.openfhir.util.FhirInstancePopulator;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import com.medblocks.openfhir.util.OpenEhrConditionEvaluator;
import com.medblocks.openfhir.util.OpenEhrPopulator;
import com.medblocks.openfhir.util.OpenFhirMapperUtils;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import com.medblocks.openfhir.util.OpenFhirTestUtility;
import com.nedap.archie.rm.composition.Composition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.http.ResponseEntity;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public abstract class KdsBidirectionalTest {

    /**
     * Change this to 'true' and set corresponding ehrbase variables if you want mapped Composition
     * to automatically be created against a running (by yourself) EHRBase instance. Meant for an integration
     * test and implicit validation of the mapped Composition.
     */
    final boolean TEST_AGAINST_EHRBASE = false;
    final String EHRBASE_BASIC_USERNAME = "ehrbase-user";
    final String EHRBASE_BASIC_PASSWORD = "SuperSecretPassword";
    final String EHRBASE_HOST = "http://localhost:8081";

    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
    final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();
    final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());
    final JsonParser jsonParser = (JsonParser) FhirContext.forR4().newJsonParser();

    TestOpenFhirMappingContext repo;
    OpenEhrToFhir openEhrToFhir;
    FhirToOpenEhr fhirToOpenEhr;

    FhirConnectContext context;
    OPERATIONALTEMPLATE operationaltemplate;
    String operationaltemplateSerialized;
    WebTemplate webTemplate;

    protected abstract void prepareState();

    @Before
    public void init() {
        repo = new TestOpenFhirMappingContext(fhirPath, openFhirStringUtils, fhirConnectModelMerger);
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            // todo!!
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });

        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(
                openFhirStringUtils);
        openEhrToFhir = new OpenEhrToFhir(new FlatJsonMarshaller(),
                                          repo,
                                          new OpenEhrCachedUtils(null),
                                          new Gson(),
                                          openFhirStringUtils,
                                          new OpenEhrRmWorker(openFhirStringUtils, openFhirMapperUtils),
                                          new OpenFhirMapperUtils(),
                                          new FhirInstancePopulator(),
                                          new FhirInstanceCreator(openFhirStringUtils, fhirInstanceCreatorUtility),
                                          fhirInstanceCreatorUtility,
                                          fhirPath,
                                          new IntermediateCacheProcessing(openFhirStringUtils),
                                          new OpenEhrConditionEvaluator(openFhirStringUtils));
        fhirToOpenEhr = new FhirToOpenEhr(fhirPath,
                                          new OpenFhirStringUtils(),
                                          new FlatJsonUnmarshaller(),
                                          new Gson(),
                                          new OpenEhrRmWorker(openFhirStringUtils, openFhirMapperUtils),
                                          openFhirStringUtils,
                                          repo,
                                          new OpenEhrCachedUtils(null),
                                          new OpenFhirMapperUtils(),
                                          new OpenEhrPopulator(new OpenFhirMapperUtils()));

        prepareState();
    }

    @Test
    public void toOpenEhrTest() {
        final JsonObject flatPaths = toOpenEhr();

        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(new Gson().toJson(flatPaths),
                                                                                     webTemplate);
        fhirToOpenEhr.enrichComposition(compositionFromFlat,null);


        if (testAgainstEhrBase()) {
            final ResponseEntity<String> result = new EhrBaseTestClient(EHRBASE_HOST,
                                                                        EHRBASE_BASIC_USERNAME,
                                                                        EHRBASE_BASIC_PASSWORD)
                    .createComposition(compositionFromFlat, operationaltemplateSerialized);
            final int resultCode = result.getStatusCode().value();
            if (resultCode != 204) {
                final String body = result.getBody();
                final String[] errors = body.split(", /");
                Arrays.stream(errors).forEach(log::error);
            } else {
                log.info("SUCCESSfully stored to EHRBase.");
            }
            Assert.assertEquals(204, resultCode);

        }
    }

    protected abstract JsonObject toOpenEhr();

    protected boolean testAgainstEhrBase() {
        return TEST_AGAINST_EHRBASE;
    }

    protected String getFlat(final String path) {
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        try {
            return IOUtils.toString(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void compareJsonObjects(final JsonObject initial, final JsonObject expected) {
        for (Map.Entry<String, JsonElement> initialEntrySet : expected.entrySet()) {
            final String initialKey = initialEntrySet.getKey();
            final String initialValue = initialEntrySet.getValue().getAsString();
            final String actualValue = initial.getAsJsonPrimitive(initialKey).getAsString();
            if (!initialValue.equals(actualValue)) {
                System.out.println(initialKey);
            }
            Assert.assertEquals(initialValue, actualValue);
        }
    }

    protected org.hl7.fhir.r4.model.Bundle getTestBundle(final String path) {
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        return (org.hl7.fhir.r4.model.Bundle) jsonParser.parseResource(inputStream);
    }

    protected FhirConnectContext getContext(final String path) {
        final Yaml yaml = OpenFhirTestUtility.getYaml();
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        return yaml.loadAs(inputStream, FhirConnectContext.class);
    }

    protected OPERATIONALTEMPLATE getOperationalTemplate() {
        try {
            return TemplateDocument.Factory.parse(operationaltemplateSerialized).getTemplate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
