package com.fintech.wallet.ledger.dto;

public record LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

	public static LoginResponse of(String accessToken, String refreshToken, long expiresInSeconds) {
		return new LoginResponse(accessToken, refreshToken, "Bearer", expiresInSeconds);
	}
}
