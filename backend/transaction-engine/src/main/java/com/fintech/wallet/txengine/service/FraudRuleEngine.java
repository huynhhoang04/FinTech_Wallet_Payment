package com.fintech.wallet.txengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.txengine.domain.FraudAlert;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Embedded rule engine (docs 02 section 3.4). Only the two rules kept by the schedule cut list
 * (docs 06 section 3) are implemented: VELOCITY and AMOUNT_LIMIT. NEW_DEVICE and GEO_ANOMALY would
 * need device/geo data the wallet does not collect yet.
 *
 * <p>It only decides: persisting the alert and the {@code risk.alert} outbox row happens in
 * {@link TransactionRecorder} so both land in one transaction (docs 07 FIN-04).
 */
@Slf4j
@Service
public class FraudRuleEngine {

	private static final String AMOUNT_LIMIT_RULE = "AMOUNT_LIMIT";
	private static final String VELOCITY_RULE = "VELOCITY";

	private final TransactionRepository transactions;
	private final ObjectMapper objectMapper;
	private final long amountLimit;
	private final int velocityMax;
	private final Duration velocityWindow;

	public FraudRuleEngine(TransactionRepository transactions,
			ObjectMapper objectMapper,
			@Value("${app.fraud.amount-limit}") long amountLimit,
			@Value("${app.fraud.velocity-max}") int velocityMax,
			@Value("${app.fraud.velocity-window}") Duration velocityWindow) {
		this.transactions = transactions;
		this.objectMapper = objectMapper;
		this.amountLimit = amountLimit;
		this.velocityMax = velocityMax;
		this.velocityWindow = velocityWindow;
	}

	/** Empty when the request may proceed. */
	public Optional<FraudDecision> evaluate(UUID sourceAccountId, long amount) {
		if (amount > amountLimit) {
			return Optional.of(new FraudDecision(AMOUNT_LIMIT_RULE, FraudAlert.Severity.HIGH,
					details(AMOUNT_LIMIT_RULE, "amount=" + amount + " limit=" + amountLimit)));
		}
		Instant since = Instant.now().minus(velocityWindow);
		long recent = transactions.countSettledBySourceAccountIdAfter(sourceAccountId, since);
		if (recent >= velocityMax) {
			return Optional.of(new FraudDecision(VELOCITY_RULE, FraudAlert.Severity.HIGH,
					details(VELOCITY_RULE, recent + " transactions in the last " + velocityWindow)));
		}
		return Optional.empty();
	}

	private String details(String ruleCode, String detail) {
		try {
			return objectMapper.writeValueAsString(Map.of("rule", ruleCode, "detail", detail));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise fraud details", e);
		}
	}

	public record FraudDecision(String ruleCode, FraudAlert.Severity severity, String details) {
	}
}
