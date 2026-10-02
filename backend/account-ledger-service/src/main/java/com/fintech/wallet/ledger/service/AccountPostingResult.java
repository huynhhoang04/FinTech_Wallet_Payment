package com.fintech.wallet.ledger.service;

import java.util.UUID;

public record AccountPostingResult(UUID ledgerTransactionId, String status, long balanceAfter) {
}
