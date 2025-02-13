package com.medblocks.openfhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import ca.uhn.fhir.parser.JsonParser;
import com.google.gson.Gson;
import com.medblocks.openfhir.OpenEhrRmWorker;
import com.medblocks.openfhir.TestOpenFhirMappingContext;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
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
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
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
import org.yaml.snakeyaml.Yaml;

public abstract class GenericTest {

    final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
    final FhirConnectModelMerger fhirConnectModelMerger = new FhirConnectModelMerger();
    protected final FhirPathR4 fhirPath = new FhirPathR4(FhirContext.forR4());
    final JsonParser jsonParser = (JsonParser) FhirContext.forR4().newJsonParser();

    protected TestOpenFhirMappingContext repo;
    protected OpenEhrToFhir openEhrToFhir;
    protected FhirToOpenEhr fhirToOpenEhr;
    protected FhirConnectContext context;
    protected OPERATIONALTEMPLATE operationaltemplate;
    protected String operationaltemplateSerialized;
    protected WebTemplate webTemplate;

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

        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(openFhirStringUtils);
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

    protected abstract void prepareState();


    protected String getFlat(final String path) {
        final InputStream inputStream = this.getClass().getResourceAsStream(path);
        try {
            return IOUtils.toString(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
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
