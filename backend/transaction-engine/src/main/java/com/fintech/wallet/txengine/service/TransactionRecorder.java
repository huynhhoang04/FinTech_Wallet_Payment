package com.fintech.wallet.txengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.KafkaTopics;
import com.fintech.wallet.common.event.TransactionCompletedEvent;
import com.fintech.wallet.txengine.domain.FraudAlert;
import com.fintech.wallet.txengine.domain.OutboxEvent;
import com.fintech.wallet.txengine.domain.Transaction;
import com.fintech.wallet.txengine.domain.TransactionStatus;
import com.fintech.wallet.txengine.repository.FraudAlertRepository;
import com.fintech.wallet.txengine.repository.OutboxEventRepository;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The database transaction boundary of the money path. The state change and the outbox row are
 * written together, so an event can never be lost when Kafka is down (docs 03 section 3.3). The
 * gRPC call and the distributed lock deliberately stay outside this transaction.
 */
@Service
public class TransactionRecorder {

	private static final String AGGREGATE_TYPE = "TRANSACTION";

	private final TransactionRepository transactions;
	private final FraudAlertRepository fraudAlerts;
	private final OutboxEventRepository outbox;
	private final ObjectMapper objectMapper;

	public TransactionRecorder(TransactionRepository transactions, FraudAlertRepository fraudAlerts,
			OutboxEventRepository outbox, ObjectMapper objectMapper) {
		this.transactions = transactions;
		this.fraudAlerts = fraudAlerts;
		this.outbox = outbox;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public Transaction complete(Transaction transaction) {
		Transaction saved = transactions.save(transaction);
		if (saved.getStatus() == TransactionStatus.SUCCESS) {
			outbox.save(transactionEvent(saved));
		}
		return saved;
	}

	@Transactional
	public Transaction fail(Transaction transaction, String reason) {
		transaction.setStatus(TransactionStatus.FAILED);
		transaction.setFailureReason(reason);
		return transactions.save(transaction);
	}

	/**
	 * Fraud alert and its {@code risk.alert} event in one transaction, and the transaction marked
	 * FAILED, so a blocked attempt is both recorded and announced (docs 07 FIN-04).
	 */
	@Transactional
	public Transaction recordFraud(Transaction transaction, FraudRuleEngine.FraudDecision decision) {
		FraudAlert alert = new FraudAlert();
		alert.setTransactionId(transaction.getId());
		alert.setRuleCode(decision.ruleCode());
		alert.setSeverity(decision.severity());
		alert.setDecision(FraudAlert.Decision.BLOCK);
		alert.setDetails(decision.details());
		fraudAlerts.save(alert);

		Transaction failed = fail(transaction, "FRAUD_BLOCKED:" + decision.ruleCode());
		outbox.save(riskAlertEvent(transaction, decision));
		return failed;
	}

	private OutboxEvent transactionEvent(Transaction transaction) {
		TransactionCompletedEvent event = new TransactionCompletedEvent(
				text(transaction.getId()),
				transaction.getType().name(),
				transaction.getStatus().name(),
				text(transaction.getSourceAccountId()),
				text(transaction.getTargetAccountId()),
				transaction.getAmount(),
				transaction.getCurrency(),
				transaction.getDescription(),
				Instant.now());
		return outboxEvent(transaction.getId(), KafkaTopics.TX_COMPLETED, write(event));
	}

	private OutboxEvent riskAlertEvent(Transaction transaction, FraudRuleEngine.FraudDecision decision) {
		Map<String, Object> payload = Map.of(
				"transactionId", text(transaction.getId()),
				"accountId", text(transaction.getSourceAccountId()),
				"rule", decision.ruleCode(),
				"severity", decision.severity().name(),
				"amount", transaction.getAmount(),
				"currency", transaction.getCurrency(),
				"occurredAt", Instant.now().toString());
		return outboxEvent(transaction.getId(), KafkaTopics.RISK_ALERT, write(payload));
	}

	private OutboxEvent outboxEvent(UUID aggregateId, String eventType, String payload) {
		OutboxEvent outboxEvent = new OutboxEvent();
		outboxEvent.setAggregateType(AGGREGATE_TYPE);
		outboxEvent.setAggregateId(aggregateId);
		outboxEvent.setEventType(eventType);
		outboxEvent.setPayload(payload);
		outboxEvent.setStatus(OutboxEvent.Status.NEW);
		return outboxEvent;
	}

	private String write(Object payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise outbox payload", e);
		}
	}

	private static String text(UUID value) {
		return value == null ? null : value.toString();
	}
}
