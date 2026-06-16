package com.facecheck.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.auth.service.JwtService;
import com.facecheck.common.api.ApiResponse;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.support.RedisRabbitContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserSelfAccessIntegrationTest.SelfAccessTestController.class)
class UserSelfAccessIntegrationTest extends RedisRabbitContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.facecheck.auth.service.PasswordService passwordService;

    private User owner;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        owner = saveUser("owner", "password123", UserRole.USER);
        anotherUser = saveUser("another", "password123", UserRole.USER);
    }

    @Test
    void shouldAllowCurrentUserToReachOwnData() throws Exception {
        String token = jwtService.issue(owner).accessToken();
        mockMvc.perform(get("/api/me/test/users/{userId}/self-access", owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyAccessToAnotherUsersData() throws Exception {
        String token = jwtService.issue(owner).accessToken();
        mockMvc.perform(get("/api/me/test/users/{userId}/self-access", anotherUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private User saveUser(String username, String rawPassword, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @RestController
    static class SelfAccessTestController {

        @GetMapping("/api/me/test/users/{userId}/self-access")
        @PreAuthorize("@currentUserAccess.matches(authentication, #userId)")
        ApiResponse<String> selfAccess(@PathVariable UUID userId) {
            return ApiResponse.success(userId.toString());
        }
    }
}
