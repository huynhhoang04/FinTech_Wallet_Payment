package com.fintech.wallet.payment.dto;

import java.util.UUID;

/**
 * The shape a provider sends is provider specific (docs 04 section 10 lists it as still open); this
 * is the demo shape used by the sandbox providers and by the simulate-callback endpoint.
 */
public record WebhookEvent(String eventId, UUID paymentIntentId, String status, Long amount, String currency) {
}
