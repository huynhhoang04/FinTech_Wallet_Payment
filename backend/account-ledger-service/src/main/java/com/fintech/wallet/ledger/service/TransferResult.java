package com.fintech.wallet.ledger.service;

import java.util.UUID;

public record TransferResult(UUID ledgerTransactionId, String status, long sourceBalanceAfter,
		long targetBalanceAfter) {
}
