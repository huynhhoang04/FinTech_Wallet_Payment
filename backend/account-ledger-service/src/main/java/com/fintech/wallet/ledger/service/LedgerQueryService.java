package com.fintech.wallet.ledger.service;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.ledger.domain.Account;
import com.fintech.wallet.ledger.domain.AccountBalance;
import com.fintech.wallet.ledger.domain.LedgerEntry;
import com.fintech.wallet.ledger.repository.AccountBalanceRepository;
import com.fintech.wallet.ledger.repository.AccountRepository;
import com.fintech.wallet.ledger.repository.LedgerEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerQueryService {

	private static final int DEFAULT_PAGE_SIZE = 20;

	private final AccountRepository accountRepository;
	private final AccountBalanceRepository accountBalanceRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	public LedgerQueryService(AccountRepository accountRepository,
			AccountBalanceRepository accountBalanceRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.accountRepository = accountRepository;
		this.accountBalanceRepository = accountBalanceRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public BalanceView getBalance(UUID accountId) {
		Account account = requireAccount(accountId);
		long available = accountBalanceRepository.findFirstByAccountId(accountId)
				.map(AccountBalance::getBalance)
				.orElse(0L);
		return new BalanceView(accountId, account.getCurrency(), available, 0L);
	}

	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public StatementView getStatement(UUID accountId, Instant from, Instant to, int page, int size) {
		requireAccount(accountId);
		PageRequest pageRequest = PageRequest.of(Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
		List<LedgerEntry> entries = ledgerEntryRepository
				.findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
						accountId, from, to, pageRequest)
				.getContent();
		return new StatementView(entries,
				ledgerEntryRepository.sumBefore(accountId, from),
				ledgerEntryRepository.sumBefore(accountId, to));
	}

	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public AccountInfo accountByNumber(String accountNumber) {
		return accountRepository.findByAccountNumber(accountNumber)
				.map(account -> new AccountInfo(account.getId(), account.getAccountNumber(), account.getCurrency(),
						account.getStatus().name()))
				.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
	}

	@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
	public EntriesView entriesOf(UUID transactionId) {
		List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
		return new EntriesView(transactionId, entries, entries.stream().mapToLong(LedgerEntry::getAmount).sum());
	}

	public record AccountInfo(UUID accountId, String accountNumber, String currency, String status) {
	}

	public record EntriesView(UUID transactionId, List<LedgerEntry> entries, long sum) {
	}

	private Account requireAccount(UUID accountId) {
		return accountRepository.findById(accountId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
	}
}
