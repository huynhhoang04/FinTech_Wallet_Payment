package com.fintech.wallet.txengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "outbox_events", indexes = {
		@Index(name = "ix_outbox_status", columnList = "status, created_at")
})
public class OutboxEvent extends BaseEntity {

	@Column(name = "aggregate_type", nullable = false, length = 32)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false)
	private UUID aggregateId;

	@Column(name = "event_type", nullable = false, length = 48)
	private String eventType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private Status status;

	@Column(name = "published_at")
	private Instant publishedAt;

	public enum Status {
		NEW,
		PUBLISHED,
		FAILED
	}
}
