package com.fintech.wallet.txengine.repository;

import com.fintech.wallet.txengine.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

	Optional<Transaction> findByPaymentIntentId(UUID paymentIntentId);

	/**
	 * Only settled traffic counts towards the velocity rule: a blocked or abandoned attempt must not
	 * push the next legitimate transfer over the threshold (docs 07 FIN-10).
	 */
	@Query("SELECT COUNT(t) FROM Transaction t WHERE t.sourceAccountId = :sourceAccountId"
			+ " AND t.status IN (com.fintech.wallet.txengine.domain.TransactionStatus.SUCCESS,"
			+ " com.fintech.wallet.txengine.domain.TransactionStatus.PROCESSING)"
			+ " AND t.createdAt > :since")
	long countSettledBySourceAccountIdAfter(@Param("sourceAccountId") UUID sourceAccountId,
			@Param("since") Instant since);

	Page<Transaction> findBySourceAccountIdOrTargetAccountId(UUID sourceAccountId, UUID targetAccountId,
			Pageable pageable);
}
