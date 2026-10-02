package com.fintech.wallet.txengine.controller;

import com.fintech.wallet.txengine.grpc.LedgerClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Balances live in the ledger; this service only forwards the read (docs 04 section 3.8). */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final LedgerClient ledger;

	public AccountController(LedgerClient ledger) {
		this.ledger = ledger;
	}

	@GetMapping("/{accountId}/balance")
	public LedgerClient.BalanceInfo balance(@PathVariable UUID accountId) {
		return ledger.balance(accountId);
	}
}
