package com.fintech.wallet.txengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransferRequest(
		@NotNull UUID sourceAccountId,
		@NotBlank @Size(max = 24) String targetAccountNumber,
		@Positive long amount,
		@NotBlank @Size(min = 3, max = 3) String currency,
		@Size(max = 255) String description,
		String otpToken) {
}
