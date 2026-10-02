package com.fintech.wallet.common.event;

import java.time.Instant;

public record PaymentSucceededEvent(
		String eventId,
		String paymentIntentId,
		String accountId,
		long amount,
		String currency,
		String provider,
		Instant occurredAt) {
}
