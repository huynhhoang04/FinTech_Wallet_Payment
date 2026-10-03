package com.fintech.wallet.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Webhook receipts double as the outbox for the provider events this service publishes: the
 * payload is stored in the same transaction that records the receipt, and the scheduler retries
 * anything that has not been handed to Kafka yet (docs 03 section 5.2 gives no separate table).
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_logs", uniqueConstraints =
		@UniqueConstraint(name = "uq_webhook_provider_event", columnNames = { "provider", "event_id" }))
public class WebhookLog extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private PaymentIntent.Provider provider;

	@Column(name = "event_id", nullable = false, length = 128)
	private String eventId;

	@Column(length = 512)
	private String signature;

	@Column(name = "signature_valid", nullable = false)
	private boolean signatureValid;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(nullable = false)
	private boolean processed;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "payment_intent_id")
	private UUID paymentIntentId;

	@Column(name = "published_at")
	private Instant publishedAt;
}
