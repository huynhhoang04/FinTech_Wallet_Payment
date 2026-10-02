package com.fintech.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Immutable
@Entity
@Table(name = "ledger_entries", indexes = {
		@Index(name = "ix_le_tx", columnList = "transaction_id"),
		@Index(name = "ix_le_account_created", columnList = "account_id, created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "transaction_id", nullable = false, updatable = false)
	private UUID transactionId;

	@Column(name = "account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Column(nullable = false, updatable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8, updatable = false)
	private EntryDirection direction;

	@Column(nullable = false, length = 3, updatable = false)
	private String currency;

	@Column(name = "balance_after", nullable = false, updatable = false)
	private Long balanceAfter;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", updatable = false)
	private String metadata;
}
