package com.fintech.wallet.ledger;

import com.fintech.wallet.ledger.support.AbstractLedgerIT;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers(disabledWithoutDocker = true)
class LedgerConcurrencyIT extends AbstractLedgerIT {

	@Test
	void concurrentOppositeTransfersDoNotDeadlock() throws Exception {
		UUID accountA = seedUserAccount(1_000_000);
		UUID accountB = seedUserAccount(1_000_000);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<?> aToB = pool.submit(() -> {
				start.await();
				return postingService.postTransfer(UUID.randomUUID(), accountA, accountB, 100_000, "VND", "TX-ab");
			});
			Future<?> bToA = pool.submit(() -> {
				start.await();
				return postingService.postTransfer(UUID.randomUUID(), accountB, accountA, 100_000, "VND", "TX-ba");
			});
			start.countDown();

			assertThatCode(() -> {
				aToB.get(10, TimeUnit.SECONDS);
				bToA.get(10, TimeUnit.SECONDS);
			}).doesNotThrowAnyException();
		} finally {
			pool.shutdownNow();
		}

		assertThat(balanceOf(accountA)).isEqualTo(1_000_000);
		assertThat(balanceOf(accountB)).isEqualTo(1_000_000);
		assertThat(entryRepository.totalAmount()).isZero();
	}

	@Test
	void manyConcurrentTransfersKeepTheLedgerBalanced() throws Exception {
		UUID source = seedUserAccount(1_000_000);
		UUID target = seedUserAccount(0);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<?>[] futures = new Future<?>[8];
			for (int i = 0; i < futures.length; i++) {
				futures[i] = pool.submit(() -> {
					start.await();
					return postingService.postTransfer(UUID.randomUUID(), source, target, 10_000, "VND", "TX-load");
				});
			}
			start.countDown();
			for (Future<?> future : futures) {
				future.get(20, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		assertThat(balanceOf(source)).isEqualTo(entryRepository.sumByAccountId(source));
		assertThat(balanceOf(target)).isEqualTo(entryRepository.sumByAccountId(target));
		assertThat(balanceOf(source) + balanceOf(target)).isEqualTo(1_000_000);
		assertThat(entryRepository.findUnbalancedTransactionIds()).isEmpty();
		assertThat(entryRepository.totalAmount()).isZero();
	}
}
