package com.fintech.wallet.txengine.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.KafkaTopics;
import com.fintech.wallet.common.event.PaymentSucceededEvent;
import com.fintech.wallet.txengine.domain.Transaction;
import com.fintech.wallet.txengine.domain.TransactionStatus;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import com.fintech.wallet.txengine.service.TransactionRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Credits the wallet when a provider confirms the deposit - the step that closes the deposit loop
 * (docs 02 section 4.5). Delivery is at least once, so the transaction is ignored when already
 * SUCCESS and the ledger transaction id is derived from the payment intent, which makes a
 * redelivery a replay at the ledger rather than a second credit.
 */
@Slf4j
@Component
public class PaymentSucceededConsumer {

	private static final String LEDGER_ID_PREFIX = "ledger-deposit:";

	private final TransactionRepository transactions;
	private final LedgerClient ledger;
	private final TransactionRecorder recorder;
	private final ObjectMapper objectMapper;

	public PaymentSucceededConsumer(TransactionRepository transactions, LedgerClient ledger,
			TransactionRecorder recorder, ObjectMapper objectMapper) {
		this.transactions = transactions;
		this.ledger = ledger;
		this.recorder = recorder;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = KafkaTopics.PAYMENT_SUCCEEDED, groupId = "${spring.kafka.consumer.group-id}")
	public void onPaymentSucceeded(String payload) {
		PaymentSucceededEvent event = read(payload);
		if (event.paymentIntentId() == null) {
			log.warn("payment.succeeded without a payment intent id, ignoring: {}", payload);
			return;
		}
		transactions.findByPaymentIntentId(UUID.fromString(event.paymentIntentId()))
				.ifPresentOrElse(this::settle, () -> log.warn("payment.succeeded for unknown intent {}",
						event.paymentIntentId()));
	}

	private void settle(Transaction transaction) {
		if (transaction.getStatus() == TransactionStatus.SUCCESS) {
			log.debug("Deposit for intent {} already settled", transaction.getPaymentIntentId());
			return;
		}
		UUID ledgerTransactionId = UUID.nameUUIDFromBytes(
				(LEDGER_ID_PREFIX + transaction.getPaymentIntentId()).getBytes(StandardCharsets.UTF_8));
		LedgerClient.PostingOutcome outcome = ledger.deposit(ledgerTransactionId, transaction.getTargetAccountId(),
				transaction.getAmount(), transaction.getCurrency(), "PROVIDER");
		transaction.setLedgerTransactionId(outcome.ledgerTransactionId());
		transaction.setStatus(TransactionStatus.SUCCESS);
		recorder.complete(transaction);
		log.info("Deposit {} credited from payment intent {}", transaction.getId(), transaction.getPaymentIntentId());
	}

	private PaymentSucceededEvent read(String payload) {
		try {
			return objectMapper.readValue(payload, PaymentSucceededEvent.class);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot read payment.succeeded payload: " + payload, e);
		}
	}
}
