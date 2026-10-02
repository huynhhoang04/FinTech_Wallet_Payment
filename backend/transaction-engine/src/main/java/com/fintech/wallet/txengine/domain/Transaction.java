package com.fintech.wallet.txengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "transactions", indexes = {
		@Index(name = "ix_tx_account_src", columnList = "source_account_id"),
		@Index(name = "ix_tx_account_tgt", columnList = "target_account_id"),
		@Index(name = "ix_tx_status_created", columnList = "status, created_at"),
		@Index(name = "ix_tx_idem", columnList = "idempotency_key", unique = true)
})
public class Transaction extends BaseEntity {

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private TransactionType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private TransactionStatus status;

	@Column(name = "source_account_id")
	private UUID sourceAccountId;

	@Column(name = "target_account_id")
	private UUID targetAccountId;

	@Column(nullable = false)
	private Long amount;

	@Column(nullable = false)
	private Long fee;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(length = 255)
	private String description;

	@Column(name = "failure_reason", length = 255)
	private String failureReason;

	@Column(name = "ledger_transaction_id")
	private UUID ledgerTransactionId;

	@Version
	private long version;
}
