package com.fintech.wallet.txengine;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.txengine.domain.OutboxEvent;
import com.fintech.wallet.txengine.dto.TransferRequest;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import com.fintech.wallet.txengine.support.AbstractTransactionIT;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class TransactionFlowIT extends AbstractTransactionIT {

	private static final UUID SOURCE = UUID.randomUUID();
	private static final UUID TARGET = UUID.randomUUID();
	private static final String TARGET_NUMBER = "9704000000123";
	private static final String USER_ID = UUID.randomUUID().toString();

	@Test
	void sameIdempotencyKeyPostsOnceAndReplaysTheStoredResponse() {
		stubLedgerSuccess();
		String key = UUID.randomUUID().toString();

		var first = transactionService.transfer(key, USER_ID, transferRequest());
		var replay = transactionService.transfer(key, USER_ID, transferRequest());

		assertThat(first.replayed()).isFalse();
		assertThat(replay.replayed()).isTrue();
		assertThat(replay.response().transactionId()).isEqualTo(first.response().transactionId());
		assertThat(transactions.count()).isEqualTo(1);
	}

	/** The ledger id must be derived from the key, or a retry after a timeout posts twice (FIN-01). */
	@Test
	void ledgerTransactionIdIsDerivedFromTheIdempotencyKey() {
		stubLedgerSuccess();
		String key = UUID.randomUUID().toString();

		transactionService.transfer(key, USER_ID, transferRequest());

		ArgumentCaptor<UUID> ledgerTransactionId = ArgumentCaptor.forClass(UUID.class);
		verify(ledger).postTransfer(ledgerTransactionId.capture(), any(), any(), anyLong(), anyString(), any());
		assertThat(ledgerTransactionId.getValue()).isEqualTo(derivedLedgerId(key));
	}

	@Test
	void sameKeyWithAnotherPayloadConflicts() {
		stubLedgerSuccess();
		String key = UUID.randomUUID().toString();
		transactionService.transfer(key, USER_ID, transferRequest());

		assertThatThrownBy(() -> transactionService.transfer(key, USER_ID,
				new TransferRequest(SOURCE, TARGET_NUMBER, 999_999, "VND", "different", null)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
	}

	@Test
	void ledgerUnavailableReturns503AndMovesNoMoney() {
		stubLedgerAccount();
		when(ledger.postTransfer(any(), any(), any(), anyLong(), anyString(), any()))
				.thenThrow(new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE));

		assertThatThrownBy(() -> transactionService.transfer(UUID.randomUUID().toString(), USER_ID, transferRequest()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));

		assertThat(transactions.count()).isZero();
	}

	@Test
	void outboxRowIsWrittenTogetherWithTheStateChange() {
		stubLedgerSuccess();

		transactionService.transfer(UUID.randomUUID().toString(), USER_ID, transferRequest());

		assertThat(outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.NEW))
				.singleElement()
				.satisfies(event -> {
					assertThat(event.getEventType()).isEqualTo("tx.completed");
					assertThat(event.getAggregateType()).isEqualTo("TRANSACTION");
				});
	}

	@Test
	void velocityRuleBlocksAndAnnouncesItInTheOutbox() {
		stubLedgerSuccess();
		for (int i = 0; i < 10; i++) {
			transactionService.transfer(UUID.randomUUID().toString(), USER_ID, transferRequest());
		}

		assertThatThrownBy(() -> transactionService.transfer(UUID.randomUUID().toString(), USER_ID, transferRequest()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.FRAUD_BLOCKED));

		assertThat(fraudAlerts.count()).isEqualTo(1);
		assertThat(outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.NEW))
				.anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("risk.alert"));
	}

	private void stubLedgerSuccess() {
		stubLedgerAccount();
		when(ledger.postTransfer(any(), any(), any(), anyLong(), anyString(), any()))
				.thenAnswer(invocation -> new LedgerClient.PostingOutcome(invocation.getArgument(0), 0, 0));
	}

	private void stubLedgerAccount() {
		when(ledger.accountByNumber(anyString()))
				.thenReturn(new LedgerClient.AccountInfo(TARGET, TARGET_NUMBER, "VND", "ACTIVE"));
	}

	private static UUID derivedLedgerId(String key) {
		return UUID.nameUUIDFromBytes(("ledger-tx:" + key).getBytes(StandardCharsets.UTF_8));
	}

	private static TransferRequest transferRequest() {
		return new TransferRequest(SOURCE, TARGET_NUMBER, 1_000, "VND", "integration", null);
	}
}
