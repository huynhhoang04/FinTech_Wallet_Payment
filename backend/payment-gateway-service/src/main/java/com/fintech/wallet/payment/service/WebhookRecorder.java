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
import java.util.Objects;

/**
 * One transaction for the receipt and the intent status change, so a provider event is never
 * half-recorded. Publishing happens afterwards, and anything left unpublished is retried from the
 * receipt itself (see {@link PaymentEventPublisher}).
 *
 * <p>An intent is only marked SUCCEEDED when the callback says SUCCEEDED <b>and</b> its amount,
 * currency and provider match the intent it points at; otherwise a signed but wrong callback would
 * settle the wrong payment (docs 07 SEC-05).
 */
@Slf4j
@Service
public class WebhookRecorder {

	private static final String SUCCEEDED = "SUCCEEDED";

	private final WebhookLogRepository webhooks;
	private final PaymentIntentRepository intents;

	public WebhookRecorder(WebhookLogRepository webhooks, PaymentIntentRepository intents) {
		this.webhooks = webhooks;
		this.intents = intents;
	}

	@Transactional
	public WebhookLog record(PaymentIntent.Provider provider, WebhookEvent event, String signature, String rawBody) {
		WebhookLog receipt = new WebhookLog();
		receipt.setProvider(provider);
		receipt.setEventId(event.eventId());
		receipt.setSignature(signature);
		receipt.setSignatureValid(true);
		receipt.setPayload(rawBody);
		receipt.setProcessed(false);
		receipt.setPaymentIntentId(event.paymentIntentId());

		if (event.paymentIntentId() == null) {
			log.warn("Webhook {} carries no payment intent, recorded without settling", event.eventId());
			return webhooks.save(receipt);
		}
		intents.findById(event.paymentIntentId())
				.ifPresentOrElse(intent -> settle(provider, intent, event, receipt),
						() -> log.warn("Webhook {} references unknown intent {}", event.eventId(),
								event.paymentIntentId()));
		return webhooks.save(receipt);
	}

	private void settle(PaymentIntent.Provider provider, PaymentIntent intent, WebhookEvent event,
			WebhookLog receipt) {
		if (!SUCCEEDED.equals(event.status())) {
			log.info("Webhook {} for intent {} reports status {}, intent left as is", event.eventId(), intent.getId(),
					event.status());
			return;
		}
		if (!matches(provider, intent, event)) {
			log.error("Webhook {} does not match intent {}: provider {}/{}, amount {}/{}, currency {}/{}",
					event.eventId(), intent.getId(), provider, intent.getProvider(), event.amount(), intent.getAmount(),
					event.currency(), intent.getCurrency());
			return;
		}
		intent.setStatus(PaymentIntent.PaymentStatus.SUCCEEDED);
		intents.save(intent);
		receipt.setProcessed(true);
		receipt.setProcessedAt(Instant.now());
	}

	private static boolean matches(PaymentIntent.Provider provider, PaymentIntent intent, WebhookEvent event) {
		return provider == intent.getProvider()
				&& Objects.equals(event.amount(), intent.getAmount())
				&& Objects.equals(event.currency(), intent.getCurrency());
	}
}
