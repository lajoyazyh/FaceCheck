package com.facecheck.checkin.repo;

import com.facecheck.checkin.model.AttendanceCheckinAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AttendanceCheckinAttemptRepository
        extends JpaRepository<AttendanceCheckinAttempt, UUID>, JpaSpecificationExecutor<AttendanceCheckinAttempt> {

    Optional<AttendanceCheckinAttempt> findBySessionIdAndIdempotencyKey(UUID sessionId, String idempotencyKey);
}
