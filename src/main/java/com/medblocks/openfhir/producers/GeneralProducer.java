package com.medblocks.openfhir.producers;

import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.representer.Representer;

@Component
public class GeneralProducer {

    @Bean
    public Gson gson() {
        return new Gson();
    }

    @Bean
    public Yaml Yaml() {
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
