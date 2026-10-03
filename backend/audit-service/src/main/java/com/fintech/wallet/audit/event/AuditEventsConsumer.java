package com.fintech.wallet.audit.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.audit.service.AuditChainService;
import com.fintech.wallet.common.constant.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Records every event the system publishes. The event id comes from the payload when it carries one,
 * otherwise it is derived from the payload itself, which keeps redelivery a no-op either way.
 */
@Slf4j
@Component
public class AuditEventsConsumer {

	private final AuditChainService chain;
	private final ObjectMapper objectMapper;

	public AuditEventsConsumer(AuditChainService chain, ObjectMapper objectMapper) {
		this.chain = chain;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = { KafkaTopics.TX_COMPLETED, KafkaTopics.PAYMENT_SUCCEEDED,
			KafkaTopics.PAYMENT_WEBHOOK_RECEIVED, KafkaTopics.RISK_ALERT, KafkaTopics.AUDIT_EVENT },
			groupId = "${spring.kafka.consumer.group-id}")
	public void onEvent(@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
			String payload) {
		Map<String, Object> event = read(payload);
		chain.append(eventId(event, key, payload), topic, aggregateId(event), actor(event),
				payload, Instant.now());
	}

	private static UUID eventId(Map<String, Object> event, String key, String payload) {
		for (String field : new String[] { "eventId", "transactionId", "paymentIntentId" }) {
			UUID candidate = uuid(event.get(field));
			if (candidate != null) {
				return candidate;
			}
		}
		UUID fromKey = uuid(key);
		return fromKey != null ? fromKey : UUID.nameUUIDFromBytes(payload.getBytes(StandardCharsets.UTF_8));
	}

	private static UUID aggregateId(Map<String, Object> event) {
		for (String field : new String[] { "transactionId", "paymentIntentId", "accountId", "aggregateId" }) {
			UUID candidate = uuid(event.get(field));
			if (candidate != null) {
				return candidate;
			}
		}
		return null;
	}

	private static String actor(Map<String, Object> event) {
		Object actor = event.get("actor");
		return actor == null ? "SYSTEM" : actor.toString();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> read(String payload) {
		try {
			return objectMapper.readValue(payload, Map.class);
		} catch (JsonProcessingException e) {
			log.warn("Audit received a payload it cannot read: {}", e.toString());
			return Map.of();
		}
	}

	private static UUID uuid(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return UUID.fromString(value.toString());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
