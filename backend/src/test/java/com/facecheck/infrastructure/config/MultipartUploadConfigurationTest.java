package com.facecheck.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class MultipartUploadConfigurationTest {

    @Test
    void configuresMultipartUploadLimitToTenMegabytes() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("spring.servlet.multipart.max-file-size", "10MB")
                .containsEntry("spring.servlet.multipart.max-request-size", "10MB");
    }
}
