package com.facecheck.face.messaging;

public class RetryableFacePhotoException extends RuntimeException {

    private final String code;

    public RetryableFacePhotoException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RetryableFacePhotoException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
