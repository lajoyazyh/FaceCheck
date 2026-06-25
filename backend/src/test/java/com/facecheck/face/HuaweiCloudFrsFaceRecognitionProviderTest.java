package com.facecheck.face;

import static org.assertj.core.api.Assertions.assertThat;

import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.facecheck.infrastructure.huawei.HuaweiFrsClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HuaweiCloudFrsFaceRecognitionProviderTest {

    @Test
    void shouldMapDetectSearchCompareAndDeleteCalls() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setSimilarityThreshold(85);
        HuaweiCloudFrsFaceRecognitionProvider provider =
                new HuaweiCloudFrsFaceRecognitionProvider(new SuccessfulClient(properties), properties);

        FaceRecognitionProvider.DetectFaceResult detectResult = provider.detectFace(new byte[]{1, 2, 3});
        FaceRecognitionProvider.SearchFaceResult searchResult = provider.searchFace(new byte[]{1, 2, 3}, 3);
        FaceRecognitionProvider.CompareFaceResult compareResult = provider.compareFace(new byte[]{1, 2, 3}, "face-1");
        FaceRecognitionProvider.EnrollFaceResult enrollResult =
                provider.enrollFace("photo-1", Map.of("userId", "u1"), new byte[]{1, 2, 3});

        assertThat(detectResult.acceptable()).isTrue();
        assertThat(searchResult.candidates()).hasSize(1);
        assertThat(searchResult.candidates().getFirst().faceId()).isEqualTo("face-1");
        assertThat(compareResult.matched()).isTrue();
        assertThat(enrollResult.faceId()).isEqualTo("face-1");
    }

    @Test
    void shouldFlagMultipleFacesAsUnacceptable() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setSimilarityThreshold(85);
        HuaweiCloudFrsFaceRecognitionProvider provider =
                new HuaweiCloudFrsFaceRecognitionProvider(new MultiFaceClient(properties), properties);

        FaceRecognitionProvider.DetectFaceResult detectResult = provider.detectFace(new byte[]{9});

        assertThat(detectResult.acceptable()).isFalse();
        assertThat(detectResult.failureCode()).isEqualTo("MULTIPLE_FACES");
    }

    private static final class SuccessfulClient extends HuaweiFrsClient {

        private SuccessfulClient(HuaweiCloudProperties properties) {
            super(properties);
        }

        @Override
        public DetectFaceResponse detect(byte[] imageBytes) {
            return new DetectFaceResponse(1, "detect-req");
        }

        @Override
        public EnrollFaceResponse enroll(String externalImageId, Map<String, String> externalFields, byte[] imageBytes) {
            return new EnrollFaceResponse("face-1", "enroll-req");
        }

        @Override
        public SearchFaceResponse search(byte[] imageBytes, int maxCandidates) {
            return new SearchFaceResponse(
                    List.of(new SearchCandidate("face-1", 91.3, Map.of("userId", "u1"))),
                    "search-req"
            );
        }

        @Override
        public CompareFaceResponse compare(byte[] imageBytes, String faceId) {
            return new CompareFaceResponse(88.8, "compare-req");
        }

        @Override
        public void deleteByExternalImageId(String externalImageId) {
        }
    }

    private static final class MultiFaceClient extends HuaweiFrsClient {

        private MultiFaceClient(HuaweiCloudProperties properties) {
            super(properties);
        }

        @Override
        public DetectFaceResponse detect(byte[] imageBytes) {
            return new DetectFaceResponse(2, "detect-req");
        }
    }
}
