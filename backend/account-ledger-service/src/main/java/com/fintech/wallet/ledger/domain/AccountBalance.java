package com.fintech.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "account_balances", uniqueConstraints =
		@UniqueConstraint(name = "uq_balance_account_currency", columnNames = { "account_id", "currency" }))
public class AccountBalance extends BaseEntity {

	@Column(name = "account_id", nullable = false)
	private UUID accountId;

	@Column(nullable = false)
	private Long balance;

	@Column(nullable = false, length = 3)
	private String currency;

	@Version
	private long version;
}
