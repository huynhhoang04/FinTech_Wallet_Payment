package com.fintech.wallet.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.hmac.HmacUtils;
import com.fintech.wallet.payment.config.PaymentProperties;
import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.domain.WebhookLog;
import com.fintech.wallet.payment.dto.WebhookEvent;
import com.fintech.wallet.payment.dto.WebhookResponse;
import com.fintech.wallet.payment.repository.PaymentIntentRepository;
import com.fintech.wallet.payment.repository.WebhookLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Provider webhooks (docs 04 section 5.4).
 *
 * <p>The signature is checked <b>before</b> anything is remembered. Consuming the dedupe slot first
 * would let anyone post a forged callback with a real event id and suppress the genuine one that
 * follows - the customer pays and the wallet is never credited (docs 07 SEC-02). A forged callback is
 * therefore rejected without writing a receipt or a Redis key.
 */
@Slf4j
@Service
public class WebhookService {

	private static final Duration DEDUPE_TTL = Duration.ofDays(7);
	private static final String DEDUPE_PREFIX = "webhook:";
	private static final String SUCCEEDED = "SUCCEEDED";

	private final WebhookLogRepository webhooks;
	private final PaymentIntentRepository intents;
	private final WebhookRecorder recorder;
	private final PaymentEventPublisher publisher;
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final Map<String, String> providerSecrets;

	public WebhookService(WebhookLogRepository webhooks,
			PaymentIntentRepository intents,
			WebhookRecorder recorder,
			PaymentEventPublisher publisher,
			StringRedisTemplate redis,
			ObjectMapper objectMapper,
			PaymentProperties properties) {
		this.webhooks = webhooks;
		this.intents = intents;
		this.recorder = recorder;
		this.publisher = publisher;
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.providerSecrets = properties.providerSecrets();
	}

	public WebhookResponse receive(PaymentIntent.Provider provider, String signature, String rawBody) {
		WebhookEvent event = read(rawBody);

		if (!HmacUtils.verify(secret(provider), rawBody, signature)) {
			log.warn("Rejecting webhook {} from {}: signature mismatch", event.eventId(), provider);
			throw new BusinessException(ErrorCode.INVALID_SIGNATURE, "Chữ ký đối tác không hợp lệ");
		}
		if (isDuplicate(provider, event.eventId())) {
			return new WebhookResponse(true, event.eventId(), true);
		}

		try {
			WebhookLog receipt = recorder.record(provider, event, signature, rawBody);
			if (!SUCCEEDED.equals(event.status())) {
				log.info("Webhook {} reports status {}, nothing to credit", event.eventId(), event.status());
				return new WebhookResponse(true, event.eventId(), false);
			}
			publisher.publish(receipt);
			return new WebhookResponse(true, event.eventId(), false);
		} catch (DataIntegrityViolationException e) {
			// Two callbacks for the same event raced; the unique constraint won. Answer as a duplicate
			// instead of a 500 so the partner stops retrying (docs 07 SEC-09).
			log.info("Webhook {} arrived twice at once, treated as duplicate", event.eventId());
			return new WebhookResponse(true, event.eventId(), true);
		}
	}

	/**
	 * Demo helper (docs 04 section 5.5): builds the callback a provider would send and signs it, then
	 * runs it through the very same verification path so the demo exercises real code.
	 */
	public WebhookResponse simulateCallback(UUID paymentIntentId) {
		PaymentIntent intent = intents.findById(paymentIntentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên nạp tiền"));
		String rawBody = write(new WebhookEvent("sim-" + intent.getId(), intent.getId(), SUCCEEDED,
				intent.getAmount(), intent.getCurrency()));
		return receive(intent.getProvider(), HmacUtils.sign(secret(intent.getProvider()), rawBody), rawBody);
	}

	private boolean isDuplicate(PaymentIntent.Provider provider, String eventId) {
		if (webhooks.findByProviderAndEventId(provider, eventId).isPresent()) {
			return true;
		}
		String key = DEDUPE_PREFIX + provider + ":" + eventId;
		return !Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", DEDUPE_TTL));
	}

	private String secret(PaymentIntent.Provider provider) {
		return providerSecrets.getOrDefault(provider.name(), "dev-" + provider.name().toLowerCase() + "-secret");
	}

	private WebhookEvent read(String rawBody) {
		try {
			return objectMapper.readValue(rawBody, WebhookEvent.class);
		} catch (JsonProcessingException e) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Webhook payload không đọc được");
		}
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise webhook payload", e);
		}
	}
}
