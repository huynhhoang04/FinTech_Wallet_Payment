package com.fintech.wallet.ledger.dto;

public record OtpVerifyResponse(String otpToken, boolean valid) {
}
