package com.fintech.wallet.ledger.service;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.ledger.domain.Account;
import com.fintech.wallet.ledger.domain.AccountStatus;
import com.fintech.wallet.ledger.domain.AccountType;
import com.fintech.wallet.ledger.domain.LedgerEntry;
import com.fintech.wallet.ledger.domain.LedgerEntryFactory;
import com.fintech.wallet.ledger.domain.LedgerStatus;
import com.fintech.wallet.ledger.domain.SystemAccounts;
import com.fintech.wallet.ledger.repository.AccountBalanceRepository;
import com.fintech.wallet.ledger.repository.AccountRepository;
import com.fintech.wallet.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Money write path. Accounts are always locked in ascending accountId order, all at once, before
 * any balance is read; that ordering is what keeps concurrent A-&gt;B and B-&gt;A transfers deadlock free.
 *
 * <p>The replay check runs twice on purpose: once before locking as a cheap fast path, and again
 * after the lock is granted. Under READ_COMMITTED a second request with the same transaction id
 * reads "no entries yet" before it blocks, so without the second check the loser would post a
 * duplicate set of entries (docs 07 FIN-02). The unique key on (transaction_id, account_id) is the
 * database-level backstop.
 *
 * <p>Isolation is READ_COMMITTED, not REPEATABLE_READ. Under REPEATABLE_READ PostgreSQL raises
 * SQLState 40001 ("could not serialize access due to concurrent update") when a transaction locks a
 * row changed by another transaction after its snapshot was taken, aborting the posting; measured
 * with 25 concurrent A-&gt;B / B-&gt;A pairs, 43 of 50 postings failed that way. The ascending row
 * locks already serialise every writer of the involved accounts and READ_COMMITTED re-reads the
 * latest committed balance after the lock is granted, so correctness does not depend on the
 * snapshot isolation level.
 */
@Service
public class LedgerPostingService {

