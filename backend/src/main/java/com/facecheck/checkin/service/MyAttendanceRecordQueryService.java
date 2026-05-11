package com.facecheck.checkin.service;

import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.checkin.api.dto.MyAttendanceRecordResponse;
import com.facecheck.checkin.model.AttendanceRecord;
import com.facecheck.checkin.repo.AttendanceRecordRepository;
import com.facecheck.session.repo.AttendanceSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyAttendanceRecordQueryService {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final CurrentUserAccess currentUserAccess;

    public MyAttendanceRecordQueryService(
            AttendanceRecordRepository attendanceRecordRepository,
            AttendanceSessionRepository attendanceSessionRepository,
            CurrentUserAccess currentUserAccess
    ) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.currentUserAccess = currentUserAccess;
    }

    @Transactional(readOnly = true)
    public List<MyAttendanceRecordResponse> listCurrentUserRecords(Authentication authentication, Instant from, Instant to) {
        UUID userId = currentUserAccess.requireUserId(authentication);

        List<AttendanceRecord> records = attendanceRecordRepository.findAll(
                userOwnedRecord(userId).and(checkinTimeBetween(from, to)),
                Sort.by(Sort.Direction.DESC, "checkinTime")
        );

        Map<UUID, String> sessionNames = attendanceSessionRepository.findAllById(
                        records.stream().map(AttendanceRecord::getSessionId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(session -> session.getId(), session -> session.getName()));

        return records.stream()
                .map(record -> new MyAttendanceRecordResponse(
                        record.getId(),
                        record.getSessionId(),
                        sessionNames.getOrDefault(record.getSessionId(), "Unknown Session"),
                        record.getCheckinTime(),
                        record.getStatus(),
                        message(record)
                ))
                .toList();
    }

    private Specification<AttendanceRecord> userOwnedRecord(UUID userId) {
        return (root, query, builder) -> builder.equal(root.get("userId"), userId);
    }

    private Specification<AttendanceRecord> checkinTimeBetween(Instant from, Instant to) {
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

    private String message(AttendanceRecord record) {
        return switch (record.getStatus()) {
            case VALID -> "Check-in completed successfully.";
            case MANUAL_CONFIRMED -> "Check-in was manually confirmed by an administrator.";
        };
    }
}
