package com.facecheck.face;

import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MockFaceRecognitionProvider implements FaceRecognitionProvider {

    private final HuaweiCloudProperties properties;

    public MockFaceRecognitionProvider(HuaweiCloudProperties properties) {
        this.properties = properties;
    }

    @Override
    public DetectFaceResult detectFace(byte[] imageBytes) {
        boolean acceptable = imageBytes.length > 0;
        return new DetectFaceResult(acceptable ? 1 : 0, acceptable, acceptable ? null : "NO_FACE", "mock-detect");
    }

    @Override
    public EnrollFaceResult enrollFace(String externalImageId, Map<String, String> externalFields, byte[] imageBytes) {
        return new EnrollFaceResult("mock-face-" + externalImageId, "mock-enroll");
    }

    @Override
    public SearchFaceResult searchFace(byte[] imageBytes, int maxCandidates) {
        SearchCandidate candidate = new SearchCandidate(
                "mock-face-" + UUID.randomUUID(),
                properties.getSimilarityThreshold(),
                Map.of("source", "mock")
        );
        return new SearchFaceResult(List.of(candidate), "mock-search");
    }

    @Override
    public CompareFaceResult compareFace(byte[] imageBytes, String faceId) {
        return new CompareFaceResult(true, properties.getSimilarityThreshold(), "mock-compare");
    }

    @Override
    public void deleteFace(String externalImageId) {
    }
}