	private final AccountRepository accountRepository;
	private final AccountBalanceRepository accountBalanceRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	public LedgerPostingService(AccountRepository accountRepository,
			AccountBalanceRepository accountBalanceRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.accountRepository = accountRepository;
		this.accountBalanceRepository = accountBalanceRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public TransferResult postTransfer(UUID transactionId, UUID sourceAccountId, UUID targetAccountId, long amount,
			String currency, String reference) {
		Optional<TransferResult> replay = replayTransfer(transactionId, sourceAccountId, targetAccountId);
		if (replay.isPresent()) {
			return replay.get();
		}
		requirePositive(amount);
		if (sourceAccountId.equals(targetAccountId)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tài khoản nguồn và đích phải khác nhau");
		}

		LockedAccounts locked = lockActive(List.of(sourceAccountId, targetAccountId));
		Optional<TransferResult> committedWhileWaiting = replayTransfer(transactionId, sourceAccountId, targetAccountId);
		if (committedWhileWaiting.isPresent()) {
			return committedWhileWaiting.get();
		}

		Account source = locked.accounts().get(sourceAccountId);
		Account target = locked.accounts().get(targetAccountId);
		requireCurrency(source, currency);
		requireCurrency(target, currency);

		long sourceAfter = Math.subtractExact(locked.balances().get(sourceAccountId), amount);
		long targetAfter = Math.addExact(locked.balances().get(targetAccountId), amount);
		requireNonNegativeForUserAccount(source, sourceAfter);

		ledgerEntryRepository.saveAll(LedgerEntryFactory.transfer(transactionId, sourceAccountId, targetAccountId,
				amount, currency, reference, sourceAfter, targetAfter));
		applyDelta(sourceAccountId, currency, Math.negateExact(amount));
		applyDelta(targetAccountId, currency, amount);

		return new TransferResult(transactionId, LedgerStatus.POSTED, sourceAfter, targetAfter);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AccountPostingResult deposit(UUID transactionId, UUID walletAccountId, long amount, String currency,
			String channel) {
		Optional<AccountPostingResult> replay = replayAccountPosting(transactionId, walletAccountId);
		if (replay.isPresent()) {
			return replay.get();
		}
		requirePositive(amount);
		Account suspense = suspenseAccount();
		requireDistinctWallet(walletAccountId, suspense);
		LockedAccounts locked = lockActive(List.of(suspense.getId(), walletAccountId));
		Optional<AccountPostingResult> committedWhileWaiting = replayAccountPosting(transactionId, walletAccountId);
		if (committedWhileWaiting.isPresent()) {
			return committedWhileWaiting.get();
		}
		requireCurrency(locked.accounts().get(suspense.getId()), currency);
		requireCurrency(locked.accounts().get(walletAccountId), currency);

		long suspenseAfter = Math.subtractExact(locked.balances().get(suspense.getId()), amount);
		long walletAfter = Math.addExact(locked.balances().get(walletAccountId), amount);

		ledgerEntryRepository.saveAll(LedgerEntryFactory.deposit(transactionId, suspense.getId(), walletAccountId,
				amount, currency, channel, suspenseAfter, walletAfter));
		applyDelta(suspense.getId(), currency, Math.negateExact(amount));
		applyDelta(walletAccountId, currency, amount);

		return new AccountPostingResult(transactionId, LedgerStatus.POSTED, walletAfter);
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	public AccountPostingResult withdraw(UUID transactionId, UUID walletAccountId, long amount, String currency,
			String channel) {
		Optional<AccountPostingResult> replay = replayAccountPosting(transactionId, walletAccountId);
		if (replay.isPresent()) {
			return replay.get();
		}
		requirePositive(amount);
		Account suspense = suspenseAccount();
		requireDistinctWallet(walletAccountId, suspense);
		LockedAccounts locked = lockActive(List.of(suspense.getId(), walletAccountId));
		Optional<AccountPostingResult> committedWhileWaiting = replayAccountPosting(transactionId, walletAccountId);
		if (committedWhileWaiting.isPresent()) {
			return committedWhileWaiting.get();
		}
		Account wallet = locked.accounts().get(walletAccountId);
		requireCurrency(wallet, currency);
		requireCurrency(locked.accounts().get(suspense.getId()), currency);

		long walletAfter = Math.subtractExact(locked.balances().get(walletAccountId), amount);
		long suspenseAfter = Math.addExact(locked.balances().get(suspense.getId()), amount);
		requireNonNegativeForUserAccount(wallet, walletAfter);

		ledgerEntryRepository.saveAll(LedgerEntryFactory.withdraw(transactionId, walletAccountId, suspense.getId(),
				amount, currency, channel, walletAfter, suspenseAfter));
		applyDelta(walletAccountId, currency, Math.negateExact(amount));
		applyDelta(suspense.getId(), currency, amount);

		return new AccountPostingResult(transactionId, LedgerStatus.POSTED, walletAfter);
	}

	private LockedAccounts lockActive(List<UUID> accountIds) {
		List<UUID> ordered = accountIds.stream().distinct().sorted().toList();
		Map<UUID, Account> accounts = new LinkedHashMap<>();
		Map<UUID, Long> balances = new LinkedHashMap<>();
		for (UUID accountId : ordered) {
			Account account = accountRepository.lockById(accountId)
					.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
			if (account.getStatus() != AccountStatus.ACTIVE) {
				throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
			}
			accounts.put(accountId, account);
			balances.put(accountId, accountBalanceRepository
					.lockByAccountIdAndCurrency(accountId, account.getCurrency())
					.map(AccountBalance -> AccountBalance.getBalance())
					.orElse(0L));
		}
		return new LockedAccounts(accounts, balances);
	}

	private Optional<TransferResult> replayTransfer(UUID transactionId, UUID sourceAccountId, UUID targetAccountId) {
		List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		requireAccountsPresent(entries, List.of(sourceAccountId, targetAccountId));
		return Optional.of(new TransferResult(transactionId, LedgerStatus.POSTED,
				balanceAfterOf(entries, sourceAccountId), balanceAfterOf(entries, targetAccountId)));
	}

	private Optional<AccountPostingResult> replayAccountPosting(UUID transactionId, UUID walletAccountId) {
		List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		requireAccountsPresent(entries, List.of(walletAccountId));
		return Optional.of(new AccountPostingResult(transactionId, LedgerStatus.POSTED,
				balanceAfterOf(entries, walletAccountId)));
	}

	private static void requireAccountsPresent(List<LedgerEntry> entries, List<UUID> expectedAccounts) {
		boolean allPresent = expectedAccounts.stream()
				.allMatch(expected -> entries.stream()
						.anyMatch(entry -> entry.getAccountId().equals(expected)));
		if (!allPresent) {
			throw new BusinessException(ErrorCode.DUPLICATE_REQUEST,
					"transaction_id đã được dùng cho giao dịch khác");
		}
	}

	private static long balanceAfterOf(List<LedgerEntry> entries, UUID accountId) {
		return entries.stream()
				.filter(entry -> entry.getAccountId().equals(accountId))
				.mapToLong(LedgerEntry::getBalanceAfter)
				.findFirst()
				.orElse(0L);
	}

	private Account suspenseAccount() {
		return accountRepository.findByAccountNumber(SystemAccounts.SUSPENSE_NUMBER)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Tài khoản SUSPENSE chưa được seed"));
	}

	private static void requireDistinctWallet(UUID walletAccountId, Account suspense) {
		if (suspense.getId().equals(walletAccountId)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tài khoản ví không hợp lệ");
		}
	}

	private void applyDelta(UUID accountId, String currency, long delta) {
		accountBalanceRepository.applyDelta(UUID.randomUUID(), accountId, currency, delta);
	}

	private static void requirePositive(long amount) {
		if (amount <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tiền phải lớn hơn 0");
		}
	}

	private static void requireCurrency(Account account, String currency) {
		if (!account.getCurrency().equals(currency)) {
			throw new BusinessException(ErrorCode.CURRENCY_MISMATCH);
		}
	}

	private static void requireNonNegativeForUserAccount(Account account, long balanceAfter) {
		if (account.getType() == AccountType.USER && balanceAfter < 0) {
			throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
		}
	}

	private record LockedAccounts(Map<UUID, Account> accounts, Map<UUID, Long> balances) {
	}
}
