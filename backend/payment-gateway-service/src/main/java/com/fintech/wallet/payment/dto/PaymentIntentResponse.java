package com.fintech.wallet.payment.dto;

import com.fintech.wallet.payment.domain.PaymentIntent;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(
		UUID paymentIntentId,
		String provider,
		String status,
		long amount,
		String currency,
		String checkoutUrl,
		String qrContent,
		Instant expiresAt,
		Instant createdAt) {

	public static PaymentIntentResponse of(PaymentIntent intent) {
		return new PaymentIntentResponse(intent.getId(), intent.getProvider().name(), intent.getStatus().name(),
				intent.getAmount(), intent.getCurrency(), intent.getCheckoutUrl(), qrContent(intent),
				intent.getExpiresAt(), intent.getCreatedAt());
	}

	/**
	 * The schema stores the checkout URL but no QR column (docs 03 section 5.1), so the demo derives
	 * a QR payload from the intent. A real provider returns the QR string from its API.
	 */
	private static String qrContent(PaymentIntent intent) {
		return intent.getId() == null
				? null
				: "000201010212" + intent.getProvider().name() + intent.getId().toString().replace("-", "");
	}
}
