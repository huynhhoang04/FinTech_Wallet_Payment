package com.fintech.wallet.ledger.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank String phone,
		@NotBlank String password) {
}
