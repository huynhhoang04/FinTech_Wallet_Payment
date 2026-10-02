package com.fintech.wallet.ledger.repository;

import com.fintech.wallet.ledger.domain.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

	Optional<AccountBalance> findFirstByAccountId(UUID accountId);

	@Query(value = "SELECT * FROM account_balances WHERE account_id = :accountId FOR UPDATE", nativeQuery = true)
	Optional<AccountBalance> lockByAccountId(@Param("accountId") UUID accountId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			INSERT INTO account_balances (id, account_id, currency, balance, version, created_at, updated_at)
			VALUES (:id, :accountId, :currency, :delta, 0, now(), now())
			ON CONFLICT (account_id, currency)
			DO UPDATE SET balance = account_balances.balance + :delta,
			              version = account_balances.version + 1,
			              updated_at = now()
			""", nativeQuery = true)
	int applyDelta(@Param("id") UUID id, @Param("accountId") UUID accountId, @Param("currency") String currency,
			@Param("delta") long delta);
}
