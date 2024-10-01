package com.medblocks.openfhir.producers;

import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
/**
 * EHRBase specific producers to avoid a new instantiation each time it's injected, which could lead to
 * performance decrease
 */
@Component
public class OpenEhrProducer {
    @Bean
    public FlatJsonUnmarshaller flatJsonUnmarshaller() {
        return new FlatJsonUnmarshaller();
    }

    @Bean
    public FlatJsonMarshaller flatJsonMarshaller() {
        return new FlatJsonMarshaller();
    }

}
