package com.facecheck.auth.service;

import com.facecheck.auth.config.AuthRateLimitProperties;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.infrastructure.redis.RedisKeyFactory;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LoginRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitService.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory redisKeyFactory;
    private final AuthRateLimitProperties properties;

    public LoginRateLimitService(
            StringRedisTemplate redisTemplate,
            RedisKeyFactory redisKeyFactory,
            AuthRateLimitProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.redisKeyFactory = redisKeyFactory;
        this.properties = properties;
    }

    public void checkAllowed(String username) {
        String key = redisKeyFactory.authLoginRate(subject(username));
        try {
            String attempts = redisTemplate.opsForValue().get(key);
            if (attempts != null && Long.parseLong(attempts) >= properties.getMaxFailedAttempts()) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "Too many failed login attempts. Please try again later.");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Failed to read login rate limit for username {}", username, exception);
        }
    }

    public void recordFailure(String username) {
        String key = redisKeyFactory.authLoginRate(subject(username));
        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to record login failure for username {}", username, exception);
        }
    }

    public void clear(String username) {
        try {
            redisTemplate.delete(redisKeyFactory.authLoginRate(subject(username)));
        } catch (RuntimeException exception) {
            log.warn("Failed to clear login rate limit for username {}", username, exception);
        }
    }

    private String subject(String username) {
        if (!StringUtils.hasText(username)) {
            return "blank";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
