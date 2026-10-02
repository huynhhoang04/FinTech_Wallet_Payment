package com.fintech.wallet.ledger.repository;

import com.fintech.wallet.ledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByAccountNumber(String accountNumber);

	boolean existsByAccountNumber(String accountNumber);

	@Query(value = "SELECT * FROM accounts WHERE id = :id FOR UPDATE", nativeQuery = true)
	Optional<Account> lockById(@Param("id") UUID id);
}
