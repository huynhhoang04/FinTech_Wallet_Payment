package com.fintech.wallet.txengine.repository;

import com.fintech.wallet.txengine.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

	long countBySourceAccountIdAndCreatedAtAfter(UUID sourceAccountId, Instant createdAt);

	Page<Transaction> findBySourceAccountIdOrTargetAccountId(UUID sourceAccountId, UUID targetAccountId,
			Pageable pageable);
}
