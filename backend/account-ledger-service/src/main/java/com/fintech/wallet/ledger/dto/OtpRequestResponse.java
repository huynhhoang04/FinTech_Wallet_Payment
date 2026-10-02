package com.fintech.wallet.ledger.dto;

public record OtpRequestResponse(String otpRef, long expiresIn) {
}
