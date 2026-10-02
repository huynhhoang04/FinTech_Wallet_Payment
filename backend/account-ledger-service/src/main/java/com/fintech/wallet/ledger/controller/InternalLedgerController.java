package com.fintech.wallet.ledger.controller;

import com.fintech.wallet.ledger.domain.AccountBalance;
import com.fintech.wallet.ledger.repository.AccountBalanceRepository;
import com.fintech.wallet.ledger.repository.LedgerEntryRepository;
import com.fintech.wallet.ledger.service.BalanceView;
import com.fintech.wallet.ledger.service.LedgerQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalLedgerController {

	private final LedgerQueryService queryService;
	private final AccountBalanceRepository accountBalanceRepository;
	private final LedgerEntryRepository ledgerEntryRepository;

	public InternalLedgerController(LedgerQueryService queryService,
			AccountBalanceRepository accountBalanceRepository,
			LedgerEntryRepository ledgerEntryRepository) {
		this.queryService = queryService;
		this.accountBalanceRepository = accountBalanceRepository;
		this.ledgerEntryRepository = ledgerEntryRepository;
	}

	@GetMapping("/accounts/{accountId}/balance")
	public BalanceView balance(@PathVariable UUID accountId) {
		return queryService.getBalance(accountId);
	}

	@GetMapping("/accounts/{accountId}/verify-balance")
	public BalanceVerification verifyBalance(@PathVariable UUID accountId) {
		long snapshot = accountBalanceRepository.findFirstByAccountId(accountId)
				.map(AccountBalance::getBalance)
				.orElse(0L);
		long computed = ledgerEntryRepository.sumByAccountId(accountId);
		return new BalanceVerification(accountId.toString(), snapshot, computed, snapshot == computed);
	}

	@GetMapping("/ledger/summary")
	public LedgerSummary summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		LocalDate target = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
		Instant from = target.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = target.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		long totalDebit = Math.abs(ledgerEntryRepository.totalDebitsBetween(from, to));
		long totalCredit = ledgerEntryRepository.totalCreditsBetween(from, to);
		return new LedgerSummary(target.toString(), totalDebit, totalCredit, totalCredit - totalDebit);
	}

	@GetMapping("/ledger/verify-chain")
	public LedgerIntegrity verifyChain() {
		List<UUID> unbalanced = ledgerEntryRepository.findUnbalancedTransactionIds();
		return new LedgerIntegrity(unbalanced.isEmpty(), ledgerEntryRepository.countTransactions(),
				unbalanced.size(), ledgerEntryRepository.totalAmount());
	}

	public record BalanceVerification(String accountId, long snapshotBalance, long computedBalance, boolean consistent) {
	}

	public record LedgerSummary(String date, long totalDebit, long totalCredit, long difference) {
	}

	public record LedgerIntegrity(boolean valid, long checkedTransactions, long unbalancedTransactions,
			long totalAmount) {
	}
}
