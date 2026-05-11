package com.facecheck.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.admin.repo.SystemConfigRepository;
import com.facecheck.auth.service.JwtService;
import com.facecheck.auth.service.PasswordService;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
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
class SystemConfigIntegrationTest extends RedisRabbitContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        systemConfigRepository.deleteAll();
        userRepository.deleteAll();
        adminUser = saveUser("config-admin", UserRole.ADMIN);
    }

    @Test
    void shouldPersistWhitelistedConfigUpdates() throws Exception {
        mockMvc.perform(get("/api/admin/system/config")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].configKey").exists());

        mockMvc.perform(put("/api/admin/system/config/{configKey}", "frs.similarity-threshold")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configValue":"88.5"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configKey").value("frs.similarity-threshold"))
                .andExpect(jsonPath("$.data.configValue").value("88.5"));

        mockMvc.perform(get("/api/admin/system/config")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.configKey=='frs.similarity-threshold')].configValue").value("88.5"));

        assertThat(systemConfigRepository.findByConfigKey("frs.similarity-threshold"))
                .isPresent()
                .get()
                .extracting(config -> config.getUpdatedBy())
                .isEqualTo(adminUser.getId());
    }

    @Test
    void shouldExposeSystemStateSummary() throws Exception {
        mockMvc.perform(get("/api/admin/system/state")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.database").exists())
                .andExpect(jsonPath("$.data.redis").exists())
                .andExpect(jsonPath("$.data.rabbitmq").exists())
                .andExpect(jsonPath("$.data.frs").exists())
                .andExpect(jsonPath("$.data.obs").exists())
                .andExpect(jsonPath("$.data.checkedAt").exists());
    }

    private User saveUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash("password123"));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).accessToken();
    }
}
