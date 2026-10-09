package com.fintech.wallet.payment.dto;

import com.fintech.wallet.payment.domain.PaymentIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Service-to-service create: the caller is the transaction engine, so the user comes from the body. */
public record InternalIntentRequest(
		@NotNull UUID userId,
		@NotNull UUID accountId,
		@Positive long amount,
		@NotBlank @Size(min = 3, max = 3) String currency,
		@NotNull PaymentIntent.Provider provider) {
}
