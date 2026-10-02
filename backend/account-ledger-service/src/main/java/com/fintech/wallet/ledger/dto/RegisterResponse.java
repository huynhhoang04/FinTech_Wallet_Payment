package com.fintech.wallet.ledger.dto;

public record RegisterResponse(String userId, String walletAccountId, String accountNumber, String status) {
}
