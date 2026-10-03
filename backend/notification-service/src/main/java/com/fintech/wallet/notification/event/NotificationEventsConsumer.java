package com.fintech.wallet.notification.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.KafkaTopics;
import com.fintech.wallet.common.event.PaymentSucceededEvent;
import com.fintech.wallet.common.event.TransactionCompletedEvent;
import com.fintech.wallet.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumers are idempotent: the notification log is keyed by template plus the transaction it refers
 * to, so a Kafka redelivery is a no-op (backend skill, section 7).
 */
@Slf4j
@Component
public class NotificationEventsConsumer {

	private final NotificationService notifications;
	private final ObjectMapper objectMapper;

	public NotificationEventsConsumer(NotificationService notifications, ObjectMapper objectMapper) {
		this.notifications = notifications;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = KafkaTopics.TX_COMPLETED, groupId = "${spring.kafka.consumer.group-id}")
	public void onTransactionCompleted(String payload) {
		TransactionCompletedEvent event = read(payload, TransactionCompletedEvent.class);
		UUID accountId = accountOf(event);
		notifications.notify(accountId, uuid(event.transactionId()), NotificationService.TEMPLATE_BALANCE_CHANGED,
				"Biến động số dư", Map.of(
						"transactionId", String.valueOf(event.transactionId()),
						"type", String.valueOf(event.type()),
						"amount", String.valueOf(event.amount()),
						"currency", String.valueOf(event.currency()),
						"occurredAt", String.valueOf(event.occurredAt())),
				payload);
	}

	@KafkaListener(topics = KafkaTopics.PAYMENT_SUCCEEDED, groupId = "${spring.kafka.consumer.group-id}")
	public void onPaymentSucceeded(String payload) {
		PaymentSucceededEvent event = read(payload, PaymentSucceededEvent.class);
		notifications.notify(uuid(event.accountId()), uuid(event.paymentIntentId()),
				NotificationService.TEMPLATE_PAYMENT_SUCCESS, "Nạp tiền thành công", Map.of(
						"amount", String.valueOf(event.amount()),
						"currency", String.valueOf(event.currency()),
						"provider", String.valueOf(event.provider()),
						"occurredAt", String.valueOf(event.occurredAt())),
				payload);
	}

	/** Published by the fraud rules in the transaction engine; the topic exists in docs 02 section 0.2. */
	@KafkaListener(topics = KafkaTopics.RISK_ALERT, groupId = "${spring.kafka.consumer.group-id}")
	public void onRiskAlert(String payload) {
		Map<?, ?> event = read(payload, Map.class);
		notifications.notify(uuid(String.valueOf(event.get("accountId"))), null,
				NotificationService.TEMPLATE_RISK_ALERT, "Cảnh báo giao dịch bất thường",
				Map.of("rule", String.valueOf(event.get("rule")), "occurredAt", String.valueOf(Instant.now())),
				payload);
	}

	private UUID accountOf(TransactionCompletedEvent event) {
		return event.sourceAccountId() != null ? uuid(event.sourceAccountId()) : uuid(event.targetAccountId());
	}

	private <T> T read(String payload, Class<T> type) {
		try {
			return objectMapper.readValue(payload, type);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot read event payload: " + payload, e);
		}
	}

	private static UUID uuid(String value) {
		return value == null || "null".equals(value) ? null : UUID.fromString(value);
	}
}
