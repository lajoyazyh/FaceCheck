package com.facecheck.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.admin.model.ExternalCallStatus;
import com.facecheck.admin.model.ExternalServiceName;
import com.facecheck.admin.repo.AuditLogRepository;
import com.facecheck.admin.repo.ExternalServiceCallLogRepository;
import com.facecheck.auth.service.JwtService;
import com.facecheck.auth.service.PasswordService;
import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.session.model.AttendanceSession;
import com.facecheck.session.model.AttendanceSessionStatus;
import com.facecheck.session.repo.AttendanceSessionRepository;
import com.facecheck.support.RedisRabbitContainerSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogIntegrationTest extends RedisRabbitContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ExternalServiceCallLogRepository externalServiceCallLogRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private FaceRecognitionProvider faceRecognitionProvider;

    private User adminUser;
    private User matchedUser;
    private AttendanceSession session;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        externalServiceCallLogRepository.deleteAll();
        attendanceSessionRepository.deleteAll();
        userRepository.deleteAll();
        flushRedis();

        adminUser = saveUser("audit-admin", UserRole.ADMIN);
        matchedUser = saveUser("audit-user", UserRole.USER);
        session = savePublishedSession();
    }

    @Test
    void shouldPersistAuditRowsForAdminMutations() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String createResponse = mockMvc.perform(post("/api/admin/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Audited Session",
                                  "description":"Audit trail",
                                  "startTime":"%s",
                                  "endTime":"%s"
                                }
                                """.formatted(now.minus(5, ChronoUnit.MINUTES), now.plus(55, ChronoUnit.MINUTES))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.data.sessionId");

        mockMvc.perform(post("/api/admin/sessions/{sessionId}/publish", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/system/config/{configKey}", "checkin.rate-limit-max-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"configValue":"9"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configValue").value("9"));

        assertThat(auditLogRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(auditLogRepository.findAll().stream().map(log -> log.getAction()).toList())
                .contains("SESSION_CREATE", "SESSION_PUBLISH", "SYSTEM_CONFIG_UPDATE");
    }

    @Test
    void shouldPersistExternalServiceCallLogsDuringRecognition() throws Exception {
        given(faceRecognitionProvider.detectFace(any()))
                .willReturn(new FaceRecognitionProvider.DetectFaceResult(1, true, null, "detect-audit"));
        given(faceRecognitionProvider.searchFace(any(), anyInt()))
                .willReturn(new FaceRecognitionProvider.SearchFaceResult(
                        List.of(new FaceRecognitionProvider.SearchCandidate(
                                "frs-face-audit",
                                94.0,
                                Map.of("userId", matchedUser.getId().toString())
                        )),
                        "search-audit"
                ));
        given(faceRecognitionProvider.compareFace(any(), eq("frs-face-audit")))
                .willReturn(new FaceRecognitionProvider.CompareFaceResult(true, 94.0, "compare-audit"));

        mockMvc.perform(multipart("/api/public/checkin/attempts")
                        .file(pngFile("audit-checkin.png"))
                        .param("qrToken", session.getQrToken())
                        .param("idempotencyKey", "audit-idem")
                        .param("deviceId", "pixel-audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        assertThat(externalServiceCallLogRepository.findAll()).hasSize(3);
        assertThat(externalServiceCallLogRepository.findAll().stream()
                        .map(log -> log.getServiceName()))
                .allMatch(name -> name == ExternalServiceName.HUAWEI_FRS);
        assertThat(externalServiceCallLogRepository.findAll().stream()
                        .map(log -> log.getStatus()))
                .allMatch(statusValue -> statusValue == ExternalCallStatus.SUCCESS);
    }

    private User saveUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash("password123"));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private AttendanceSession savePublishedSession() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AttendanceSession attendanceSession = new AttendanceSession();
        attendanceSession.setName("Audit Session");
        attendanceSession.setDescription("Recognition audit");
        attendanceSession.setStartTime(now.minus(20, ChronoUnit.MINUTES));
        attendanceSession.setEndTime(now.plus(20, ChronoUnit.MINUTES));
        attendanceSession.setStatus(AttendanceSessionStatus.PUBLISHED);
        attendanceSession.setQrToken("audit-qr-token");
        attendanceSession.setQrTokenVersion(1);
        attendanceSession.setCreatedByUserId(adminUser.getId());
        return attendanceSessionRepository.save(attendanceSession);
    }

    private MockMultipartFile pngFile(String name) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return new MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, outputStream.toByteArray());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).accessToken();
    }

    private void flushRedis() {
        RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection();
        try {
            connection.serverCommands().flushAll();
        } finally {
            connection.close();
        }
    }
}
