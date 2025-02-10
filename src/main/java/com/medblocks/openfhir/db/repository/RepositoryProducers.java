package com.medblocks.openfhir.db.repository;

import com.medblocks.openfhir.db.repository.mongodb.BootstrapMongoRepository;
import com.medblocks.openfhir.db.repository.mongodb.FhirConnectContextMongoRepository;
import com.medblocks.openfhir.db.repository.mongodb.FhirConnectModelMongoRepository;
import com.medblocks.openfhir.db.repository.mongodb.OptMongoRepository;
import com.medblocks.openfhir.db.repository.postgres.BootstrapPgRepository;
import com.medblocks.openfhir.db.repository.postgres.FhirConnectContextPgRepository;
import com.medblocks.openfhir.db.repository.postgres.FhirConnectModelPgRepository;
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
    private final BootstrapPgRepository bootstrapPgRepository;
    private final BootstrapMongoRepository bootstrapMongoRepository;
    private FhirConnectModelPgRepository fhirConnectModelPgRepository;
    private FhirConnectModelMongoRepository fhirConnectModelMongoRepository;

    @Autowired
    public RepositoryProducers(@Autowired(required = false) final OptMongoRepository optMongoRepository,
                               @Autowired(required = false) final OptPgRepository optPgRepository,
                               @Autowired(required = false) final BootstrapPgRepository bootstrapPgRepository,
                               @Autowired(required = false) final BootstrapMongoRepository bootstrapMongoRepository,
                               @Autowired(required = false) final FhirConnectContextPgRepository fhirConnectContextPgRepository,
                               @Autowired(required = false) final FhirConnectContextMongoRepository fhirConnectContextMongoRepository,
                               @Autowired(required = false) final FhirConnectModelPgRepository fhirConnectModelPgRepository,
                               @Autowired(required = false) final FhirConnectModelMongoRepository fhirConnectModelMongoRepository) {
        this.optMongoRepository = optMongoRepository;
        this.optPgRepository = optPgRepository;
        this.fhirConnectContextPgRepository = fhirConnectContextPgRepository;
        this.fhirConnectContextMongoRepository = fhirConnectContextMongoRepository;
        this.fhirConnectModelPgRepository = fhirConnectModelPgRepository;
        this.fhirConnectModelMongoRepository = fhirConnectModelMongoRepository;
        this.bootstrapPgRepository = bootstrapPgRepository;
        this.bootstrapMongoRepository = bootstrapMongoRepository;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "postgres")
    public BootstrapRepository postgresBootstrapRepository() {
        return bootstrapPgRepository;
    }


    @Bean
    @Primary
    @ConditionalOnProperty(name = "db.type", havingValue = "mongo")
    public BootstrapRepository mongoBootstrapRepository() {
        return bootstrapMongoRepository;
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
    public FhirConnectModelPgRepository postgresFhirConnectModelRepository() {
        return fhirConnectModelPgRepository;
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
    public FhirConnectModelMongoRepository mongoFhirConnectModelRepository() {
        return fhirConnectModelMongoRepository;
    }
}
