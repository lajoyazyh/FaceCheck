package com.facecheck.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.facecheck.admin.service.AuditLogService;
import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.session.api.dto.CreateAttendanceSessionRequest;
import com.facecheck.session.api.dto.SessionEntryResponse;
import com.facecheck.session.api.dto.UpdateAttendanceSessionRequest;
import com.facecheck.session.model.AttendanceSession;
import com.facecheck.session.model.AttendanceSessionStatus;
import com.facecheck.session.repo.AttendanceSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceSessionServiceTest {

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @Mock
    private CurrentUserAccess currentUserAccess;

    @Mock
    private QrTokenService qrTokenService;

    @Mock
    private AuditLogService auditLogService;

    private AttendanceSessionService attendanceSessionService;

    @BeforeEach
    void setUp() {
        attendanceSessionService = new AttendanceSessionService(
                attendanceSessionRepository,
                currentUserAccess,
                qrTokenService,
                auditLogService
        );
    }

    @Test
    void shouldRejectInvalidCreateTimeWindowWithStableErrorCode() {
        CreateAttendanceSessionRequest request = new CreateAttendanceSessionRequest(
                "Morning Roll Call",
                null,
                Instant.parse("2026-05-10T09:00:00Z"),
                Instant.parse("2026-05-10T08:00:00Z"),
                null
        );

        assertThatThrownBy(() -> attendanceSessionService.createSession(null, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SESSION_TIME_WINDOW);

        verifyNoInteractions(attendanceSessionRepository, currentUserAccess, qrTokenService, auditLogService);
    }

    @Test
    void shouldRejectEditingNonDraftSessionsWithStableErrorCode() {
        UUID sessionId = UUID.randomUUID();
        given(attendanceSessionRepository.findById(sessionId))
                .willReturn(Optional.of(session(sessionId, AttendanceSessionStatus.PUBLISHED)));

        UpdateAttendanceSessionRequest request = new UpdateAttendanceSessionRequest(
                "Morning Roll Call",
                null,
                Instant.parse("2026-05-10T08:00:00Z"),
                Instant.parse("2026-05-10T09:00:00Z"),
                null
        );

        assertThatThrownBy(() -> attendanceSessionService.updateSession(sessionId, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SESSION_STATUS);
    }

    @Test
    void shouldReturnPrecisePublicEntryRefusalCodes() {
        AttendanceSession futureSession = session(UUID.randomUUID(), AttendanceSessionStatus.PUBLISHED);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        futureSession.setStartTime(now.plus(10, ChronoUnit.MINUTES));
        futureSession.setEndTime(now.plus(40, ChronoUnit.MINUTES));
        given(qrTokenService.requireByToken("future-token")).willReturn(futureSession);

        SessionEntryResponse futureResponse = attendanceSessionService.resolveSessionEntry("future-token");
        assertThat(futureResponse.canCheckin()).isFalse();
        assertThat(futureResponse.refusalCode()).isEqualTo("SESSION_NOT_STARTED");

        AttendanceSession closedSession = session(UUID.randomUUID(), AttendanceSessionStatus.CLOSED);
        given(qrTokenService.requireByToken("closed-token")).willReturn(closedSession);

        SessionEntryResponse closedResponse = attendanceSessionService.resolveSessionEntry("closed-token");
        assertThat(closedResponse.canCheckin()).isFalse();
        assertThat(closedResponse.refusalCode()).isEqualTo("SESSION_CLOSED");

        AttendanceSession openSession = session(UUID.randomUUID(), AttendanceSessionStatus.PUBLISHED);
        openSession.setStartTime(now.minus(5, ChronoUnit.MINUTES));
        openSession.setEndTime(now.plus(20, ChronoUnit.MINUTES));
        given(qrTokenService.requireByToken("open-token")).willReturn(openSession);

        SessionEntryResponse openResponse = attendanceSessionService.resolveSessionEntry("open-token");
        assertThat(openResponse.canCheckin()).isTrue();
        assertThat(openResponse.refusalCode()).isNull();
    }

    private AttendanceSession session(UUID sessionId, AttendanceSessionStatus status) {
        AttendanceSession session = new AttendanceSession();
        session.setId(sessionId);
        session.setName("Morning Roll Call");
        session.setDescription("Main building lobby");
        session.setStartTime(Instant.parse("2026-05-10T08:00:00Z"));
        session.setEndTime(Instant.parse("2026-05-10T09:00:00Z"));
        session.setStatus(status);
        session.setQrToken("token-" + sessionId);
        session.setQrTokenVersion(1);
        session.setCreatedByUserId(UUID.randomUUID());
        return session;
    }
}
