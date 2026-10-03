package com.fintech.wallet.payment.dto;

import com.fintech.wallet.payment.domain.PaymentIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateIntentRequest(
		@NotNull UUID accountId,
		@Positive long amount,
		@NotBlank @Size(min = 3, max = 3) String currency,
		@NotNull PaymentIntent.Provider provider,
		@Size(max = 255) String returnUrl) {
}
