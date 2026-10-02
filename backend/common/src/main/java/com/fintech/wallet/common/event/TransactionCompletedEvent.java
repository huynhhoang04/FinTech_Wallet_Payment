package com.fintech.wallet.common.event;

import java.time.Instant;

public record TransactionCompletedEvent(
		String transactionId,
		String type,
		String status,
		String sourceAccountId,
		String targetAccountId,
		long amount,
		String currency,
		String description,
		Instant occurredAt) {
}
