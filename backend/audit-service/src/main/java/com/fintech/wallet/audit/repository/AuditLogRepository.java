package com.fintech.wallet.audit.repository;

import com.fintech.wallet.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

	List<AuditLog> findAllByOrderByRecordedAtAsc();

	Page<AuditLog> findAllByOrderByRecordedAtDesc(Pageable pageable);

	boolean existsByEventId(UUID eventId);

	AuditLog findFirstByOrderByRecordedAtDesc();
}
