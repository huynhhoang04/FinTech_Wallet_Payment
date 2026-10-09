package com.fintech.wallet.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only. {@code hash = SHA256(prevHash + payload)} links every row to the one before it, so an
 * edit anywhere breaks the chain and {@code /verify-chain} reports it (docs 03 section 7.1). The
 * unique {@code event_id} makes a Kafka redelivery a no-op (docs 02 section 7).
 */
@Getter
@Setter
@Immutable
@Entity
@Table(name = "audit_logs",
		uniqueConstraints = @UniqueConstraint(name = "uq_audit_prev_hash", columnNames = "prev_hash"),
		indexes = {
				@Index(name = "ix_audit_event", columnList = "event_id", unique = true),
				@Index(name = "ix_audit_aggregate", columnList = "aggregate_id"),
				@Index(name = "ix_audit_occurred", columnList = "occurred_at")
		})
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "event_id", nullable = false, unique = true)
	private UUID eventId;

	@Column(name = "event_type", nullable = false, length = 48)
	private String eventType;

	@Column(name = "aggregate_id")
	private UUID aggregateId;

	@Column(length = 64)
	private String actor;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(name = "prev_hash", length = 64)
	private String prevHash;

	@Column(nullable = false, length = 64)
	private String hash;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;
}
