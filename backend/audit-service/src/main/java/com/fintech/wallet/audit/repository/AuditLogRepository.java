package com.fintech.wallet.audit.repository;

import com.fintech.wallet.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

	List<AuditLog> findAllByOrderByRecordedAtAsc();

	Page<AuditLog> findAllByOrderByRecordedAtDesc(Pageable pageable);

	boolean existsByEventId(UUID eventId);

	AuditLog findFirstByOrderByRecordedAtDesc();

	/**
	 * Transaction scoped advisory lock. Two consumers appending at once would read the same prevHash
	 * and fork the chain permanently, and the rows are immutable so it could never be repaired
	 * (docs 07 SEC-08). The lock is released when the transaction commits.
	 */
	@Query(value = "SELECT pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
	void lockChain(@Param("lockKey") long lockKey);
}
