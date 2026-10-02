package com.fintech.wallet.ledger.support;

import com.fintech.wallet.ledger.domain.Account;
import com.fintech.wallet.ledger.domain.AccountBalance;
import com.fintech.wallet.ledger.domain.AccountStatus;
import com.fintech.wallet.ledger.domain.AccountType;
import com.fintech.wallet.ledger.domain.SystemAccounts;
import com.fintech.wallet.ledger.repository.AccountBalanceRepository;
import com.fintech.wallet.ledger.repository.AccountRepository;
import com.fintech.wallet.ledger.repository.LedgerEntryRepository;
import com.fintech.wallet.ledger.service.LedgerPostingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractLedgerIT {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	protected LedgerPostingService postingService;

	@Autowired
	protected LedgerEntryRepository entryRepository;

	@Autowired
	protected AccountRepository accountRepository;

	@Autowired
	protected AccountBalanceRepository balanceRepository;

	protected UUID seedUserAccount(long initialBalance) {
		Account account = new Account();
		account.setType(AccountType.USER);
		account.setCurrency("VND");
		account.setStatus(AccountStatus.ACTIVE);
		account.setAccountNumber(
				"9704" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L)));
		accountRepository.save(account);
		if (initialBalance > 0) {
			postingService.deposit(UUID.randomUUID(), account.getId(), initialBalance, "VND", "SEED");
		}
		return account.getId();
	}

	protected long balanceOf(UUID accountId) {
		return balanceRepository.findFirstByAccountId(accountId)
				.map(AccountBalance::getBalance)
				.orElse(0L);
	}

	protected UUID suspenseAccountId() {
		return accountRepository.findByAccountNumber(SystemAccounts.SUSPENSE_NUMBER).orElseThrow().getId();
	}
}
