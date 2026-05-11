package com.facecheck.checkin.repo;

import com.facecheck.checkin.model.AttendanceRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AttendanceRecordRepository
        extends JpaRepository<AttendanceRecord, UUID>, JpaSpecificationExecutor<AttendanceRecord> {

    Optional<AttendanceRecord> findBySessionIdAndUserId(UUID sessionId, UUID userId);

    Optional<AttendanceRecord> findByAttemptId(UUID attemptId);
}
