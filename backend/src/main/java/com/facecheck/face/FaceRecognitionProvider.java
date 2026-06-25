package com.facecheck.face;

import java.util.List;
import java.util.Map;

public interface FaceRecognitionProvider {

    DetectFaceResult detectFace(byte[] imageBytes);

    EnrollFaceResult enrollFace(String externalImageId, Map<String, String> externalFields, byte[] imageBytes);

    SearchFaceResult searchFace(byte[] imageBytes, int maxCandidates);

    CompareFaceResult compareFace(byte[] imageBytes, String faceId);

    void deleteFace(String externalImageId);

    record DetectFaceResult(int faceCount, boolean acceptable, String failureCode, String requestId) {
    }

    record EnrollFaceResult(String faceId, String requestId) {
    }

    record SearchCandidate(String faceId, double similarity, Map<String, String> externalFields) {
    }

    record SearchFaceResult(List<SearchCandidate> candidates, String requestId) {
    }

    record CompareFaceResult(boolean matched, double similarity, String requestId) {
    }
}
