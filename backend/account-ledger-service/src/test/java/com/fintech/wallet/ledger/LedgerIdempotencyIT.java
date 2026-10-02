package com.fintech.wallet.ledger;

import com.fintech.wallet.ledger.domain.LedgerStatus;
import com.fintech.wallet.ledger.service.TransferResult;
import com.fintech.wallet.ledger.support.AbstractLedgerIT;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LedgerIdempotencyIT extends AbstractLedgerIT {

	@Test
	void sameTransactionIdDoesNotDoublePost() {
		UUID source = seedUserAccount(1_000_000);
		UUID target = seedUserAccount(0);
		UUID transactionId = UUID.randomUUID();

		TransferResult first = postingService.postTransfer(transactionId, source, target, 100_000, "VND", "TX-idem");
		TransferResult replay = postingService.postTransfer(transactionId, source, target, 100_000, "VND", "TX-idem");

		assertThat(first.status()).isEqualTo(LedgerStatus.POSTED);
		assertThat(replay.status()).isEqualTo(LedgerStatus.POSTED);
		assertThat(replay.sourceBalanceAfter()).isEqualTo(first.sourceBalanceAfter());
		assertThat(replay.targetBalanceAfter()).isEqualTo(first.targetBalanceAfter());
		assertThat(entryRepository.findByTransactionId(transactionId)).hasSize(2);
		assertThat(balanceOf(source)).isEqualTo(900_000);
		assertThat(balanceOf(target)).isEqualTo(100_000);
	}

	@Test
	void sameTransactionIdForDifferentAccountsIsRejected() {
		UUID source = seedUserAccount(1_000_000);
		UUID target = seedUserAccount(0);
		UUID other = seedUserAccount(0);
		UUID transactionId = UUID.randomUUID();

		postingService.postTransfer(transactionId, source, target, 100_000, "VND", "TX-conflict");

		assertThat(org.assertj.core.api.Assertions
				.catchThrowable(() -> postingService.postTransfer(transactionId, source, other, 100_000, "VND", "TX-conflict")))
				.isInstanceOf(com.fintech.wallet.common.error.BusinessException.class);

		assertThat(balanceOf(other)).isZero();
		assertThat(entryRepository.findByTransactionId(transactionId)).hasSize(2);
	}
}
