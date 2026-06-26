package com.facecheck.face;

import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.facecheck.infrastructure.huawei.HuaweiFrsClient;
import java.util.List;

public class HuaweiCloudFrsFaceRecognitionProvider implements FaceRecognitionProvider {

    private final HuaweiFrsClient client;
    private final HuaweiCloudProperties properties;

    public HuaweiCloudFrsFaceRecognitionProvider(HuaweiFrsClient client, HuaweiCloudProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public DetectFaceResult detectFace(byte[] imageBytes) {
        HuaweiFrsClient.DetectFaceResponse response = client.detect(imageBytes);
        int faceCount = response.faceCount();
        boolean acceptable = faceCount == 1;
        String failureCode = acceptable ? null : faceCount == 0 ? "NO_FACE" : "MULTIPLE_FACES";
        return new DetectFaceResult(faceCount, acceptable, failureCode, response.requestId());
    }

    @Override
    public EnrollFaceResult enrollFace(String externalImageId, java.util.Map<String, String> externalFields, byte[] imageBytes) {
        HuaweiFrsClient.EnrollFaceResponse response = client.enroll(externalImageId, externalFields, imageBytes);
        return new EnrollFaceResult(response.faceId(), response.requestId());
    }

    @Override
    public SearchFaceResult searchFace(byte[] imageBytes, int maxCandidates) {
        HuaweiFrsClient.SearchFaceResponse response = client.search(imageBytes, maxCandidates);
        List<SearchCandidate> candidates = response.candidates().stream()
                .map(candidate -> new SearchCandidate(candidate.faceId(), candidate.similarity(), candidate.externalFields()))
                .toList();
        return new SearchFaceResult(candidates, response.requestId());
    }

    @Override
    public CompareFaceResult compareFace(byte[] imageBytes, String faceId) {
        HuaweiFrsClient.CompareFaceResponse response = client.compare(imageBytes, faceId);
        boolean matched = response.similarity() >= properties.getSimilarityThreshold();
        return new CompareFaceResult(matched, response.similarity(), response.requestId());
    }

    @Override
    public void deleteFace(String externalImageId) {
        client.deleteByExternalImageId(externalImageId);
    }
}
