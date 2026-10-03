package com.fintech.wallet.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "reconciliation_reports", uniqueConstraints =
		@UniqueConstraint(name = "uq_recon_date", columnNames = { "report_date" }))
public class ReconciliationReport {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "report_date", nullable = false)
	private LocalDate reportDate;

	@Column(name = "entry_count", nullable = false)
	private long entryCount;

	@Column(name = "total_debit", nullable = false)
	private Long totalDebit;

	@Column(name = "total_credit", nullable = false)
	private Long totalCredit;

	@Column(nullable = false)
	private Long difference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private ReconStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String details;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(nullable = false)
	private Instant updatedAt;

	public enum ReconStatus {
		BALANCED,
		IMBALANCED,
		FAILED
	}
}
