package com.fintech.wallet.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent extends BaseEntity {

	@Column(nullable = false, length = 64)
	private String topic;

	@Column(name = "event_key", length = 64)
	private String eventKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(name = "error_message", length = 1024)
	private String errorMessage;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;
}
