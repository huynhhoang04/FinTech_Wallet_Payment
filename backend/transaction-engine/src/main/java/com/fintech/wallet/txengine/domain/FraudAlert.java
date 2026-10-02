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

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "fraud_alerts", indexes = {
		@Index(name = "ix_fraud_tx", columnList = "transaction_id"),
		@Index(name = "ix_fraud_created", columnList = "created_at")
})
public class FraudAlert extends BaseEntity {

	@Column(name = "transaction_id")
	private UUID transactionId;

	@Column(name = "rule_code", nullable = false, length = 32)
	private String ruleCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private Severity severity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private Decision decision;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String details;

	public enum Severity {
		LOW,
		MEDIUM,
		HIGH
	}

	public enum Decision {
		ALLOW,
		REVIEW,
		BLOCK
	}
}
