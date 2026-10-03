package com.fintech.wallet.payment.service;

import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.domain.WebhookLog;
import com.fintech.wallet.payment.dto.WebhookEvent;
import com.fintech.wallet.payment.repository.PaymentIntentRepository;
import com.fintech.wallet.payment.repository.WebhookLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * One transaction for the receipt and the intent status change, so a provider event is never
 * half-recorded. Publishing happens afterwards, and anything left unpublished is retried from the
 * receipt itself (see {@link PaymentEventPublisher}).
 */
@Slf4j
@Service
public class WebhookRecorder {

	private final WebhookLogRepository webhooks;
	private final PaymentIntentRepository intents;

	public WebhookRecorder(WebhookLogRepository webhooks, PaymentIntentRepository intents) {
		this.webhooks = webhooks;
		this.intents = intents;
	}

	@Transactional
	public WebhookLog record(PaymentIntent.Provider provider, WebhookEvent event, String signature, boolean signatureValid,
			String rawBody) {
		WebhookLog receipt = new WebhookLog();
		receipt.setProvider(provider);
		receipt.setEventId(event.eventId());
		receipt.setSignature(signature);
		receipt.setSignatureValid(signatureValid);
		receipt.setPayload(rawBody);
		receipt.setProcessed(false);
		receipt.setPaymentIntentId(event.paymentIntentId());

		if (signatureValid && event.paymentIntentId() != null) {
			intents.findById(event.paymentIntentId()).ifPresentOrElse(intent -> {
				intent.setStatus(PaymentIntent.PaymentStatus.SUCCEEDED);
				intents.save(intent);
				receipt.setProcessed(true);
				receipt.setProcessedAt(Instant.now());
			}, () -> log.warn("Webhook {} references unknown intent {}", event.eventId(), event.paymentIntentId()));
		}
		return webhooks.save(receipt);
	}
}
