package com.fintech.wallet.txengine.service;

import com.fintech.wallet.txengine.domain.FraudAlert;
import com.fintech.wallet.txengine.repository.FraudAlertRepository;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Embedded rule engine (docs 02 section 3.4). Only the two rules kept by the schedule cut list
 * (docs 06 section 3) are implemented: VELOCITY and AMOUNT_LIMIT. NEW_DEVICE and GEO_ANOMALY would
 * need device/geo data the wallet does not collect yet.
 */
@Slf4j
@Service
public class FraudRuleEngine {

	private final TransactionRepository transactions;
	private final FraudAlertRepository alerts;
	private final long amountLimit;
	private final int velocityMax;
	private final Duration velocityWindow;

	public FraudRuleEngine(TransactionRepository transactions,
			FraudAlertRepository alerts,
			@Value("${app.fraud.amount-limit}") long amountLimit,
			@Value("${app.fraud.velocity-max}") int velocityMax,
			@Value("${app.fraud.velocity-window}") Duration velocityWindow) {
		this.transactions = transactions;
		this.alerts = alerts;
		this.amountLimit = amountLimit;
		this.velocityMax = velocityMax;
		this.velocityWindow = velocityWindow;
	}

	/** Empty when the request may proceed; otherwise the alert that blocked it is already stored. */
	public Optional<FraudAlert> evaluate(UUID transactionId, UUID sourceAccountId, long amount) {
		if (amount > amountLimit) {
			return Optional.of(record(transactionId, "AMOUNT_LIMIT", FraudAlert.Severity.HIGH,
					"amount=" + amount + " limit=" + amountLimit));
		}
		Instant since = Instant.now().minus(velocityWindow);
		long recent = transactions.countBySourceAccountIdAndCreatedAtAfter(sourceAccountId, since);
		if (recent >= velocityMax) {
			return Optional.of(record(transactionId, "VELOCITY", FraudAlert.Severity.HIGH,
					recent + " transactions in the last " + velocityWindow));
		}
		return Optional.empty();
	}

	private FraudAlert record(UUID transactionId, String ruleCode, FraudAlert.Severity severity, String details) {
		FraudAlert alert = new FraudAlert();
		alert.setTransactionId(transactionId);
		alert.setRuleCode(ruleCode);
		alert.setSeverity(severity);
		alert.setDecision(FraudAlert.Decision.BLOCK);
		alert.setDetails("{\"rule\":\"" + ruleCode + "\",\"detail\":\"" + details + "\"}");
		log.warn("Fraud rule {} blocked transaction {}: {}", ruleCode, transactionId, details);
		return alerts.save(alert);
	}
}
