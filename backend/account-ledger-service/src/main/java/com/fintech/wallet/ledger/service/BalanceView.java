package com.fintech.wallet.ledger.service;

import java.util.UUID;

public record BalanceView(UUID accountId, String currency, long available, long pending) {
}
