package com.fintech.wallet.txengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FraudRuleEngineTest {

	private static final UUID ACCOUNT_ID = UUID.randomUUID();

	private final TransactionRepository transactions = mock(TransactionRepository.class);
	private final FraudRuleEngine engine = new FraudRuleEngine(transactions, new ObjectMapper(), 1_000_000, 3,
			Duration.ofMinutes(1));

	@Test
	void blocksAmountAboveTheLimitWithoutTouchingTheRepository() {
		var decision = engine.evaluate(ACCOUNT_ID, 2_000_000);

		assertThat(decision).isPresent();
		assertThat(decision.get().ruleCode()).isEqualTo("AMOUNT_LIMIT");
		assertThat(decision.get().details()).contains("\"rule\":\"AMOUNT_LIMIT\"");
		verify(transactions, never()).countSettledBySourceAccountIdAfter(any(), any());
	}

	@Test
	void blocksWhenVelocityReachesTheThreshold() {
		when(transactions.countSettledBySourceAccountIdAfter(any(), any())).thenReturn(3L);

		var decision = engine.evaluate(ACCOUNT_ID, 100);

		assertThat(decision).isPresent();
		assertThat(decision.get().ruleCode()).isEqualTo("VELOCITY");
	}

	@Test
	void allowsWhenBothRulesPass() {
		when(transactions.countSettledBySourceAccountIdAfter(any(), any())).thenReturn(1L);

		assertThat(engine.evaluate(ACCOUNT_ID, 100)).isEmpty();
	}
}
