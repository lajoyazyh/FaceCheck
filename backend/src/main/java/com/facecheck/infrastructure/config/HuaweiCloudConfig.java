package com.facecheck.infrastructure.config;

import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.face.HuaweiCloudFrsFaceRecognitionProvider;
import com.facecheck.face.MockFaceRecognitionProvider;
import com.facecheck.infrastructure.huawei.HuaweiFrsClient;
import com.facecheck.storage.HuaweiObsStorageService;
import com.facecheck.storage.HuaweiObsStorageServiceImpl;
import com.facecheck.storage.InMemoryHuaweiObsStorageService;
import com.facecheck.storage.ObjectKeyStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HuaweiCloudConfig {

    @Bean
    HuaweiFrsClient huaweiFrsClient(HuaweiCloudProperties properties) {
        return new HuaweiFrsClient(properties);
    }

    @Bean
    HuaweiObsStorageService huaweiObsStorageService(
            HuaweiCloudProperties properties,
            ObjectKeyStrategy objectKeyStrategy
    ) {
        if (properties.isObsEnabled() || properties.isEnabled()) {
            return new HuaweiObsStorageServiceImpl(properties, objectKeyStrategy);
        }
        return new InMemoryHuaweiObsStorageService(properties, objectKeyStrategy);
    }

    @Bean
    FaceRecognitionProvider faceRecognitionProvider(HuaweiCloudProperties properties, HuaweiFrsClient client) {
        if (properties.isEnabled()) {
            return new HuaweiCloudFrsFaceRecognitionProvider(client, properties);
        }
        return new MockFaceRecognitionProvider(properties);
    }
}
