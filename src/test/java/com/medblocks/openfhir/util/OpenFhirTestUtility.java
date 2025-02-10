package com.medblocks.openfhir.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import java.io.InputStream;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;

public class OpenFhirTestUtility {

    public static Yaml getYaml() {
        final LoaderOptions loaderOptions = new LoaderOptions();
        final Representer representer = new Representer(new DumperOptions());
        representer.getPropertyUtils().setSkipMissingProperties(true);
        loaderOptions.setEnumCaseSensitive(false);
        final Constructor constructor = new Constructor(loaderOptions);
        constructor.setPropertyUtils(new PropertyUtils() {
            @Override
            public Property getProperty(Class<? extends Object> type, String name) {
                if ( name.equals("extends") ) {
                    name = "_extends";
                }
                return super.getProperty(type, name);
            }
        });
        return new Yaml(constructor, representer);
    }


}
