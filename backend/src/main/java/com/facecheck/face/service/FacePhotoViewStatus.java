package com.facecheck.face.service;

import com.facecheck.face.model.FaceDetectStatus;
import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.model.FaceRegisterStatus;

public enum FacePhotoViewStatus {
    PENDING_REGISTER,
    ACTIVE,
    FAILED,
    DELETE_PENDING,
    DELETE_FAILED,
    DELETED;

    public static FacePhotoViewStatus from(FacePhoto photo) {
        if (!photo.isEnabled() || photo.getRegisterStatus() == FaceRegisterStatus.DELETED) {
            return DELETED;
        }
        if (photo.getRegisterStatus() == FaceRegisterStatus.DELETE_PENDING) {
            return DELETE_PENDING;
        }
        if (photo.getRegisterStatus() == FaceRegisterStatus.DELETE_FAILED) {
            return DELETE_FAILED;
        }
        if (photo.getRegisterStatus() == FaceRegisterStatus.ACTIVE) {
            return ACTIVE;
        }
        if (photo.getRegisterStatus() == FaceRegisterStatus.FAILED || photo.getDetectStatus() == FaceDetectStatus.FAILED) {
            return FAILED;
        }
        return PENDING_REGISTER;
    }
}
