package com.fintech.wallet.ledger.domain;

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
@Table(name = "accounts", indexes = {
		@Index(name = "ix_acc_owner", columnList = "owner_user_id"),
		@Index(name = "ix_acc_type_status", columnList = "type, status")
})
public class Account extends BaseEntity {

	@Column(name = "owner_user_id")
	private UUID ownerUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private AccountType type;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private AccountStatus status;

	@Column(name = "account_number", nullable = false, unique = true, length = 24)
	private String accountNumber;

	@Version
	private long version;
}
