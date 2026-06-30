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
    INVALID_SESSION_TIME_WINDOW(HttpStatus.BAD_REQUEST, "INVALID_SESSION_TIME_WINDOW", "The attendance session time window is invalid."),
    INVALID_SESSION_STATUS(HttpStatus.BAD_REQUEST, "INVALID_SESSION_STATUS", "The requested attendance session transition is invalid."),
    SESSION_NOT_PUBLISHED(HttpStatus.BAD_REQUEST, "SESSION_NOT_PUBLISHED", "The attendance session has not been published."),
    SESSION_NOT_STARTED(HttpStatus.BAD_REQUEST, "SESSION_NOT_STARTED", "The attendance session has not started."),
    EXPIRED_SESSION(HttpStatus.BAD_REQUEST, "EXPIRED_SESSION", "The attendance session has expired."),
    SESSION_CLOSED(HttpStatus.BAD_REQUEST, "SESSION_CLOSED", "The attendance session has already been closed."),
    SESSION_CANCELED(HttpStatus.BAD_REQUEST, "SESSION_CANCELED", "The attendance session has been canceled."),
    INVALID_QR_TOKEN(HttpStatus.NOT_FOUND, "INVALID_QR_TOKEN", "The QR token is invalid or expired."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "The request was rate-limited."),
    SESSION_QR_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "SESSION_QR_GENERATION_FAILED", "Failed to generate a QR token."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource does not exist."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The HTTP method is not allowed for this resource."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "The request content type is not supported."),
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
