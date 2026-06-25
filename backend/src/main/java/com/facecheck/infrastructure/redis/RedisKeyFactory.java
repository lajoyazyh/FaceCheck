package com.facecheck.infrastructure.redis;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RedisKeyFactory {

    public String authBlacklist(String jti) {
        return "auth:blacklist:" + jti;
    }

    public String authLoginRate(String subject) {
        return "auth:login:rate:" + subject;
    }

    public String checkinIdempotency(String scope, String idempotencyKey) {
        return "checkin:idempotency:" + scope + ":" + idempotencyKey;
    }

    public String checkinRate(UUID sessionId, String subject) {
        return "checkin:rate:" + sessionId + ":" + subject;
    }

    public String sessionQr(String qrToken) {
        return "session:qr:" + qrToken;
    }
}
