package com.facecheck.face.api;

import com.facecheck.auth.security.AdminOnly;
import com.facecheck.common.api.ApiResponse;
import com.facecheck.face.api.dto.FacePhotoSummaryResponse;
import com.facecheck.face.service.FacePhotoDeletionService;
import com.facecheck.face.service.FacePhotoQueryService;
import com.facecheck.face.service.FacePhotoService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@AdminOnly
@RequestMapping("/api/admin/users/{userId}/face-photos")
public class AdminFacePhotoController {

    private final FacePhotoService facePhotoService;
    private final FacePhotoQueryService facePhotoQueryService;
    private final FacePhotoDeletionService facePhotoDeletionService;

    public AdminFacePhotoController(
            FacePhotoService facePhotoService,
            FacePhotoQueryService facePhotoQueryService,
            FacePhotoDeletionService facePhotoDeletionService
    ) {
        this.facePhotoService = facePhotoService;
        this.facePhotoQueryService = facePhotoQueryService;
        this.facePhotoDeletionService = facePhotoDeletionService;
    }

    @GetMapping
    public ApiResponse<List<FacePhotoSummaryResponse>> list(@PathVariable UUID userId) {
        return ApiResponse.success(facePhotoQueryService.listUserPhotos(userId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FacePhotoSummaryResponse>> upload(
            @PathVariable UUID userId,
            Authentication authentication,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.accepted().body(ApiResponse.success(facePhotoService.uploadUserPhoto(userId, authentication, file)));
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(@PathVariable UUID userId, @PathVariable UUID photoId) {
        facePhotoDeletionService.deleteUserPhoto(userId, photoId);
        return ResponseEntity.noContent().build();
    }
}
