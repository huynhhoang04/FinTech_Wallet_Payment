package com.fintech.wallet.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "payment_intents", indexes = {
		@Index(name = "ix_pi_idem", columnList = "idempotency_key", unique = true),
		@Index(name = "ix_pi_status_created", columnList = "status, created_at"),
		@Index(name = "ix_pi_provider_ref", columnList = "provider, provider_ref")
})
public class PaymentIntent extends BaseEntity {

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
	private String idempotencyKey;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "wallet_account_id", nullable = false)
	private UUID walletAccountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private Provider provider;

	@Column(name = "provider_ref", length = 64)
	private String providerRef;

	@Column(nullable = false)
	private Long amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private PaymentStatus status;

	@Column(name = "checkout_url", length = 512)
	private String checkoutUrl;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Version
	private long version;

	public enum Provider {
		VNPAY,
		STRIPE,
		VIETQR
	}

	public enum PaymentStatus {
		CREATED,
		PENDING,
		SUCCEEDED,
		FAILED,
		EXPIRED
	}
}
