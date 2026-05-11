package com.facecheck.admin.service;

import com.facecheck.admin.api.dto.AdminAttendanceRecordResponse;
import com.facecheck.checkin.model.AttendanceRecord;
import com.facecheck.checkin.model.AttendanceRecordStatus;
import com.facecheck.checkin.repo.AttendanceRecordRepository;
import com.facecheck.identity.model.User;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.session.repo.AttendanceSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAttendanceRecordQueryService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final UserRepository userRepository;

    public AdminAttendanceRecordQueryService(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceSessionRepository attendanceSessionRepository,
            UserRepository userRepository
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminAttendanceRecordResponse> listRecords(
            UUID sessionId,
            UUID userId,
            Instant from,
            Instant to,
            AttendanceRecordStatus status
    ) {
        List<AttendanceRecord> records = attendanceRecordRepository.findAll(
                bySessionId(sessionId)
                        .and(byUserId(userId))
                        .and(byCheckinTime(from, to))
                        .and(byStatus(status)),
                Sort.by(Sort.Direction.DESC, "checkinTime")
        );

        Map<UUID, String> sessionNames = attendanceSessionRepository.findAllById(
                        records.stream().map(AttendanceRecord::getSessionId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(session -> session.getId(), session -> session.getName()));

        Map<UUID, User> users = userRepository.findAllById(records.stream().map(AttendanceRecord::getUserId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, java.util.function.Function.identity()));

        return records.stream()
                .map(record -> new AdminAttendanceRecordResponse(
                        record.getId(),
                        record.getSessionId(),
                        sessionNames.getOrDefault(record.getSessionId(), "Unknown Session"),
                        record.getUserId(),
                        mask(users.get(record.getUserId())),
                        record.getCheckinTime(),
                        record.getStatus(),
                        record.getSimilarity()
                ))
                .toList();
    }

    private Specification<AttendanceRecord> bySessionId(UUID sessionId) {
        return (root, query, builder) -> sessionId == null
                ? builder.conjunction()
                : builder.equal(root.get("sessionId"), sessionId);
    }

    private Specification<AttendanceRecord> byUserId(UUID userId) {
        return (root, query, builder) -> userId == null
                ? builder.conjunction()
                : builder.equal(root.get("userId"), userId);
    }

    private Specification<AttendanceRecord> byStatus(AttendanceRecordStatus status) {
        return (root, query, builder) -> status == null
                ? builder.conjunction()
                : builder.equal(root.get("status"), status);
    }

    private Specification<AttendanceRecord> byCheckinTime(Instant from, Instant to) {
        return (root, query, builder) -> {
            if (from != null && to != null) {
                return builder.between(root.get("checkinTime"), from, to);
            }
            if (from != null) {
                return builder.greaterThanOrEqualTo(root.get("checkinTime"), from);
            }
            if (to != null) {
                return builder.lessThanOrEqualTo(root.get("checkinTime"), to);
            }
            return builder.conjunction();
        };
    }

    private String mask(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return null;
        }
        String username = user.getUsername();
        if (username.length() <= 2) {
            return username.charAt(0) + "*";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
