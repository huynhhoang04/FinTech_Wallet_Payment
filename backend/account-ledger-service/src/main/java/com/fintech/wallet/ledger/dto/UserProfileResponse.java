package com.fintech.wallet.ledger.dto;

public record UserProfileResponse(String userId, String phone, String email, String fullName, String kycStatus) {
}
