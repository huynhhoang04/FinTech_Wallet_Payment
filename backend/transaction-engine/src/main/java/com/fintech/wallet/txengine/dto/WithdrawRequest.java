package com.fintech.wallet.txengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WithdrawRequest(
		@NotNull UUID accountId,
		@Positive long amount,
		@NotBlank @Size(min = 3, max = 3) String currency,
		@NotBlank @Size(max = 16) String bankCode,
		@NotBlank @Size(max = 32) String bankAccount,
		String otpToken) {
}
