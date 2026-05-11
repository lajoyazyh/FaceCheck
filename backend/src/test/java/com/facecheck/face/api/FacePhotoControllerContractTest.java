package com.facecheck.face.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.auth.filter.JwtAuthenticationFilter;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.common.error.GlobalExceptionHandler;
import com.facecheck.face.api.dto.FacePhotoSummaryResponse;
import com.facecheck.face.service.FacePhotoDeletionService;
import com.facecheck.face.service.FacePhotoQueryService;
import com.facecheck.face.service.FacePhotoService;
import com.facecheck.face.service.FacePhotoViewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

class FacePhotoControllerContractTest {

    private MockMvc mockMvc;

    private StubFacePhotoService facePhotoService;

    private StubFacePhotoQueryService facePhotoQueryService;

    private StubFacePhotoDeletionService facePhotoDeletionService;

    @BeforeEach
    void setUp() {
        facePhotoService = new StubFacePhotoService();
        facePhotoQueryService = new StubFacePhotoQueryService();
        facePhotoDeletionService = new StubFacePhotoDeletionService();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FacePhotoController(facePhotoService, facePhotoQueryService, facePhotoDeletionService),
                        new AdminFacePhotoController(facePhotoService, facePhotoQueryService, facePhotoDeletionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeCurrentUserFacePhotoContracts() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        FacePhotoSummaryResponse summary = summary(photoId, userId, FacePhotoViewStatus.PENDING_REGISTER);
        facePhotoQueryService.currentUserPhotos = List.of(summary);
        facePhotoService.currentUserUploadResponse = summary;

        mockMvc.perform(get("/api/me/face-photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].photoId").value(photoId.toString()))
                .andExpect(jsonPath("$.data[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_REGISTER"))
                .andExpect(jsonPath("$.data[0].previewUrl").isString());

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "png".getBytes());
        mockMvc.perform(multipart("/api/me/face-photos").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING_REGISTER"))
                .andExpect(jsonPath("$.data.failureCode").doesNotExist());

        mockMvc.perform(delete("/api/me/face-photos/{photoId}", photoId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldExposeAdminFacePhotoContracts() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        FacePhotoSummaryResponse summary = summary(photoId, userId, FacePhotoViewStatus.ACTIVE);
        facePhotoQueryService.userPhotos = List.of(summary);
        facePhotoService.adminUploadResponse = summary;

        mockMvc.perform(get("/api/admin/users/{userId}/face-photos", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "png".getBytes());
        mockMvc.perform(multipart("/api/admin/users/{userId}/face-photos", userId).file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.photoId").value(photoId.toString()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(delete("/api/admin/users/{userId}/face-photos/{photoId}", userId, photoId))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldExposePhotoLimitRejectionContract() throws Exception {
        facePhotoService.currentUserUploadError =
                new BusinessException(ErrorCode.FACE_PHOTO_LIMIT_REACHED, "A user can keep at most five photos.");

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "png".getBytes());
        mockMvc.perform(multipart("/api/me/face-photos").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FACE_PHOTO_LIMIT_REACHED"));
    }

    private FacePhotoSummaryResponse summary(UUID photoId, UUID userId, FacePhotoViewStatus status) {
        return new FacePhotoSummaryResponse(
                photoId,
                userId,
                status,
                "PENDING",
                status == FacePhotoViewStatus.ACTIVE ? "ACTIVE" : "PENDING",
                null,
                null,
                "https://preview.example.com/" + photoId,
                true,
                Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private static final class StubFacePhotoService extends FacePhotoService {

        private FacePhotoSummaryResponse currentUserUploadResponse;
        private FacePhotoSummaryResponse adminUploadResponse;
        private RuntimeException currentUserUploadError;

        private StubFacePhotoService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public FacePhotoSummaryResponse uploadCurrentUserPhoto(Authentication authentication, MultipartFile file) {
            if (currentUserUploadError != null) {
                throw currentUserUploadError;
            }
            return currentUserUploadResponse;
        }

        @Override
        public FacePhotoSummaryResponse uploadUserPhoto(UUID userId, Authentication authentication, MultipartFile file) {
            return adminUploadResponse;
        }
    }

    private static final class StubFacePhotoQueryService extends FacePhotoQueryService {

        private List<FacePhotoSummaryResponse> currentUserPhotos = List.of();
        private List<FacePhotoSummaryResponse> userPhotos = List.of();

        private StubFacePhotoQueryService() {
            super(null, null, null);
        }

        @Override
        public List<FacePhotoSummaryResponse> listCurrentUserPhotos(Authentication authentication) {
            return currentUserPhotos;
        }

        @Override
        public List<FacePhotoSummaryResponse> listUserPhotos(UUID userId) {
            return userPhotos;
        }
    }

    private static final class StubFacePhotoDeletionService extends FacePhotoDeletionService {

        private StubFacePhotoDeletionService() {
            super(null, null, null, null, null, null, null, null);
        }

        @Override
        public void deleteCurrentUserPhoto(Authentication authentication, UUID photoId) {
        }

        @Override
        public void deleteUserPhoto(UUID userId, UUID photoId) {
        }
    }
}
