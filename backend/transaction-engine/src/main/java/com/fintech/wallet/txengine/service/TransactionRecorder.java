package com.fintech.wallet.txengine.service;

import com.fintech.wallet.common.constant.KafkaTopics;
import com.fintech.wallet.common.event.TransactionCompletedEvent;
import com.fintech.wallet.txengine.domain.OutboxEvent;
import com.fintech.wallet.txengine.domain.Transaction;
import com.fintech.wallet.txengine.domain.TransactionStatus;
import com.fintech.wallet.txengine.repository.OutboxEventRepository;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
	private final OutboxEventRepository outbox;
	private final ObjectMapper objectMapper;

	public TransactionRecorder(TransactionRepository transactions, OutboxEventRepository outbox,
			ObjectMapper objectMapper) {
		this.transactions = transactions;
		this.outbox = outbox;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public Transaction complete(Transaction transaction) {
		Transaction saved = transactions.save(transaction);
		if (saved.getStatus() == TransactionStatus.SUCCESS) {
			outbox.save(eventFor(saved));
		}
		return saved;
	}

	@Transactional
	public Transaction fail(Transaction transaction, String reason) {
		transaction.setStatus(TransactionStatus.FAILED);
		transaction.setFailureReason(reason);
		return transactions.save(transaction);
	}

	private OutboxEvent eventFor(Transaction transaction) {
		TransactionCompletedEvent event = new TransactionCompletedEvent(
				transaction.getId().toString(),
				transaction.getType().name(),
				transaction.getStatus().name(),
				text(transaction.getSourceAccountId()),
				text(transaction.getTargetAccountId()),
				transaction.getAmount(),
				transaction.getCurrency(),
				transaction.getDescription(),
				Instant.now());

		OutboxEvent outboxEvent = new OutboxEvent();
		outboxEvent.setAggregateType(AGGREGATE_TYPE);
		outboxEvent.setAggregateId(transaction.getId());
		outboxEvent.setEventType(KafkaTopics.TX_COMPLETED);
		outboxEvent.setPayload(write(event));
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
