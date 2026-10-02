package com.fintech.wallet.ledger.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
		@NotBlank String otpRef,
		@NotBlank String code) {
}
