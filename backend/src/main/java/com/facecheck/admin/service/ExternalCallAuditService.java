package com.facecheck.admin.service;

import com.facecheck.admin.model.ExternalCallStatus;
import com.facecheck.admin.model.ExternalServiceCallLog;
import com.facecheck.admin.model.ExternalServiceName;
import com.facecheck.admin.repo.ExternalServiceCallLogRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExternalCallAuditService {

    private static final Logger log = LoggerFactory.getLogger(ExternalCallAuditService.class);
    private static final String DEFAULT_ENTITY_TYPE = "ATTENDANCE_CHECKIN_ATTEMPT";

    private final ExternalServiceCallLogRepository externalServiceCallLogRepository;

    public ExternalCallAuditService(ExternalServiceCallLogRepository externalServiceCallLogRepository) {
        this.externalServiceCallLogRepository = externalServiceCallLogRepository;
    }

    public void recordSuccess(String serviceName, String operation, UUID relatedEntityId, String requestId, long latencyMs) {
        ExternalServiceCallLog logEntry = new ExternalServiceCallLog();
        logEntry.setServiceName(ExternalServiceName.valueOf(serviceName));
        logEntry.setOperation(operation);
        logEntry.setRequestId(requestId);
        logEntry.setRelatedEntityType(DEFAULT_ENTITY_TYPE);
        logEntry.setRelatedEntityId(relatedEntityId);
        logEntry.setStatus(ExternalCallStatus.SUCCESS);
        logEntry.setLatencyMs(toInt(latencyMs));
        externalServiceCallLogRepository.save(logEntry);
        log.info(
                "external_call_success service={} operation={} entityId={} requestId={} latencyMs={}",
                serviceName,
                operation,
                relatedEntityId,
                requestId,
                latencyMs
        );
    }

    public void recordFailure(
            String serviceName,
            String operation,
            UUID relatedEntityId,
            String requestId,
            String resultCode,
            long latencyMs,
            Throwable exception
    ) {
        ExternalServiceCallLog logEntry = new ExternalServiceCallLog();
        logEntry.setServiceName(ExternalServiceName.valueOf(serviceName));
        logEntry.setOperation(operation);
        logEntry.setRequestId(requestId);
        logEntry.setRelatedEntityType(DEFAULT_ENTITY_TYPE);
        logEntry.setRelatedEntityId(relatedEntityId);
        logEntry.setStatus(classify(resultCode));
        logEntry.setLatencyMs(toInt(latencyMs));
        logEntry.setErrorCode(resultCode);
        logEntry.setErrorSummary(exception == null ? null : exception.getMessage());
        externalServiceCallLogRepository.save(logEntry);
        log.warn(
                "external_call_failure service={} operation={} entityId={} requestId={} resultCode={} latencyMs={}",
                serviceName,
                operation,
                relatedEntityId,
                requestId,
                resultCode,
                latencyMs,
                exception
        );
    }

    private ExternalCallStatus classify(String resultCode) {
        if ("FRS_TIMEOUT".equals(resultCode)) {
            return ExternalCallStatus.TIMEOUT;
        }
        if ("FRS_RATE_LIMITED".equals(resultCode)) {
            return ExternalCallStatus.RATE_LIMITED;
        }
        return ExternalCallStatus.FAILED;
    }

    private Integer toInt(long latencyMs) {
        return latencyMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latencyMs;
    }
}
