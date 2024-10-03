package com.medblocks.openfhir.db.repository.postgres;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "db.type", havingValue = "postgres")
@EnableJpaRepositories(basePackages = "com.medblocks.openfhir.db.repository.postgres",
        basePackageClasses = OptPgRepository.class,
        entityManagerFactoryRef = "postgresEntityManagerFactory",
        transactionManagerRef = "postgresTransactionManager")
@Slf4j
public class PostgresConfig {

    @Value("${spring.datasource.url:#{null}}")
    private String postgresUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String postgresUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String postgresPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String postgresDriverClassName;

    @Value("${spring.jpa.hibernate.ddl-auto:update}")
    private String ddlAuto;

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSource postgresDataSource() {
        if (postgresUrl == null || postgresUsername == null || postgresPassword == null) {
            log.error("PostgreSQL is configured as db.type (=postgres), but datasource properties are missing. " +
                    "Make sure spring.datasource.url, spring.datasource.username, and spring.datasource.password are set.");
            throw new IllegalStateException("Missing PostgreSQL configuration, but db.type is set to 'postgres'. " +
                    "Please configure spring.datasource.url, username, and password. If you've set mongodb properties, configure db.type to be 'mongo'");
        }

        log.info("Configuring PostgreSQL DataSource with URL: {}", postgresUrl);
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(postgresDriverClassName);
        dataSource.setUrl(postgresUrl);
        dataSource.setUsername(postgresUsername);
        dataSource.setPassword(postgresPassword);
        return dataSource;
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean postgresEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(postgresDataSource());
        em.setPackagesToScan("com.medblocks.openfhir.db.entity");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        // Set Hibernate properties including the dialect
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean
    public PlatformTransactionManager postgresTransactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(postgresEntityManagerFactory().getObject());
        return transactionManager;
    }
}
