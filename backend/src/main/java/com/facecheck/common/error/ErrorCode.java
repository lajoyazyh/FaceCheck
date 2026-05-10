package com.facecheck.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request is invalid."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request failed validation."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image is invalid."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "The username or password is incorrect."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "The user account is not active."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "The username is already in use."),
    FACE_PHOTO_LIMIT_REACHED(HttpStatus.CONFLICT, "FACE_PHOTO_LIMIT_REACHED", "A user can keep at most five photos."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "The service is temporarily unavailable."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
