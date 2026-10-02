package com.fintech.wallet.txengine;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.txengine.domain.OutboxEvent;
import com.fintech.wallet.txengine.dto.TransferRequest;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import com.fintech.wallet.txengine.support.AbstractTransactionIT;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class TransactionFlowIT extends AbstractTransactionIT {

	private static final UUID SOURCE = UUID.randomUUID();
	private static final UUID TARGET = UUID.randomUUID();
	private static final String TARGET_NUMBER = "9704000000123";

	@Test
	void sameIdempotencyKeyPostsOnceAndReplaysTheStoredResponse() {
		stubLedgerSuccess();
		String key = UUID.randomUUID().toString();

		var first = transactionService.transfer(key, transferRequest());
		var replay = transactionService.transfer(key, transferRequest());

		assertThat(first.replayed()).isFalse();
		assertThat(replay.replayed()).isTrue();
		assertThat(replay.response().transactionId()).isEqualTo(first.response().transactionId());
		assertThat(transactions.count()).isEqualTo(1);
	}

	@Test
	void sameKeyWithAnotherPayloadConflicts() {
		stubLedgerSuccess();
		String key = UUID.randomUUID().toString();
		transactionService.transfer(key, transferRequest());

		assertThatThrownBy(() -> transactionService.transfer(key,
				new TransferRequest(SOURCE, TARGET_NUMBER, 999_999, "VND", "different", null)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
	}

	@Test
	void ledgerUnavailableReturns503AndMovesNoMoney() {
		when(ledger.accountByNumber(anyString())).thenReturn(new LedgerClient.AccountInfo(TARGET, "VND"));
		when(ledger.postTransfer(any(), any(), any(), anyLong(), anyString(), any()))
				.thenThrow(new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE));

		assertThatThrownBy(() -> transactionService.transfer(UUID.randomUUID().toString(), transferRequest()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));

		assertThat(transactions.count()).isZero();
	}

	@Test
	void outboxRowIsWrittenTogetherWithTheStateChange() {
		stubLedgerSuccess();

		transactionService.transfer(UUID.randomUUID().toString(), transferRequest());

		assertThat(outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.NEW))
				.singleElement()
				.satisfies(event -> {
					assertThat(event.getEventType()).isEqualTo("tx.completed");
					assertThat(event.getAggregateType()).isEqualTo("TRANSACTION");
				});
	}

	@Test
	void velocityRuleBlocksAndRecordsAnAlert() {
		stubLedgerSuccess();
		for (int i = 0; i < 10; i++) {
			transactionService.transfer(UUID.randomUUID().toString(), transferRequest());
		}

		assertThatThrownBy(() -> transactionService.transfer(UUID.randomUUID().toString(), transferRequest()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FRAUD_BLOCKED));

		assertThat(fraudAlerts.count()).isEqualTo(1);
	}

	private void stubLedgerSuccess() {
		when(ledger.accountByNumber(anyString())).thenReturn(new LedgerClient.AccountInfo(TARGET, "VND"));
		when(ledger.postTransfer(any(), any(), any(), anyLong(), anyString(), any()))
				.thenAnswer(invocation -> new LedgerClient.PostingOutcome(invocation.getArgument(0), 0, 0));
	}

	private static TransferRequest transferRequest() {
		return new TransferRequest(SOURCE, TARGET_NUMBER, 1_000, "VND", "integration", null);
	}
}
