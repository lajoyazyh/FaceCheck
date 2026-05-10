package com.facecheck.storage;

import com.facecheck.common.error.BusinessException;
import com.facecheck.face.model.FacePhoto;
import org.springframework.stereotype.Service;

@Service
public class PreviewUrlService {

    private final HuaweiObsStorageService storageService;

    public PreviewUrlService(HuaweiObsStorageService storageService) {
        this.storageService = storageService;
    }

    public String previewUrl(FacePhoto photo) {
        try {
            return storageService.generatePreviewUrl(photo.getObsObjectKey()).toString();
        } catch (BusinessException exception) {
            return null;
        }
    }
}
