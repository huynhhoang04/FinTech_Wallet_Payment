package com.fintech.wallet.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users", uniqueConstraints = {
		@UniqueConstraint(name = "uq_users_phone", columnNames = "phone"),
		@UniqueConstraint(name = "uq_users_email", columnNames = "email")
})
public class User extends BaseEntity {

	@Column(nullable = false, length = 20)
	private String phone;

	@Column(nullable = false, length = 120)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 120)
	private String fullName;

	@Enumerated(EnumType.STRING)
	@Column(name = "kyc_status", nullable = false, length = 12)
	private KycStatus kycStatus = KycStatus.PENDING;

	@Version
	private long version;
}
