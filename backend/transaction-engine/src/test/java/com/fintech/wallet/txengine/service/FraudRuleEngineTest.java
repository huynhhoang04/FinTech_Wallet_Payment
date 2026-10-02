package com.fintech.wallet.txengine.service;

import com.fintech.wallet.txengine.domain.FraudAlert;
import com.fintech.wallet.txengine.repository.FraudAlertRepository;
import com.fintech.wallet.txengine.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FraudRuleEngineTest {

	private static final UUID TRANSACTION_ID = UUID.randomUUID();
	private static final UUID ACCOUNT_ID = UUID.randomUUID();

	private final TransactionRepository transactions = mock(TransactionRepository.class);
	private final FraudAlertRepository alerts = mock(FraudAlertRepository.class);
	private final FraudRuleEngine engine = new FraudRuleEngine(transactions, alerts, 1_000_000, 3,
			Duration.ofMinutes(1));

	@Test
	void blocksAmountAboveTheLimit() {
		when(alerts.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var alert = engine.evaluate(TRANSACTION_ID, ACCOUNT_ID, 2_000_000);

		assertThat(alert).isPresent();
		assertThat(alert.get().getRuleCode()).isEqualTo("AMOUNT_LIMIT");
		assertThat(alert.get().getDecision()).isEqualTo(FraudAlert.Decision.BLOCK);
		verifyNoInteractions(transactions);
	}

	@Test
	void blocksWhenVelocityReachesTheThreshold() {
		when(transactions.countBySourceAccountIdAndCreatedAtAfter(any(), any())).thenReturn(3L);
		when(alerts.save(any(FraudAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var alert = engine.evaluate(TRANSACTION_ID, ACCOUNT_ID, 100);

		assertThat(alert).isPresent();
		assertThat(alert.get().getRuleCode()).isEqualTo("VELOCITY");
	}

	@Test
	void allowsWhenBothRulesPass() {
		when(transactions.countBySourceAccountIdAndCreatedAtAfter(any(), any())).thenReturn(1L);

		assertThat(engine.evaluate(TRANSACTION_ID, ACCOUNT_ID, 100)).isEmpty();
		verifyNoInteractions(alerts);
	}
}
