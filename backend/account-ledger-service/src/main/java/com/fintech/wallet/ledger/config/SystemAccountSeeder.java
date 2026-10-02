package com.fintech.wallet.ledger.config;

import com.fintech.wallet.ledger.domain.Account;
import com.fintech.wallet.ledger.domain.AccountStatus;
import com.fintech.wallet.ledger.domain.AccountType;
import com.fintech.wallet.ledger.domain.SystemAccounts;
import com.fintech.wallet.ledger.repository.AccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SystemAccountSeeder implements ApplicationRunner {

	private static final String CURRENCY_VND = "VND";

	private static final List<SystemAccount> ACCOUNTS = List.of(
			new SystemAccount(SystemAccounts.SYSTEM_MASTER_NUMBER, AccountType.SYSTEM),
			new SystemAccount(SystemAccounts.FEE_INCOME_NUMBER, AccountType.FEE),
			new SystemAccount(SystemAccounts.SUSPENSE_NUMBER, AccountType.SUSPENSE));

	private final AccountRepository accountRepository;

	public SystemAccountSeeder(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		ACCOUNTS.stream()
				.filter(systemAccount -> !accountRepository.existsByAccountNumber(systemAccount.number()))
				.forEach(this::create);
	}

	private void create(SystemAccount systemAccount) {
		Account account = new Account();
		account.setType(systemAccount.type());
		account.setCurrency(CURRENCY_VND);
		account.setStatus(AccountStatus.ACTIVE);
		account.setAccountNumber(systemAccount.number());
		accountRepository.save(account);
	}

	private record SystemAccount(String number, AccountType type) {
	}
}
