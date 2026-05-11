package com.facecheck.admin.repo;

import com.facecheck.admin.model.ExternalServiceCallLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalServiceCallLogRepository extends JpaRepository<ExternalServiceCallLog, UUID> {

    List<ExternalServiceCallLog> findAllByOrderByCreatedAtDesc();
}
