package com.medblocks.openfhir.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import java.io.InputStream;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

public class OpenFhirTestUtility {

    public static Yaml getYaml() {
        final LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setEnumCaseSensitive(false);
        return new Yaml(loaderOptions);
    }


}
