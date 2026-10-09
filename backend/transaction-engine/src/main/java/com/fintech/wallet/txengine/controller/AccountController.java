package com.fintech.wallet.txengine.controller;

import com.fintech.wallet.common.security.CurrentUser;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import com.fintech.wallet.txengine.service.AccountOwnership;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Accounts and balances live in the ledger; this service forwards the reads, and checks that the
 * caller owns the account first (docs 04 sections 3.7-3.9, docs 07 SEC-04).
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final LedgerClient ledger;
	private final AccountOwnership ownership;

	public AccountController(LedgerClient ledger, AccountOwnership ownership) {
		this.ledger = ledger;
		this.ownership = ownership;
	}

	@GetMapping
	public List<LedgerClient.AccountInfo> myAccounts() {
		return ledger.accountsByOwner(UUID.fromString(CurrentUser.userId()));
	}

	@GetMapping("/{accountId}/balance")
	public LedgerClient.BalanceInfo balance(@PathVariable UUID accountId) {
		ownership.requireOwned(CurrentUser.userId(), accountId);
		return ledger.balance(accountId);
	}

	@GetMapping("/{accountId}/statement")
	public LedgerClient.StatementInfo statement(@PathVariable UUID accountId,
			@RequestParam(defaultValue = "0") long fromEpochMs,
			@RequestParam(defaultValue = "0") long toEpochMs,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ownership.requireOwned(CurrentUser.userId(), accountId);
		long to = toEpochMs > 0 ? toEpochMs : Instant.now().toEpochMilli();
		return ledger.statement(accountId, fromEpochMs, to, page, size);
	}
}
