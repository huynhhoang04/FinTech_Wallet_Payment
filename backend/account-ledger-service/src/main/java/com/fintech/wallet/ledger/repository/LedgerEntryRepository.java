package com.fintech.wallet.ledger.repository;

import com.fintech.wallet.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	List<LedgerEntry> findByTransactionId(UUID transactionId);

	Page<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(UUID accountId, Pageable pageable);

	Page<LedgerEntry> findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
			UUID accountId, Instant from, Instant to, Pageable pageable);

	@Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.transactionId = :transactionId")
	Long sumAmountByTransactionId(@Param("transactionId") UUID transactionId);

	@Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.accountId = :accountId AND e.createdAt < :at")
	Long sumAmountBefore(@Param("accountId") UUID accountId, @Param("at") Instant at);

	@Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.accountId = :accountId")
	Long sumAmountByAccountId(@Param("accountId") UUID accountId);

	@Query("SELECT SUM(e.amount) FROM LedgerEntry e")
	Long sumAmount();

	@Query("SELECT COUNT(DISTINCT e.transactionId) FROM LedgerEntry e")
	long countTransactions();

	@Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.amount < 0 AND e.createdAt >= :from AND e.createdAt < :to")
	Long sumDebitsBetween(@Param("from") Instant from, @Param("to") Instant to);

	@Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.amount > 0 AND e.createdAt >= :from AND e.createdAt < :to")
	Long sumCreditsBetween(@Param("from") Instant from, @Param("to") Instant to);

	@Query(value = "SELECT transaction_id FROM ledger_entries GROUP BY transaction_id HAVING SUM(amount) <> 0",
			nativeQuery = true)
	List<UUID> findUnbalancedTransactionIds();

	default long sumByTransactionId(UUID transactionId) {
		return orZero(sumAmountByTransactionId(transactionId));
	}

	default long sumByAccountId(UUID accountId) {
		return orZero(sumAmountByAccountId(accountId));
	}

	default long sumBefore(UUID accountId, Instant at) {
		return orZero(sumAmountBefore(accountId, at));
	}

	default long totalAmount() {
		return orZero(sumAmount());
	}

	default long totalDebitsBetween(Instant from, Instant to) {
		return orZero(sumDebitsBetween(from, to));
	}

	default long totalCreditsBetween(Instant from, Instant to) {
		return orZero(sumCreditsBetween(from, to));
	}

	private static long orZero(Long value) {
		return value == null ? 0L : value;
	}
}
