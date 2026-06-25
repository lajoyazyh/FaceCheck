package com.facecheck.infrastructure.huawei;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.huaweicloud.sdk.core.exception.ServiceResponseException;
import com.huaweicloud.sdk.frs.v2.FrsClient;
import com.huaweicloud.sdk.frs.v2.model.AddFacesByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.AddFacesByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.DeleteFaceByExternalImageIdRequest;
import com.huaweicloud.sdk.frs.v2.model.DeleteFaceByExternalImageIdResponse;
import com.huaweicloud.sdk.frs.v2.model.DetectFace;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.DetectFaceByBase64Response;
import com.huaweicloud.sdk.frs.v2.model.FaceSetFace;
import com.huaweicloud.sdk.frs.v2.model.SearchFace;
import com.huaweicloud.sdk.frs.v2.model.SearchFaceByBase64Request;
import com.huaweicloud.sdk.frs.v2.model.SearchFaceByBase64Response;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HuaweiFrsClientTest {

    private final HuaweiCloudProperties properties = properties();

    @Test
    void shouldCallDetectByBase64AndMapFaceCount() {
        StubFrsClient sdk = new StubFrsClient();
        sdk.detectResponse = new DetectFaceByBase64Response()
                .withFaces(List.of(new DetectFace(), new DetectFace()))
                .withXRequestId("detect-req");

        HuaweiFrsClient client = new HuaweiFrsClient(properties, sdk);

        HuaweiFrsClient.DetectFaceResponse response = client.detect(new byte[]{1, 2, 3});

        assertThat(response.faceCount()).isEqualTo(2);
        assertThat(response.requestId()).isEqualTo("detect-req");
        assertThat(sdk.detectRequest.getBody().getImageBase64())
                .isEqualTo(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
    }

    @Test
    void shouldEnrollFaceAndReturnFaceId() {
        StubFrsClient sdk = new StubFrsClient();
        sdk.addResponse = new AddFacesByBase64Response()
                .withFaces(List.of(new FaceSetFace().withFaceId("face-1")))
                .withXRequestId("add-req");

        HuaweiFrsClient client = new HuaweiFrsClient(properties, sdk);

        HuaweiFrsClient.EnrollFaceResponse response =
                client.enroll("photo-1", Map.of("userId", "user-1"), new byte[]{9});

        assertThat(response.faceId()).isEqualTo("face-1");
        assertThat(response.requestId()).isEqualTo("add-req");
        assertThat(sdk.addRequest.getFaceSetName()).isEqualTo("facecheck-default");
        assertThat(sdk.addRequest.getBody().getExternalImageId()).isEqualTo("photo-1");
        assertThat(sdk.addRequest.getBody().getExternalFields()).isEqualTo(Map.of("userId", "user-1"));
        assertThat(sdk.addRequest.getBody().getSingle()).isTrue();
    }

    @Test
    void shouldSearchAndMapCandidatesWithExternalFields() {
        StubFrsClient sdk = new StubFrsClient();
        sdk.searchResponse = new SearchFaceByBase64Response()
                .withFaces(List.of(new SearchFace()
                        .withFaceId("face-1")
                        .withSimilarity(91.5)
                        .withExternalFields(Map.of("userId", "user-1", "photoId", "photo-1"))))
                .withXRequestId("search-req");

        HuaweiFrsClient client = new HuaweiFrsClient(properties, sdk);

        HuaweiFrsClient.SearchFaceResponse response = client.search(new byte[]{5}, 3);

        assertThat(response.requestId()).isEqualTo("search-req");
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().faceId()).isEqualTo("face-1");
        assertThat(response.candidates().getFirst().similarity()).isEqualTo(91.5);
        assertThat(response.candidates().getFirst().externalFields()).containsEntry("userId", "user-1");
        assertThat(sdk.searchRequest.getFaceSetName()).isEqualTo("facecheck-default");
        assertThat(sdk.searchRequest.getBody().getTopN()).isEqualTo(3);
        assertThat(sdk.searchRequest.getBody().getReturnFields()).containsExactly("userId", "photoId");
    }

    @Test
    void shouldConfirmCompareBySearchingForRequestedFaceId() {
        StubFrsClient sdk = new StubFrsClient();
        sdk.searchResponse = new SearchFaceByBase64Response()
                .withFaces(List.of(
                        new SearchFace().withFaceId("other-face").withSimilarity(80.0),
                        new SearchFace().withFaceId("target-face").withSimilarity(93.2)
                ))
                .withXRequestId("compare-search-req");

        HuaweiFrsClient client = new HuaweiFrsClient(properties, sdk);

        HuaweiFrsClient.CompareFaceResponse response = client.compare(new byte[]{7}, "target-face");

        assertThat(response.similarity()).isEqualTo(93.2);
        assertThat(response.requestId()).isEqualTo("compare-search-req");
        assertThat(sdk.searchRequest.getBody().getTopN()).isEqualTo(10);
    }

    @Test
    void shouldDeleteByExternalImageId() {
        StubFrsClient sdk = new StubFrsClient();
        sdk.deleteResponse = new DeleteFaceByExternalImageIdResponse().withXRequestId("delete-req");

        HuaweiFrsClient client = new HuaweiFrsClient(properties, sdk);

        client.deleteByExternalImageId("photo-1");

        assertThat(sdk.deleteRequest.getFaceSetName()).isEqualTo("facecheck-default");
        assertThat(sdk.deleteRequest.getExternalImageId()).isEqualTo("photo-1");
    }

    @Test
    void shouldMapRateLimitException() {
        StubFrsClient sdk = new StubFrsClient();
        sdk.detectException = new ServiceResponseException(429, "FRS.429", "Too Many Requests", "rate-req");
        HuaweiFrsClient client = new HuaweiFrsClient(properties, sdk);

        assertThatThrownBy(() -> client.detect(new byte[]{1}))
                .isInstanceOfSatisfying(HuaweiFrsException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("FRS_RATE_LIMITED");
                    assertThat(exception.requestId()).isEqualTo("rate-req");
                    assertThat(exception.retryable()).isTrue();
                });
    }

    private static HuaweiCloudProperties properties() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setFaceSetName("facecheck-default");
        return properties;
    }

    private static final class StubFrsClient extends FrsClient {

        private DetectFaceByBase64Request detectRequest;
        private DetectFaceByBase64Response detectResponse;
        private RuntimeException detectException;
        private AddFacesByBase64Request addRequest;
        private AddFacesByBase64Response addResponse;
        private SearchFaceByBase64Request searchRequest;
        private SearchFaceByBase64Response searchResponse;
        private DeleteFaceByExternalImageIdRequest deleteRequest;
        private DeleteFaceByExternalImageIdResponse deleteResponse;

        private StubFrsClient() {
            super(null);
        }

        @Override
        public DetectFaceByBase64Response detectFaceByBase64(DetectFaceByBase64Request request) {
            this.detectRequest = request;
            if (detectException != null) {
                throw detectException;
            }
            return detectResponse;
        }

        @Override
        public AddFacesByBase64Response addFacesByBase64(AddFacesByBase64Request request) {
            this.addRequest = request;
            return addResponse;
        }

        @Override
        public SearchFaceByBase64Response searchFaceByBase64(SearchFaceByBase64Request request) {
            this.searchRequest = request;
            return searchResponse;
        }

        @Override
        public DeleteFaceByExternalImageIdResponse deleteFaceByExternalImageId(DeleteFaceByExternalImageIdRequest request) {
            this.deleteRequest = request;
            return deleteResponse;
        }
    }
}
