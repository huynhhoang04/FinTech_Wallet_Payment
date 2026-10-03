package com.fintech.wallet.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.KafkaTopics;
import com.fintech.wallet.common.event.PaymentSucceededEvent;
import com.fintech.wallet.payment.config.PaymentProperties;
import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.domain.WebhookLog;
import com.fintech.wallet.payment.repository.PaymentIntentRepository;
import com.fintech.wallet.payment.repository.WebhookLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Hands {@code payment.succeeded} to Kafka. The receipt row is the outbox: the event is built from it,
 * and a receipt that has not been published yet is retried, so a Kafka outage (or a crash between
 * recording and publishing) never loses a paid deposit.
 *
 * <p>The bean always exists - the webhook path needs it - and publishing is switched off per profile
 * instead, leaving receipts unpublished for the retry to pick up once Kafka is reachable.
 */
@Slf4j
@Component
public class PaymentEventPublisher {

	private final WebhookLogRepository webhooks;
	private final PaymentIntentRepository intents;
	private final KafkaTemplate<String, String> kafka;
	private final ObjectMapper objectMapper;
	private final boolean publishEnabled;

	public PaymentEventPublisher(WebhookLogRepository webhooks, PaymentIntentRepository intents,
			KafkaTemplate<String, String> kafka, ObjectMapper objectMapper, PaymentProperties properties) {
		this.webhooks = webhooks;
		this.intents = intents;
		this.kafka = kafka;
		this.objectMapper = objectMapper;
		this.publishEnabled = properties.publishEnabled();
	}

	@Scheduled(fixedDelayString = "${app.payment.publish-interval-ms:10000}")
	public void retryUnpublished() {
		if (!publishEnabled) {
			return;
		}
		webhooks.findTop50BySignatureValidIsTrueAndPublishedAtIsNullOrderByCreatedAtAsc().forEach(this::publish);
	}

	public void publish(WebhookLog receipt) {
		if (!publishEnabled) {
			log.debug("Publishing is off in this profile; receipt {} stays unpublished", receipt.getEventId());
			return;
		}
		try {
			kafka.send(KafkaTopics.PAYMENT_SUCCEEDED, receipt.getEventId(), payload(receipt))
					.get(5, TimeUnit.SECONDS);
			receipt.setPublishedAt(Instant.now());
			webhooks.save(receipt);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.warn("Payment event {} still pending, will retry: {}", receipt.getEventId(), e.toString());
		}
	}

	private String payload(WebhookLog receipt) {
		PaymentIntent intent = receipt.getPaymentIntentId() == null ? null
				: intents.findById(receipt.getPaymentIntentId()).orElse(null);
		PaymentSucceededEvent event = new PaymentSucceededEvent(
				receipt.getEventId(),
				text(receipt.getPaymentIntentId()),
				intent == null ? null : intent.getWalletAccountId().toString(),
				intent == null ? 0L : intent.getAmount(),
				intent == null ? null : intent.getCurrency(),
				receipt.getProvider().name(),
				Instant.now());
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise payment event", e);
		}
	}

	private static String text(UUID value) {
		return value == null ? null : value.toString();
	}
}
