package com.fintech.wallet.common.constant;

public final class KafkaTopics {

	public static final String TX_COMPLETED = "tx.completed";
	public static final String PAYMENT_WEBHOOK_RECEIVED = "payment.webhook.received";
	public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
	public static final String RISK_ALERT = "risk.alert";
	public static final String AUDIT_EVENT = "audit.event";

	private KafkaTopics() {
	}
}
