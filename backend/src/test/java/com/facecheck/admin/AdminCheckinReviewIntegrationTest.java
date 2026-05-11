package com.facecheck.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.admin.repo.AuditLogRepository;
import com.facecheck.auth.service.JwtService;
import com.facecheck.auth.service.PasswordService;
import com.facecheck.checkin.model.AttendanceCheckinAttempt;
import com.facecheck.checkin.model.AttendanceRecord;
import com.facecheck.checkin.model.AttendanceRecordSource;
import com.facecheck.checkin.model.AttendanceRecordStatus;
import com.facecheck.checkin.model.CheckinStatus;
import com.facecheck.checkin.repo.AttendanceCheckinAttemptRepository;
import com.facecheck.checkin.repo.AttendanceRecordRepository;
import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.session.model.AttendanceSession;
import com.facecheck.session.model.AttendanceSessionStatus;
import com.facecheck.session.repo.AttendanceSessionRepository;
import com.facecheck.storage.HuaweiObsStorageService;
import com.facecheck.support.RedisRabbitContainerSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCheckinReviewIntegrationTest extends RedisRabbitContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private AttendanceCheckinAttemptRepository attendanceCheckinAttemptRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private HuaweiObsStorageService storageService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private FaceRecognitionProvider faceRecognitionProvider;

    private User adminUser;
    private User matchedUser;
    private AttendanceSession session;
    private AttendanceCheckinAttempt existingSuccessAttempt;
    private AttendanceCheckinAttempt reviewAttempt;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        attendanceCheckinAttemptRepository.deleteAll();
        attendanceSessionRepository.deleteAll();
        userRepository.deleteAll();
        flushRedis();

        adminUser = saveUser("review-admin", UserRole.ADMIN);
        matchedUser = saveUser("review-user", UserRole.USER);
        session = savePublishedSession("review-session", adminUser.getId());

        existingSuccessAttempt = saveAttempt("idem-success", CheckinStatus.SUCCESS, "SUCCESS", matchedUser.getId(), 93.4);
        saveRecord(existingSuccessAttempt, AttendanceRecordSource.APP_QR_ANON, matchedUser.getId(), 93.4);
        reviewAttempt = saveAttempt("idem-review", CheckinStatus.FAILED, "MANUAL_REVIEW", null, null);
    }

    @Test
    void shouldQueryAdminRecordsReviewAttemptsAndRetrySafely() throws Exception {
        mockMvc.perform(get("/api/admin/attendance-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .param("sessionId", session.getId().toString())
                        .param("userId", matchedUser.getId().toString())
                        .param("status", "VALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].recordId").exists())
                .andExpect(jsonPath("$.data[0].sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("$.data[0].sessionName").value(session.getName()))
                .andExpect(jsonPath("$.data[0].userId").value(matchedUser.getId().toString()))
                .andExpect(jsonPath("$.data[0].maskedUsername").isString())
                .andExpect(jsonPath("$.data[0].status").value("VALID"));

        mockMvc.perform(get("/api/admin/sessions/{sessionId}/records", session.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value(session.getId().toString()))
                .andExpect(jsonPath("$.data[0].userId").value(matchedUser.getId().toString()));

        mockMvc.perform(get("/api/admin/checkin-attempts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .param("resultCode", "MANUAL_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attemptId").value(reviewAttempt.getId().toString()))
                .andExpect(jsonPath("$.data[0].resultCode").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.data[0].reviewed").value(false));

        mockMvc.perform(post("/api/admin/checkin-attempts/{attemptId}/review", reviewAttempt.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Need duplicate verification","reviewed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewNote").value("Need duplicate verification"))
                .andExpect(jsonPath("$.data.reviewed").value(true));

        given(faceRecognitionProvider.detectFace(any()))
                .willReturn(new FaceRecognitionProvider.DetectFaceResult(1, true, null, "detect-retry"));
        given(faceRecognitionProvider.searchFace(any(), anyInt()))
                .willReturn(new FaceRecognitionProvider.SearchFaceResult(
                        List.of(new FaceRecognitionProvider.SearchCandidate(
                                "frs-face-1",
                                95.0,
                                Map.of("userId", matchedUser.getId().toString())
                        )),
                        "search-retry"
                ));
        given(faceRecognitionProvider.compareFace(any(), eq("frs-face-1")))
                .willReturn(new FaceRecognitionProvider.CompareFaceResult(true, 95.0, "compare-retry"));

        mockMvc.perform(post("/api/admin/checkin-attempts/{attemptId}/retry", reviewAttempt.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.attemptId").value(reviewAttempt.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("DUPLICATE_CHECKIN"))
                .andExpect(jsonPath("$.data.resultCode").value("DUPLICATE_CHECKIN"))
                .andExpect(jsonPath("$.data.reviewed").value(true))
                .andExpect(jsonPath("$.data.retryCount").value(1));

        AttendanceCheckinAttempt refreshedAttempt = attendanceCheckinAttemptRepository.findById(reviewAttempt.getId()).orElseThrow();
        assertThat(refreshedAttempt.getReviewNote()).contains("Need duplicate verification");
        assertThat(refreshedAttempt.isReviewed()).isTrue();
        assertThat(refreshedAttempt.getRetryCount()).isEqualTo(1);
        assertThat(refreshedAttempt.getMatchedUserId()).isEqualTo(matchedUser.getId());
        assertThat(attendanceRecordRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    private User saveUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash("password123"));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private AttendanceSession savePublishedSession(String qrToken, UUID createdByUserId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AttendanceSession attendanceSession = new AttendanceSession();
        attendanceSession.setName("Review Session");
        attendanceSession.setDescription("Admin review flow");
        attendanceSession.setStartTime(now.minus(30, ChronoUnit.MINUTES));
        attendanceSession.setEndTime(now.plus(30, ChronoUnit.MINUTES));
        attendanceSession.setStatus(AttendanceSessionStatus.PUBLISHED);
        attendanceSession.setQrToken(qrToken);
        attendanceSession.setQrTokenVersion(1);
        attendanceSession.setCreatedByUserId(createdByUserId);
        return attendanceSessionRepository.save(attendanceSession);
    }

    private AttendanceCheckinAttempt saveAttempt(
            String idempotencyKey,
            CheckinStatus status,
            String resultCode,
            UUID matchedUserId,
            Double similarity
    ) throws Exception {
        UUID attemptId = UUID.randomUUID();
        String objectKey = "checkins/session/" + session.getId() + "/attempt/" + attemptId + ".png";
        HuaweiObsStorageService.StoredObject storedObject =
                storageService.upload(objectKey, pngBytes(), MediaType.IMAGE_PNG_VALUE);

        AttendanceCheckinAttempt attempt = new AttendanceCheckinAttempt();
        attempt.setId(attemptId);
        attempt.setSessionId(session.getId());
        attempt.setObsBucket(storedObject.bucket());
        attempt.setObsRegion(storedObject.region());
        attempt.setObsObjectKey(storedObject.objectKey());
        attempt.setContentType(storedObject.contentType());
        attempt.setSizeBytes(storedObject.sizeBytes());
        attempt.setSha256("sha256-" + idempotencyKey);
        attempt.setStorageProvider(storedObject.storageProvider());
        attempt.setStatus(status);
        attempt.setResultCode(resultCode);
        attempt.setFailureReason("MANUAL_REVIEW".equals(resultCode) ? "Need manual review." : null);
        attempt.setMatchedUserId(matchedUserId);
        attempt.setMatchedFaceId(matchedUserId == null ? null : "frs-face-1");
        attempt.setSimilarity(similarity);
        attempt.setIdempotencyKey(idempotencyKey);
        attempt.setClientIp("127.0.0.1");
        attempt.setDeviceId("pixel-review");
        return attendanceCheckinAttemptRepository.save(attempt);
    }

    private AttendanceRecord saveRecord(
            AttendanceCheckinAttempt attempt,
            AttendanceRecordSource source,
            UUID userId,
            Double similarity
    ) {
        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(attempt.getSessionId());
        record.setUserId(userId);
        record.setAttemptId(attempt.getId());
        record.setCheckinTime(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        record.setStatus(AttendanceRecordStatus.VALID);
        record.setSimilarity(similarity);
        record.setSource(source);
        return attendanceRecordRepository.save(record);
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
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
