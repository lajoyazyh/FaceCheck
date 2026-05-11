package com.facecheck.admin.service;

import com.facecheck.admin.model.AuditLog;
import com.facecheck.admin.repo.AuditLogRepository;
import com.facecheck.auth.security.AuthenticatedUser;
import com.facecheck.auth.security.CurrentUserAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserAccess currentUserAccess;
    private final ObjectMapper objectMapper;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            CurrentUserAccess currentUserAccess,
            ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserAccess = currentUserAccess;
        this.objectMapper = objectMapper;
    }

    public void recordCurrentActor(
            String action,
            String targetType,
            UUID targetId,
            String summary,
            Map<String, Object> detail
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser)) {
            return;
        }
        record(authentication, action, targetType, targetId, summary, detail);
    }

    public void record(
            Authentication authentication,
            String action,
            String targetType,
            UUID targetId,
            String summary,
            Map<String, Object> detail
    ) {
        AuthenticatedUser actor = currentUserAccess.require(authentication);

        AuditLog auditLog = new AuditLog();
        auditLog.setActorUserId(actor.userId());
        auditLog.setActorRole(actor.role().name());
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setSummary(summary);
        auditLog.setDetailJson(toJson(detail));
        auditLogRepository.save(auditLog);
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize audit log detail", exception);
        }
    }
}
