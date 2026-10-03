package com.fintech.wallet.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "notification_logs", indexes = {
		@Index(name = "ix_nf_user_created", columnList = "user_id, created_at"),
		@Index(name = "ix_nf_status", columnList = "status")
})
public class NotificationLog extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private Channel channel;

	@Column(name = "template_code", nullable = false, length = 32)
	private String templateCode;

	@Column(nullable = false, length = 255)
	private String recipient;

	@Column(length = 255)
	private String subject;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 8)
	private SendStatus status;

	@Column(name = "provider_message_id", length = 128)
	private String providerMessageId;

	@Column(name = "error_message", length = 512)
	private String errorMessage;

	@Column(name = "related_transaction_id")
	private UUID relatedTransactionId;

	@Column(name = "sent_at")
	private Instant sentAt;

	public enum Channel {
		EMAIL,
		PUSH,
		SMS
	}

	public enum SendStatus {
		PENDING,
		SENT,
		FAILED
	}
}
