package com.facecheck.infrastructure.huawei;

import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.huaweicloud.sdk.core.auth.BasicCredentials;
import com.huaweicloud.sdk.core.exception.ConnectionException;
import com.huaweicloud.sdk.core.exception.RequestTimeoutException;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.core.http.HttpConfig;
import com.huaweicloud.sdk.core.region.Region;
import com.huaweicloud.sdk.frs.v2.FrsClient;
import com.huaweicloud.sdk.frs.v2.model.AddFacesBase64Req;
import com.huaweicloud.sdk.frs.v2.model.AddFacesByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.AddFacesByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.DeleteFaceByExternalImageIdRequest;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.FaceDetectBase64Req;
import com.huaweicloud.sdk.frs.v2.model.FaceSearchBase64Req;
import com.huaweicloud.sdk.frs.v2.model.FaceSetFace;
import com.huaweicloud.sdk.frs.v2.model.SearchFace;
import com.huaweicloud.sdk.frs.v2.model.SearchFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.SearchFaceByBase64Response;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class HuaweiFrsClient {

    private static final int COMPARE_CONFIRMATION_CANDIDATES = 10;

    private final HuaweiCloudProperties properties;
    private final AtomicReference<FrsClient> clientRef = new AtomicReference<>();

    public HuaweiFrsClient(HuaweiCloudProperties properties) {
        this.properties = properties;
    }

    HuaweiFrsClient(HuaweiCloudProperties properties, FrsClient client) {
        this.properties = properties;
        this.clientRef.set(client);
    }

    public DetectFaceResponse detect(byte[] imageBytes) {
        try {
            DetectFaceByBase64Response response = client().detectFaceByBase64(
                    new DetectFaceByBase64Request()
                            .withBody(new FaceDetectBase64Req().withImageBase64(base64(imageBytes)))
            );
            int faceCount = response.getFaces() == null ? 0 : response.getFaces().size();
            return new DetectFaceResponse(faceCount, response.getXRequestId());
        } catch (RuntimeException exception) {
            throw mapException("DetectFace", exception);
        }
    }

    public EnrollFaceResponse enroll(String externalImageId, Map<String, String> externalFields, byte[] imageBytes) {
        try {
            AddFacesByBase64Response response = client().addFacesByBase64(
                    new AddFacesByBase64Request()
                            .withFaceSetName(faceSetName())
                            .withBody(new AddFacesBase64Req()
                                    .withImageBase64(base64(imageBytes))
                                    .withExternalImageId(externalImageId)
                                    .withExternalFields(externalFields)
                                    .withSingle(true))
            );
            FaceSetFace enrolledFace = first(response.getFaces());
            if (enrolledFace == null || isBlank(enrolledFace.getFaceId())) {
                throw new HuaweiFrsException(
                        "FRS_ERROR",
                        "Huawei FRS AddFaces response did not include a faceId.",
                        response.getXRequestId(),
                        false
                );
            }
            return new EnrollFaceResponse(enrolledFace.getFaceId(), response.getXRequestId());
        } catch (RuntimeException exception) {
            throw mapException("AddFaces", exception);
        }
    }

    public SearchFaceResponse search(byte[] imageBytes, int maxCandidates) {
        try {
            SearchFaceByBase64Response response = client().searchFaceByBase64(searchRequest(imageBytes, maxCandidates));
            List<SearchCandidate> candidates = response.getFaces() == null
                    ? List.of()
                    : response.getFaces().stream()
                    .filter(Objects::nonNull)
                    .map(face -> new SearchCandidate(
                            nullToEmpty(face.getFaceId()),
                            face.getSimilarity() == null ? 0.0 : face.getSimilarity(),
                            toStringMap(face.getExternalFields())
                    ))
                    .toList();
            return new SearchFaceResponse(candidates, response.getXRequestId());
        } catch (RuntimeException exception) {
            throw mapException("SearchFace", exception);
        }
    }

    public CompareFaceResponse compare(byte[] imageBytes, String faceId) {
        try {
            SearchFaceByBase64Response response = client().searchFaceByBase64(
                    searchRequest(imageBytes, COMPARE_CONFIRMATION_CANDIDATES)
            );
            SearchFace matchedFace = response.getFaces() == null
                    ? null
                    : response.getFaces().stream()
                    .filter(face -> face != null && faceId.equals(face.getFaceId()))
                    .findFirst()
                    .orElse(null);
            double similarity = matchedFace == null || matchedFace.getSimilarity() == null
                    ? 0.0
                    : matchedFace.getSimilarity();
            return new CompareFaceResponse(similarity, response.getXRequestId());
        } catch (RuntimeException exception) {
            throw mapException("CompareFace", exception);
        }
    }

    public void deleteByExternalImageId(String externalImageId) {
        try {
            client().deleteFaceByExternalImageId(
                    new DeleteFaceByExternalImageIdRequest()
                            .withFaceSetName(faceSetName())
                            .withExternalImageId(externalImageId)
            );
        } catch (RuntimeException exception) {
            throw mapException("DeleteFace", exception);
        }
    }

    public HuaweiCloudProperties properties() {
        return properties;
    }

    private SearchFaceByBase64Request searchRequest(byte[] imageBytes, int maxCandidates) {
        int topN = Math.max(1, maxCandidates);
        return new SearchFaceByBase64Request()
                .withFaceSetName(faceSetName())
                .withBody(new FaceSearchBase64Req()
                        .withImageBase64(base64(imageBytes))
                        .withTopN(topN)
                        .withReturnFields(List.of("userId", "photoId")));
    }

    private FrsClient client() {
        FrsClient existing = clientRef.get();
        if (existing != null) {
            return existing;
        }
        FrsClient created = createClient(properties);
        if (clientRef.compareAndSet(null, created)) {
            return created;
        }
        return clientRef.get();
    }

    private static FrsClient createClient(HuaweiCloudProperties properties) {
        requireConfigured(properties.getAk(), "FRS_AK");
        requireConfigured(properties.getSk(), "FRS_SK");
        requireConfigured(properties.getProjectId(), "FRS_PROJECT_ID");
        requireConfigured(properties.getRegion(), "FRS_REGION");
        requireConfigured(properties.getFrsEndpoint(), "FRS_ENDPOINT");
        requireConfigured(properties.getFaceSetName(), "FRS_FACE_SET_NAME");

        BasicCredentials credentials = new BasicCredentials()
                .withAk(properties.getAk())
                .withSk(properties.getSk())
                .withProjectId(properties.getProjectId());

        HttpConfig httpConfig = HttpConfig.getDefaultHttpConfig()
                .withConnectionTimeout(5000)
                .withReadTimeout(10000)
                .withTimeout(15000);

        return FrsClient.newBuilder()
                .withCredential(credentials)
                .withRegion(new Region(properties.getRegion(), properties.getFrsEndpoint()))
                .withHttpConfig(httpConfig)
                .build();
    }

    private static void requireConfigured(String value, String variableName) {
        if (isBlank(value)) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, variableName + " is required for Huawei FRS");
        }
    }

    private String faceSetName() {
        requireConfigured(properties.getFaceSetName(), "FRS_FACE_SET_NAME");
        return properties.getFaceSetName();
    }

    private static String base64(byte[] imageBytes) {
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private static FaceSetFace first(List<FaceSetFace> faces) {
        if (faces == null || faces.isEmpty()) {
            return null;
        }
        return faces.getFirst();
    }

    private static Map<String, String> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        rawMap.forEach((key, innerValue) -> {
            if (key != null && innerValue != null) {
                result.put(key.toString(), innerValue.toString());
            }
        });
        return result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RuntimeException mapException(String operation, RuntimeException exception) {
        if (exception instanceof BusinessException) {
            return exception;
        }
        if (exception instanceof HuaweiFrsException frsException) {
            return frsException;
        }
        if (exception instanceof RequestTimeoutException || exception instanceof ConnectionException) {
            return new HuaweiFrsException(
                    "FRS_TIMEOUT",
                    operation + " failed because Huawei FRS timed out or was unreachable.",
                    null,
                    true,
                    exception
            );
        }
        if (exception instanceof ServiceResponseException serviceException) {
            String code = classifyServiceException(serviceException);
            return new HuaweiFrsException(
                    code,
                    operation + " failed: " + safeMessage(serviceException),
                    serviceException.getRequestId(),
                    isRetryable(code, serviceException),
                    serviceException
            );
        }
        return new HuaweiFrsException(
                "FRS_ERROR",
                operation + " failed because Huawei FRS returned an unexpected error.",
                null,
                false,
                exception
        );
    }

    private static String classifyServiceException(ServiceResponseException exception) {
        int statusCode = exception.getHttpStatusCode();
        String combined = (exception.getErrorCode() + " " + exception.getErrorMsg()).toUpperCase();
        if (statusCode == 408 || combined.contains("TIMEOUT")) {
            return "FRS_TIMEOUT";
        }
        if (statusCode == 429 || combined.contains("RATE") || combined.contains("TOO MANY")) {
            return "FRS_RATE_LIMITED";
        }
        return "FRS_ERROR";
    }

    private static boolean isRetryable(String code, ServiceResponseException exception) {
        return "FRS_TIMEOUT".equals(code)
                || "FRS_RATE_LIMITED".equals(code)
                || exception.getHttpStatusCode() >= 500;
    }

    private static String safeMessage(ServiceResponseException exception) {
        if (!isBlank(exception.getErrorMsg())) {
            return exception.getErrorMsg();
        }
        if (!isBlank(exception.getErrorCode())) {
            return exception.getErrorCode();
        }
        return "HTTP " + exception.getHttpStatusCode();
    }

    public record DetectFaceResponse(int faceCount, String requestId) {
    }

    public record EnrollFaceResponse(String faceId, String requestId) {
    }

    public record SearchFaceResponse(List<SearchCandidate> candidates, String requestId) {
    }

    public record SearchCandidate(String faceId, double similarity, Map<String, String> externalFields) {
    }

    public record CompareFaceResponse(double similarity, String requestId) {
    }
}
