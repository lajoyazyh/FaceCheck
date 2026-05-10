package com.facecheck.face.service;

import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.face.api.dto.FacePhotoSummaryResponse;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.storage.PreviewUrlService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacePhotoQueryService {

    private final FacePhotoRepository facePhotoRepository;
    private final CurrentUserAccess currentUserAccess;
    private final PreviewUrlService previewUrlService;

    public FacePhotoQueryService(
            FacePhotoRepository facePhotoRepository,
            CurrentUserAccess currentUserAccess,
            PreviewUrlService previewUrlService
    ) {
        this.facePhotoRepository = facePhotoRepository;
        this.currentUserAccess = currentUserAccess;
        this.previewUrlService = previewUrlService;
    }

    @Transactional(readOnly = true)
    public List<FacePhotoSummaryResponse> listCurrentUserPhotos(Authentication authentication) {
        return listUserPhotos(currentUserAccess.requireUserId(authentication));
    }

    @Transactional(readOnly = true)
    public List<FacePhotoSummaryResponse> listUserPhotos(UUID userId) {
        return facePhotoRepository.findAllByUserIdAndEnabledTrueOrderByCreatedAtDesc(userId).stream()
                .map(photo -> FacePhotoSummaryResponse.from(photo, previewUrlService.previewUrl(photo)))
                .toList();
    }
}
