package com.medblocks.openfhir.kds;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import ca.uhn.fhir.parser.JsonParser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.TestOpenFhirMappingContext;
import com.medblocks.openfhir.fc.model.FhirConnectContext;
import com.medblocks.openfhir.tofhir.OpenEhrToFhir;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhr;
import com.medblocks.openfhir.util.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public abstract class KdsBidirectionalTest {

    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());
    final JsonParser jsonParser = (JsonParser) FhirContext.forR4().newJsonParser();

    TestOpenFhirMappingContext repo;
    OpenEhrToFhir openEhrToFhir;
    FhirToOpenEhr fhirToOpenEhr;

    FhirConnectContext context;
    OPERATIONALTEMPLATE operationaltemplate;
    WebTemplate webTemplate;

    protected abstract void prepareState();

    @Before
    public void init() {
        repo = new TestOpenFhirMappingContext(fhirPath, openFhirStringUtils);
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            // todo!!
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });

        openEhrToFhir = new OpenEhrToFhir(new FlatJsonMarshaller(),
                repo,
                new OpenEhrCachedUtils(),
                new Gson(),
                openFhirStringUtils,
                new OpenEhrRmWorker(openFhirStringUtils),
                new OpenFhirMapperUtils(),
                new FhirInstancePopulator(),
                new FhirInstanceCreator(openFhirStringUtils),
                fhirPath);
        fhirToOpenEhr = new FhirToOpenEhr(fhirPath,
                new OpenFhirStringUtils(),
                new FlatJsonUnmarshaller(),
                new Gson(),
                new OpenEhrRmWorker(openFhirStringUtils),
                openFhirStringUtils,
                repo,
                new OpenEhrCachedUtils(),
                new OpenFhirMapperUtils());

        prepareState();
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
            if(!initialValue.equals(actualValue)) {
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
        final Yaml yaml = new Yaml();
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        return yaml.loadAs(inputStream, FhirConnectContext.class);
    }

    protected OPERATIONALTEMPLATE getOperationalTemplate(final String path) {
        try {
            return TemplateDocument.Factory.parse(this.getClass().getResourceAsStream(path)).getTemplate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
