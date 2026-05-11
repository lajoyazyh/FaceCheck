package com.facecheck.checkin.service;

import com.facecheck.checkin.model.AttendanceRecord;
import com.facecheck.checkin.model.AttendanceRecordSource;
import com.facecheck.checkin.model.AttendanceRecordStatus;
import com.facecheck.checkin.repo.AttendanceRecordRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceRecordService {

    private final AttendanceRecordRepository attendanceRecordRepository;

    public AttendanceRecordService(AttendanceRecordRepository attendanceRecordRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Transactional
    public RecordOutcome persist(UUID sessionId, UUID userId, UUID attemptId, Double similarity) {
        return persist(sessionId, userId, attemptId, similarity, AttendanceRecordSource.APP_QR_ANON);
    }

    @Transactional
    public RecordOutcome persist(
            UUID sessionId,
            UUID userId,
            UUID attemptId,
            Double similarity,
            AttendanceRecordSource source
    ) {
        Optional<AttendanceRecord> existing = attendanceRecordRepository.findBySessionIdAndUserId(sessionId, userId);
        if (existing.isPresent()) {
            return new RecordOutcome(false, existing.get());
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setAttemptId(attemptId);
        record.setCheckinTime(Instant.now());
        record.setStatus(AttendanceRecordStatus.VALID);
        record.setSimilarity(similarity);
        record.setSource(source);

        try {
            return new RecordOutcome(true, attendanceRecordRepository.saveAndFlush(record));
        } catch (DataIntegrityViolationException exception) {
            return new RecordOutcome(false, null);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceRecord> findByAttemptId(UUID attemptId) {
        return attendanceRecordRepository.findByAttemptId(attemptId);
    }

    @Transactional(readOnly = true)
    public Optional<AttendanceRecord> findBySessionAndUser(UUID sessionId, UUID userId) {
        return attendanceRecordRepository.findBySessionIdAndUserId(sessionId, userId);
    }

    public record RecordOutcome(boolean created, AttendanceRecord record) {
    }
}
