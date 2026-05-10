package com.facecheck.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class RedisRabbitContainerSupport extends PostgresContainerSupport {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withUser("facecheck", "facecheck")
            .withPermission("/", "facecheck", ".*", ".*", ".*");

    @DynamicPropertySource
    static void configureRedisRabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "facecheck");
        registry.add("spring.rabbitmq.password", () -> "facecheck");
    }
}
