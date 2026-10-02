package com.fintech.wallet.ledger.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerEntryFactoryTest {

	private final UUID transactionId = UUID.randomUUID();
	private final UUID accountA = UUID.randomUUID();
	private final UUID accountB = UUID.randomUUID();
	private final String currency = "VND";

	@Test
	void transferWritesDebitAndCreditSummingToZero() {
		List<LedgerEntry> entries = LedgerEntryFactory.transfer(transactionId, accountA, accountB, 100_000, currency,
				"TX-1", 900_000, 300_000);

		assertThat(entries).hasSize(2);
		assertThat(LedgerEntryFactory.sum(entries)).isZero();
		assertThat(entries).anySatisfy(entry -> {
			assertThat(entry.getAccountId()).isEqualTo(accountA);
			assertThat(entry.getDirection()).isEqualTo(EntryDirection.DEBIT);
			assertThat(entry.getAmount()).isEqualTo(-100_000);
			assertThat(entry.getBalanceAfter()).isEqualTo(900_000);
		});
		assertThat(entries).anySatisfy(entry -> {
			assertThat(entry.getAccountId()).isEqualTo(accountB);
			assertThat(entry.getDirection()).isEqualTo(EntryDirection.CREDIT);
			assertThat(entry.getAmount()).isEqualTo(100_000);
			assertThat(entry.getBalanceAfter()).isEqualTo(300_000);
		});
	}

	@Test
	void depositDebitsSuspenseAndCreditsWallet() {
		List<LedgerEntry> entries = LedgerEntryFactory.deposit(transactionId, accountB, accountA, 500_000, currency,
				"VIETQR", -500_000, 500_000);

		assertThat(LedgerEntryFactory.sum(entries)).isZero();
		assertThat(entries).anySatisfy(entry -> {
			assertThat(entry.getAccountId()).isEqualTo(accountB);
			assertThat(entry.getDirection()).isEqualTo(EntryDirection.DEBIT);
		});
		assertThat(entries).anySatisfy(entry -> {
			assertThat(entry.getAccountId()).isEqualTo(accountA);
			assertThat(entry.getDirection()).isEqualTo(EntryDirection.CREDIT);
		});
	}

	@Test
	void withdrawDebitsWalletAndCreditsSuspense() {
		List<LedgerEntry> entries = LedgerEntryFactory.withdraw(transactionId, accountA, accountB, 200_000, currency,
				"VCB", 300_000, 200_000);

		assertThat(LedgerEntryFactory.sum(entries)).isZero();
		assertThat(entries).anySatisfy(entry -> {
			assertThat(entry.getAccountId()).isEqualTo(accountA);
			assertThat(entry.getDirection()).isEqualTo(EntryDirection.DEBIT);
		});
		assertThat(entries).anySatisfy(entry -> {
			assertThat(entry.getAccountId()).isEqualTo(accountB);
			assertThat(entry.getDirection()).isEqualTo(EntryDirection.CREDIT);
		});
	}

	@Test
	void everyEntryKeepsDirectionAgreeingWithSign() {
		List<LedgerEntry> entries = LedgerEntryFactory.transfer(transactionId, accountA, accountB, 1, currency, null,
				0, 1);

		entries.forEach(entry -> {
			if (entry.getDirection() == EntryDirection.DEBIT) {
				assertThat(entry.getAmount()).isNegative();
			} else {
				assertThat(entry.getAmount()).isPositive();
			}
		});
	}

	@Test
	void rejectsNonPositiveAmount() {
		assertThatThrownBy(() -> LedgerEntryFactory.transfer(transactionId, accountA, accountB, 0, currency, null, 0, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LedgerEntryFactory.deposit(transactionId, accountB, accountA, -1, currency, null, 0, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsTransferToTheSameAccount() {
		assertThatThrownBy(() -> LedgerEntryFactory.transfer(transactionId, accountA, accountA, 1, currency, null, 0, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void storesReferenceInMetadata() {
		List<LedgerEntry> entries = LedgerEntryFactory.transfer(transactionId, accountA, accountB, 1, currency,
				"TX-42", 0, 1);

		assertThat(entries).allSatisfy(entry -> assertThat(entry.getMetadata()).contains("TX-42"));
		assertThat(entries).allSatisfy(entry -> assertThat(entry.getTransactionId()).isEqualTo(transactionId));
		assertThat(entries).allSatisfy(entry -> assertThat(entry.getCurrency()).isEqualTo(currency));
	}
}
