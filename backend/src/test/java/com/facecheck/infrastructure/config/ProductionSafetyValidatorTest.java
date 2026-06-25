package com.facecheck.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionSafetyValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ProductionSafetyValidator.class);

    @Test
    void shouldRejectProdProfileWhenJwtSecretUsesPlaceholder() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "facecheck.security.jwt.secret=replace-with-a-long-random-secret",
                        "spring.datasource.password=strong-db-password",
                        "spring.data.redis.password=strong-redis-password",
                        "spring.rabbitmq.password=strong-rabbit-password"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("JWT_SECRET");
                });
    }

    @Test
    void shouldRejectEnabledHuaweiCloudWithoutRequiredCredentials() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "facecheck.security.jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.password=strong-db-password",
                        "spring.data.redis.password=strong-redis-password",
                        "spring.rabbitmq.password=strong-rabbit-password",
                        "facecheck.huawei.enabled=true",
                        "facecheck.huawei.obs-enabled=false",
                        "facecheck.huawei.face-set-name=facecheck-prod"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("FRS_AK");
                });
    }

    @Test
    void shouldRejectEnabledObsWithoutRequiredObsSettings() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "facecheck.security.jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.password=strong-db-password",
                        "spring.data.redis.password=strong-redis-password",
                        "spring.rabbitmq.password=strong-rabbit-password",
                        "facecheck.huawei.enabled=false",
                        "facecheck.huawei.obs-enabled=true",
                        "facecheck.huawei.ak=test-ak",
                        "facecheck.huawei.sk=test-sk"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("OBS_ENDPOINT");
                });
    }

    @Test
    void shouldAllowSafeProdConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "facecheck.security.jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.password=strong-db-password",
                        "spring.data.redis.password=strong-redis-password",
                        "spring.rabbitmq.password=strong-rabbit-password",
                        "facecheck.huawei.enabled=false",
                        "facecheck.huawei.obs-enabled=false"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void shouldRejectProdProfileWhenRedisPasswordUsesDefaultValue() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "facecheck.security.jwt.secret=0123456789abcdef0123456789abcdef",
                        "spring.datasource.password=strong-db-password",
                        "spring.data.redis.password=facecheck",
                        "spring.rabbitmq.password=strong-rabbit-password",
                        "facecheck.huawei.enabled=false",
                        "facecheck.huawei.obs-enabled=false"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("REDIS_PASSWORD");
                });
    }
}
