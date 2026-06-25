package com.facecheck.auth.service;

import com.facecheck.auth.dto.LoginRequest;
import com.facecheck.auth.dto.LoginResponse;
import com.facecheck.auth.security.AuthenticatedUser;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.identity.api.dto.UserProfileResponse;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginRateLimitService loginRateLimitService;

    public AuthService(
            UserRepository userRepository,
            PasswordService passwordService,
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService,
            LoginRateLimitService loginRateLimitService
    ) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.loginRateLimitService = loginRateLimitService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        loginRateLimitService.checkAllowed(request.username());
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> invalidCredentials(request.username()));

        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials(request.username());
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        loginRateLimitService.clear(request.username());
        user.setLastLoginAt(Instant.now());
        JwtService.IssuedToken issuedToken = jwtService.issue(user);
        return new LoginResponse(
                issuedToken.accessToken(),
                "Bearer",
                issuedToken.expiresInSeconds(),
                user.getRole(),
                toProfile(user)
        );
    }

    private BusinessException invalidCredentials(String username) {
        loginRateLimitService.recordFailure(username);
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    public void logout(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        tokenBlacklistService.blacklist(authenticatedUser.jti(), authenticatedUser.expiresAt());
    }

    public UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getRole(), user.getStatus());
    }
}
