package com.fintech.wallet.payment.dto;

public record WebhookResponse(boolean received, String eventId, boolean duplicate) {
}
