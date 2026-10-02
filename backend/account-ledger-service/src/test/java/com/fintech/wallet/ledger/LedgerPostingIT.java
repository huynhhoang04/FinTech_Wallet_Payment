package com.fintech.wallet.ledger;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.ledger.support.AbstractLedgerIT;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class LedgerPostingIT extends AbstractLedgerIT {

	@Test
	void transferWritesBalancedEntries() {
		UUID source = seedUserAccount(1_000_000);
		UUID target = seedUserAccount(0);
		UUID transactionId = UUID.randomUUID();

		postingService.postTransfer(transactionId, source, target, 100_000, "VND", "TX-transfer");

		assertThat(entryRepository.sumByTransactionId(transactionId)).isZero();
		assertThat(entryRepository.findByTransactionId(transactionId)).hasSize(2);
		assertThat(balanceOf(source)).isEqualTo(900_000);
		assertThat(balanceOf(target)).isEqualTo(100_000);
	}

	@Test
	void snapshotMatchesEntrySum() {
		UUID source = seedUserAccount(500_000);
		UUID target = seedUserAccount(0);

		postingService.postTransfer(UUID.randomUUID(), source, target, 200_000, "VND", "TX-snapshot");

		assertThat(balanceOf(source)).isEqualTo(entryRepository.sumByAccountId(source));
		assertThat(balanceOf(target)).isEqualTo(entryRepository.sumByAccountId(target));
		assertThat(entryRepository.totalAmount()).isZero();
	}

	@Test
	void insufficientFundsWritesNoEntries() {
		UUID source = seedUserAccount(50_000);
		UUID target = seedUserAccount(0);
		UUID transactionId = UUID.randomUUID();

		assertThatThrownBy(() -> postingService.postTransfer(transactionId, source, target, 100_000, "VND", "TX-fail"))
				.isInstanceOfSatisfying(BusinessException.class,
						throwable -> assertThat(throwable.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));

		assertThat(entryRepository.findByTransactionId(transactionId)).isEmpty();
		assertThat(balanceOf(source)).isEqualTo(50_000);
		assertThat(balanceOf(target)).isZero();
	}

	@Test
	void systemAccountMayGoNegative() {
		UUID wallet = seedUserAccount(0);

		postingService.deposit(UUID.randomUUID(), wallet, 300_000, "VND", "VIETQR");

		assertThat(balanceOf(wallet)).isEqualTo(300_000);
		assertThat(balanceOf(suspenseAccountId())).isEqualTo(-300_000);
	}

	@Test
	void withdrawRejectsGoingNegativeOnUserAccount() {
		UUID wallet = seedUserAccount(100_000);
		UUID transactionId = UUID.randomUUID();

		assertThatThrownBy(() -> postingService.withdraw(transactionId, wallet, 500_000, "VND", "VCB"))
				.isInstanceOfSatisfying(BusinessException.class,
						throwable -> assertThat(throwable.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));

		assertThat(entryRepository.findByTransactionId(transactionId)).isEmpty();
		assertThat(balanceOf(wallet)).isEqualTo(100_000);
	}
}
