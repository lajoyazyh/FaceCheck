package com.facecheck.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductionSafetyValidator implements InitializingBean {

    private static final String JWT_PLACEHOLDER = "replace-with-a-long-random-secret";
    private static final String DEFAULT_INFRA_PASSWORD = "facecheck";

    private final Environment environment;
    private final String jwtSecret;
    private final String datasourcePassword;
    private final String redisPassword;
    private final String rabbitPassword;
    private final boolean huaweiEnabled;
    private final String frsAk;
    private final String frsSk;
    private final String frsProjectId;
    private final String frsRegion;
    private final String frsEndpoint;
    private final String obsEndpoint;
    private final String obsRegion;
    private final String obsBucket;
    private final String faceSetName;

    public ProductionSafetyValidator(
            Environment environment,
            @Value("${facecheck.security.jwt.secret:}") String jwtSecret,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${spring.rabbitmq.password:}") String rabbitPassword,
            @Value("${facecheck.huawei.enabled:false}") boolean huaweiEnabled,
            @Value("${facecheck.huawei.ak:}") String frsAk,
            @Value("${facecheck.huawei.sk:}") String frsSk,
            @Value("${facecheck.huawei.project-id:}") String frsProjectId,
            @Value("${facecheck.huawei.region:}") String frsRegion,
            @Value("${facecheck.huawei.frs-endpoint:}") String frsEndpoint,
            @Value("${facecheck.huawei.obs-endpoint:}") String obsEndpoint,
            @Value("${facecheck.huawei.obs-region:}") String obsRegion,
            @Value("${facecheck.huawei.obs-bucket:}") String obsBucket,
            @Value("${facecheck.huawei.face-set-name:}") String faceSetName
    ) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.datasourcePassword = datasourcePassword;
        this.redisPassword = redisPassword;
        this.rabbitPassword = rabbitPassword;
        this.huaweiEnabled = huaweiEnabled;
        this.frsAk = frsAk;
        this.frsSk = frsSk;
        this.frsProjectId = frsProjectId;
        this.frsRegion = frsRegion;
        this.frsEndpoint = frsEndpoint;
        this.obsEndpoint = obsEndpoint;
        this.obsRegion = obsRegion;
        this.obsBucket = obsBucket;
        this.faceSetName = faceSetName;
    }

    @Override
    public void afterPropertiesSet() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        List<String> issues = new ArrayList<>();
        validateJwtSecret(issues);
        validateInfraPassword("DB_PASSWORD", datasourcePassword, issues);
        validateInfraPassword("REDIS_PASSWORD", redisPassword, issues);
        validateInfraPassword("RABBITMQ_PASSWORD", rabbitPassword, issues);
        validateHuaweiSettings(issues);

        if (!issues.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", issues));
        }
    }

    private void validateJwtSecret(List<String> issues) {
        if (!StringUtils.hasText(jwtSecret) || JWT_PLACEHOLDER.equals(jwtSecret) || jwtSecret.length() < 32) {
            issues.add("JWT_SECRET must be explicitly configured with at least 32 characters in prod");
        }
    }

    private void validateInfraPassword(String variableName, String value, List<String> issues) {
        if (!StringUtils.hasText(value) || DEFAULT_INFRA_PASSWORD.equals(value)) {
            issues.add(variableName + " must not use the default development password in prod");
        }
    }

    private void validateHuaweiSettings(List<String> issues) {
        if (!huaweiEnabled) {
            return;
        }

        require("FRS_AK", frsAk, issues);
        require("FRS_SK", frsSk, issues);
        require("FRS_PROJECT_ID", frsProjectId, issues);
        require("FRS_REGION", frsRegion, issues);
        require("FRS_ENDPOINT", frsEndpoint, issues);
        require("OBS_ENDPOINT", obsEndpoint, issues);
        require("OBS_REGION", obsRegion, issues);
        require("OBS_BUCKET", obsBucket, issues);
        require("FRS_FACE_SET_NAME", faceSetName, issues);
    }

    private void require(String variableName, String value, List<String> issues) {
        if (!StringUtils.hasText(value)) {
            issues.add(variableName + " is required when HUAWEI_CLOUD_ENABLED=true");
        }
    }
}
