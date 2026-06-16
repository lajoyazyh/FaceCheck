package com.facecheck.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.auth.service.JwtService;
import com.facecheck.auth.service.PasswordService;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.infrastructure.redis.RedisKeyFactory;
import com.facecheck.support.RedisRabbitContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest extends RedisRabbitContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RedisKeyFactory redisKeyFactory;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        saveUser("user-one", "password123", UserRole.USER, UserStatus.ACTIVE);
        saveUser("admin-one", "password123", UserRole.ADMIN, UserStatus.ACTIVE);
    }

    @Test
    void shouldKeepHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldLoginAsOrdinaryUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"user-one","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.user.username").value("user-one"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void shouldLoginAsAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin-one","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.user.username").value("admin-one"));
    }

    @Test
    void shouldBlacklistLoggedOutToken() throws Exception {
        User user = userRepository.findByUsername("user-one").orElseThrow();
        String token = jwtService.issue(user).accessToken();
        String jti = jwtService.parse(token).jti();

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(redisKeyFactory.authBlacklist(jti))).isTrue();

        mockMvc.perform(get("/api/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldDenyDeprecatedTestNamespaceEvenForAuthenticatedUsers() throws Exception {
        User user = userRepository.findByUsername("user-one").orElseThrow();
        String token = jwtService.issue(user).accessToken();

        mockMvc.perform(get("/api/test/ping")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private User saveUser(String username, String rawPassword, UserRole role, UserStatus status) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(rawPassword));
        user.setRole(role);
        user.setStatus(status);
        return userRepository.save(user);
    }
}
