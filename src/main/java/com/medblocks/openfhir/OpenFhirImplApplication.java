package com.medblocks.openfhir;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
@EnableScheduling
@Slf4j
public class OpenFhirImplApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenFhirImplApplication.class, args);
    }

}
