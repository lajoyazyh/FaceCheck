package com.facecheck.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.TestcontainersConfiguration;

@Testcontainers
public abstract class PostgresContainerSupport {

    static {
        System.setProperty("dockerconfig.source", "auto");
        System.setProperty("docker.host", "unix:///var/run/docker.sock");
        System.setProperty("api.version", "1.41");

        TestcontainersConfiguration configuration = TestcontainersConfiguration.getInstance();
        configuration.getUserProperties().setProperty(
                "docker.client.strategy",
                "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy");
        configuration.getUserProperties().setProperty("docker.host", "unix:///var/run/docker.sock");
        configuration.getUserProperties().setProperty("dockerconfig.source", "auto");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("facecheck")
            .withUsername("facecheck")
            .withPassword("facecheck");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    }
}
