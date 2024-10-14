package com.medblocks.openfhir.db.repository.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@ConditionalOnProperty(name = "db.type", havingValue = "mongo")
@EnableMongoRepositories(basePackages = "com.medblocks.openfhir.db.repository.mongodb", basePackageClasses = OptMongoRepository.class)
public class MongoConfig extends AbstractMongoClientConfiguration {
    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Override
    protected String getDatabaseName() {
        // Parse the MongoDB URI and extract the database name
        ConnectionString connectionString = new ConnectionString(mongoUri);
        final String database = connectionString.getDatabase();
        return StringUtils.isBlank(database) ? "openfhir" : database;
    }

    @Bean
    @Override
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri); // Creates a MongoClient with the specified URI
    }
}
