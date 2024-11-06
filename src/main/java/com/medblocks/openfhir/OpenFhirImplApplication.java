package com.medblocks.openfhir;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class, MongoAutoConfiguration.class})
@EnableScheduling
@Slf4j
public class OpenFhirImplApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenFhirImplApplication.class, args);
    }

}
