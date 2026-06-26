package com.facecheck.infrastructure.huawei;

public class HuaweiFrsException extends RuntimeException {

    private final String code;
    private final String requestId;
    private final boolean retryable;

    public HuaweiFrsException(String code, String message, String requestId, boolean retryable) {
        super(message);
        this.code = code;
        this.requestId = requestId;
        this.retryable = retryable;
    }

    public HuaweiFrsException(String code, String message, String requestId, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.requestId = requestId;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public String requestId() {
        return requestId;
    }

    public boolean retryable() {
        return retryable;
    }
}
