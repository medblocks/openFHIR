package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.repository.mongodb.FhirConnectContextMongoRepository;
import com.medblocks.openfhir.db.repository.mongodb.FhirConnectMapperMongoRepository;
import com.medblocks.openfhir.db.repository.mongodb.OptMongoRepository;
import com.medblocks.openfhir.db.repository.postgres.FhirConnectContextPgRepository;
import com.medblocks.openfhir.db.repository.postgres.FhirConnectMapperPgRepository;
import com.medblocks.openfhir.db.repository.postgres.OptPgRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RepositoryProducers {

    private OptMongoRepository optMongoRepository;
    private OptPgRepository optPgRepository;
    private FhirConnectContextPgRepository fhirConnectContextPgRepository;
    private FhirConnectContextMongoRepository fhirConnectContextMongoRepository;
    private FhirConnectMapperPgRepository fhirConnectMapperPgRepository;
    private FhirConnectMapperMongoRepository fhirConnectMapperMongoRepository;

    @Autowired
    public RepositoryProducers(@Autowired(required = false) final OptMongoRepository optMongoRepository,
                               @Autowired(required = false) final OptPgRepository optPgRepository,
                               @Autowired(required = false) final FhirConnectContextPgRepository fhirConnectContextPgRepository,
                               @Autowired(required = false) final FhirConnectContextMongoRepository fhirConnectContextMongoRepository,
                               @Autowired(required = false) final FhirConnectMapperPgRepository fhirConnectMapperPgRepository,
                               @Autowired(required = false) final FhirConnectMapperMongoRepository fhirConnectMapperMongoRepository) {
        this.optMongoRepository = optMongoRepository;
        this.optPgRepository = optPgRepository;
        this.fhirConnectContextPgRepository = fhirConnectContextPgRepository;
        this.fhirConnectContextMongoRepository = fhirConnectContextMongoRepository;
        this.fhirConnectMapperPgRepository = fhirConnectMapperPgRepository;
        this.fhirConnectMapperMongoRepository = fhirConnectMapperMongoRepository;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "postgres")
    public OptRepository postgresOptRepository() {
        return optPgRepository;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "postgres")
    public FhirConnectContextRepository postgresFhirConnectContextRepository() {
        return fhirConnectContextPgRepository;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "postgres")
    public FhirConnectMapperPgRepository postgresFhirConnectMapperRepository() {
        return fhirConnectMapperPgRepository;
    }


    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "mongo")
    public OptRepository mongoOptRepository() {
        return optMongoRepository;
    }


    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "mongo")
    public FhirConnectContextRepository mongoFhirConnectContextRepository() {
        return fhirConnectContextMongoRepository;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "mongo")
    public FhirConnectMapperMongoRepository mongoFhirConnectMapperRepository() {
        return fhirConnectMapperMongoRepository;
    }
}
