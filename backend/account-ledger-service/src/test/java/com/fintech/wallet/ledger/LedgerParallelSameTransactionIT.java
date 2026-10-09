package com.fintech.wallet.ledger;

import com.fintech.wallet.ledger.support.AbstractLedgerIT;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Two requests carrying the same transaction id at the same moment. The sequential idempotency test
 * passes even when the post-lock replay check is missing, which is why this one exists (docs 07
 * FIN-02).
 */
@Testcontainers(disabledWithoutDocker = true)
class LedgerParallelSameTransactionIT extends AbstractLedgerIT {

	@Test
	void parallelCallsWithTheSameTransactionIdPostOnce() throws Exception {
		UUID source = seedUserAccount(1_000_000);
		UUID target = seedUserAccount(0);
		UUID transactionId = UUID.randomUUID();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Callable<Object> post = () -> {
				start.await();
				return postingService.postTransfer(transactionId, source, target, 10_000, "VND", "TX-parallel");
			};
			var first = pool.submit(post);
			var second = pool.submit(post);
			start.countDown();

			assertThatCode(() -> {
				first.get(20, TimeUnit.SECONDS);
				second.get(20, TimeUnit.SECONDS);
			}).doesNotThrowAnyException();
		} finally {
			pool.shutdownNow();
		}

		assertThat(entryRepository.findByTransactionId(transactionId)).hasSize(2);
		assertThat(balanceOf(source)).isEqualTo(990_000);
		assertThat(balanceOf(target)).isEqualTo(10_000);
		assertThat(entryRepository.totalAmount()).isZero();
	}
}
