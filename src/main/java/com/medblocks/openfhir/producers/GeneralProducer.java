package com.medblocks.openfhir.producers;

import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class GeneralProducer {
    @Bean
    public Gson gson() {
        return new Gson();
    }
}
