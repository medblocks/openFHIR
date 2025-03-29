package com.medblocks.openfhir;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.medblocks.openfhir.plugin.api.FormatConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class, MongoRepositoriesAutoConfiguration.class, MongoAutoConfiguration.class})
@EnableScheduling
@Slf4j
public class OpenFhirImplApplication {

    private static final Logger log = LoggerFactory.getLogger(OpenFhirImplApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(OpenFhirImplApplication.class, args);
    }

    @Bean 
    public PluginManager pluginManager() {
        PluginManager pluginManager = new DefaultPluginManager();
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
       
        return pluginManager;
    }

    // @Bean
    // public CommandLineRunner testExtensions(PluginManager pluginManager) {
    //     return args -> {
    //         // Get all FormatConverter extensions
    //         List<FormatConverter> converters = pluginManager.getExtensions(FormatConverter.class);
            
    //         log.info("Found {} FormatConverter extensions", converters.size());
            
    //         if (converters.isEmpty()) {
    //             log.warn("No FormatConverter extensions found! Check if plugins are loaded correctly.");
                
    //             // Debug info to help troubleshoot
    //             log.info("Loaded plugins: {}", pluginManager.getPlugins());
    //             log.info("Started plugins: {}", pluginManager.getStartedPlugins());
    //             log.info("Plugin root: {}", pluginManager.getPluginsRoot());
    //         } else {
    //             // Test each converter
    //             for (FormatConverter converter : converters) {
    //                 log.info("Testing converter: {}", converter.getClass().getName());
                    
    //                 // Test OpenEHR to FHIR conversion
    //                 String sampleOpenEhr = "{\"example\": \"openEHR data\"}";
    //                 String fhirResult = converter.toFHIR(sampleOpenEhr);
    //                 log.info("OpenEHR to FHIR result: {}", fhirResult);
                    
    //                 // Test FHIR to OpenEHR conversion
    //                 String sampleFhir = "{\"resourceType\": \"Patient\", \"id\": \"test\"}";
    //                 String openEhrResult = converter.toOpenEHR(sampleFhir);
    //                 log.info("FHIR to OpenEHR result: {}", openEhrResult);
    //             }
    //         }
    //     };
    // }

}  
