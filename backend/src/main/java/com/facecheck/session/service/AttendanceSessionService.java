package com.facecheck.session.service;

import com.facecheck.admin.service.AuditLogService;
import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.session.api.dto.AttendanceSessionSummaryResponse;
import com.facecheck.session.api.dto.CreateAttendanceSessionRequest;
import com.facecheck.session.api.dto.QrTokenResponse;
import com.facecheck.session.api.dto.SessionEntryResponse;
import com.facecheck.session.api.dto.UpdateAttendanceSessionRequest;
import com.facecheck.session.model.AttendanceSession;
import com.facecheck.session.model.AttendanceSessionStatus;
import com.facecheck.session.repo.AttendanceSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceSessionService {

    private final AttendanceSessionRepository attendanceSessionRepository;
    private final CurrentUserAccess currentUserAccess;
    private final QrTokenService qrTokenService;
    private final AuditLogService auditLogService;

    public AttendanceSessionService(
            AttendanceSessionRepository attendanceSessionRepository,
            CurrentUserAccess currentUserAccess,
            QrTokenService qrTokenService,
            AuditLogService auditLogService
    ) {
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.currentUserAccess = currentUserAccess;
        this.qrTokenService = qrTokenService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AttendanceSessionSummaryResponse> listSessions() {
        return attendanceSessionRepository.findAllByOrderByStartTimeDescCreatedAtDesc().stream()
                .map(AttendanceSessionSummaryResponse::from)
                .toList();
    }

    @Transactional
    public AttendanceSessionSummaryResponse createSession(Authentication authentication, CreateAttendanceSessionRequest request) {
        validateTimeWindow(request.startTime(), request.endTime(), request.lateAfterTime());

        AttendanceSession session = new AttendanceSession();
        session.setName(request.name().trim());
        session.setDescription(blankToNull(request.description()));
        session.setStartTime(request.startTime());
        session.setEndTime(request.endTime());
        session.setLateAfterTime(request.lateAfterTime());
        session.setStatus(AttendanceSessionStatus.DRAFT);
        session.setQrToken(qrTokenService.generateUniqueToken());
        session.setQrTokenVersion(1);
        session.setCreatedByUserId(currentUserAccess.requireUserId(authentication));

        AttendanceSession saved = attendanceSessionRepository.save(session);
        qrTokenService.cache(saved);
        auditLogService.recordCurrentActor(
                "SESSION_CREATE",
                "ATTENDANCE_SESSION",
                saved.getId(),
                "Administrator created an attendance session.",
                java.util.Map.of("status", saved.getStatus().name(), "qrTokenVersion", saved.getQrTokenVersion())
        );
        return AttendanceSessionSummaryResponse.from(saved);
    }

    @Transactional
    public AttendanceSessionSummaryResponse updateSession(UUID sessionId, UpdateAttendanceSessionRequest request) {
        validateTimeWindow(request.startTime(), request.endTime(), request.lateAfterTime());

        AttendanceSession session = load(sessionId);
        requireStatus(session, AttendanceSessionStatus.DRAFT, "Only draft sessions can be edited");
        session.setName(request.name().trim());
        session.setDescription(blankToNull(request.description()));
        session.setStartTime(request.startTime());
        session.setEndTime(request.endTime());
        session.setLateAfterTime(request.lateAfterTime());
        qrTokenService.cache(session);
        auditLogService.recordCurrentActor(
                "SESSION_UPDATE",
                "ATTENDANCE_SESSION",
                session.getId(),
                "Administrator updated an attendance session.",
                java.util.Map.of("status", session.getStatus().name())
        );
        return AttendanceSessionSummaryResponse.from(session);
    }

    @Transactional
    public AttendanceSessionSummaryResponse publishSession(UUID sessionId) {
        AttendanceSession session = load(sessionId);
        requireStatus(session, AttendanceSessionStatus.DRAFT, "Only draft sessions can be published");
        session.setStatus(AttendanceSessionStatus.PUBLISHED);
        qrTokenService.cache(session);
        auditLogService.recordCurrentActor(
                "SESSION_PUBLISH",
                "ATTENDANCE_SESSION",
                session.getId(),
                "Administrator published an attendance session.",
                java.util.Map.of("status", session.getStatus().name())
        );
        return AttendanceSessionSummaryResponse.from(session);
    }

    @Transactional
    public AttendanceSessionSummaryResponse closeSession(UUID sessionId) {
        AttendanceSession session = load(sessionId);
        requireStatus(session, AttendanceSessionStatus.PUBLISHED, "Only published sessions can be closed");
        session.setStatus(AttendanceSessionStatus.CLOSED);
        qrTokenService.cache(session);
        auditLogService.recordCurrentActor(
                "SESSION_CLOSE",
                "ATTENDANCE_SESSION",
                session.getId(),
                "Administrator closed an attendance session.",
                java.util.Map.of("status", session.getStatus().name())
        );
        return AttendanceSessionSummaryResponse.from(session);
    }

    @Transactional
    public AttendanceSessionSummaryResponse cancelSession(UUID sessionId) {
        AttendanceSession session = load(sessionId);
        if (session.getStatus() != AttendanceSessionStatus.DRAFT && session.getStatus() != AttendanceSessionStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATUS, "Only draft or published sessions can be canceled");
        }
        session.setStatus(AttendanceSessionStatus.CANCELED);
        qrTokenService.cache(session);
        auditLogService.recordCurrentActor(
                "SESSION_CANCEL",
                "ATTENDANCE_SESSION",
                session.getId(),
                "Administrator canceled an attendance session.",
                java.util.Map.of("status", session.getStatus().name())
        );
        return AttendanceSessionSummaryResponse.from(session);
    }

    @Transactional(readOnly = true)
    public QrTokenResponse currentQrToken(UUID sessionId) {
        AttendanceSession session = load(sessionId);
        return new QrTokenResponse(session.getId(), session.getQrToken(), qrTokenService.qrContent(session));
    }

    @Transactional
    public QrTokenResponse resetQrToken(UUID sessionId) {
        AttendanceSession session = load(sessionId);
        if (session.getStatus() == AttendanceSessionStatus.CLOSED || session.getStatus() == AttendanceSessionStatus.CANCELED) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATUS, "Closed or canceled sessions cannot rotate QR tokens");
        }
        qrTokenService.rotateToken(session);
        auditLogService.recordCurrentActor(
                "SESSION_QR_RESET",
                "ATTENDANCE_SESSION",
                session.getId(),
                "Administrator rotated the session QR token.",
                java.util.Map.of("qrTokenVersion", session.getQrTokenVersion())
        );
        return new QrTokenResponse(session.getId(), session.getQrToken(), qrTokenService.qrContent(session));
    }

    @Transactional(readOnly = true)
    public SessionEntryResponse resolveSessionEntry(String qrToken) {
        AttendanceSession session = qrTokenService.requireByToken(qrToken);
        Instant now = Instant.now();

        return switch (session.getStatus()) {
            case DRAFT -> SessionEntryResponse.refused(session, "SESSION_NOT_PUBLISHED", "The session has not been published yet.");
            case CLOSED -> SessionEntryResponse.refused(session, "SESSION_CLOSED", "The session has already been closed.");
            case CANCELED -> SessionEntryResponse.refused(session, "SESSION_CANCELED", "The session has been canceled.");
            case PUBLISHED -> {
                if (now.isBefore(session.getStartTime())) {
                    yield SessionEntryResponse.refused(session, "SESSION_NOT_STARTED", "The session has not started yet.");
                }
                if (now.isAfter(session.getEndTime())) {
                    yield SessionEntryResponse.refused(session, "EXPIRED_SESSION", "The session has already ended.");
                }
                yield SessionEntryResponse.available(session);
            }
        };
    }

    private AttendanceSession load(UUID sessionId) {
        return attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attendance session does not exist"));
    }

    private void requireStatus(AttendanceSession session, AttendanceSessionStatus expected, String message) {
        if (session.getStatus() != expected) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_STATUS, message);
        }
    }

    private void validateTimeWindow(Instant startTime, Instant endTime, Instant lateAfterTime) {
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_TIME_WINDOW, "startTime must be before endTime");
        }
        if (lateAfterTime != null && (lateAfterTime.isBefore(startTime) || lateAfterTime.isAfter(endTime))) {
            throw new BusinessException(ErrorCode.INVALID_SESSION_TIME_WINDOW, "lateAfterTime must be within the session window");
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
