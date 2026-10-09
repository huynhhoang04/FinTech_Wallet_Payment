package com.fintech.wallet.ledger.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LedgerEntryFactory {

	/** Jackson, not string concatenation: a reference containing a quote used to break the jsonb insert. */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private LedgerEntryFactory() {
	}

	public static List<LedgerEntry> transfer(UUID transactionId, UUID sourceAccountId, UUID targetAccountId,
			long amount, String currency, String reference, long sourceBalanceAfter, long targetBalanceAfter) {
		if (sourceAccountId.equals(targetAccountId)) {
			throw new IllegalArgumentException("Source and target account must differ");
		}
		return checked(List.of(
				entry(transactionId, sourceAccountId, amount, EntryDirection.DEBIT, currency, reference,
						sourceBalanceAfter),
				entry(transactionId, targetAccountId, amount, EntryDirection.CREDIT, currency, reference,
						targetBalanceAfter)));
	}

	public static List<LedgerEntry> deposit(UUID transactionId, UUID suspenseAccountId, UUID walletAccountId,
			long amount, String currency, String reference, long suspenseBalanceAfter, long walletBalanceAfter) {
		return checked(List.of(
				entry(transactionId, suspenseAccountId, amount, EntryDirection.DEBIT, currency, reference,
						suspenseBalanceAfter),
				entry(transactionId, walletAccountId, amount, EntryDirection.CREDIT, currency, reference,
						walletBalanceAfter)));
	}

	public static List<LedgerEntry> withdraw(UUID transactionId, UUID walletAccountId, UUID suspenseAccountId,
			long amount, String currency, String reference, long walletBalanceAfter, long suspenseBalanceAfter) {
		return checked(List.of(
				entry(transactionId, walletAccountId, amount, EntryDirection.DEBIT, currency, reference,
						walletBalanceAfter),
				entry(transactionId, suspenseAccountId, amount, EntryDirection.CREDIT, currency, reference,
						suspenseBalanceAfter)));
	}

	public static long sum(List<LedgerEntry> entries) {
		return entries.stream().mapToLong(LedgerEntry::getAmount).sum();
	}

	private static LedgerEntry entry(UUID transactionId, UUID accountId, long amount, EntryDirection direction,
			String currency, String reference, long balanceAfter) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
		LedgerEntry entry = new LedgerEntry();
		entry.setTransactionId(transactionId);
		entry.setAccountId(accountId);
		entry.setAmount(direction == EntryDirection.DEBIT ? Math.negateExact(amount) : amount);
		entry.setDirection(direction);
		entry.setCurrency(currency);
		entry.setBalanceAfter(balanceAfter);
		entry.setMetadata(metadata(reference));
		return entry;
	}

	private static String metadata(String reference) {
		if (reference == null) {
			return null;
		}
		try {
			return MAPPER.writeValueAsString(Map.of("ref", reference));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise entry metadata", e);
		}
	}

	private static List<LedgerEntry> checked(List<LedgerEntry> entries) {
		if (entries.size() < 2) {
			throw new IllegalStateException("A transaction must write at least two entries");
		}
		if (sum(entries) != 0) {
			throw new IllegalStateException("Ledger entries of a transaction must sum to zero");
		}
		boolean hasDebit = entries.stream()
				.anyMatch(entry -> entry.getDirection() == EntryDirection.DEBIT && entry.getAmount() < 0);
		boolean hasCredit = entries.stream()
				.anyMatch(entry -> entry.getDirection() == EntryDirection.CREDIT && entry.getAmount() > 0);
		if (!hasDebit || !hasCredit) {
			throw new IllegalStateException("Direction must agree with the amount sign");
		}
		return entries;
	}
}
