package com.fintech.wallet.txengine.dto;

import com.fintech.wallet.txengine.domain.Transaction;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
		UUID transactionId,
		String type,
		String status,
		UUID sourceAccountId,
		UUID targetAccountId,
		long amount,
		long fee,
		String currency,
		String description,
		String failureReason,
		UUID ledgerTransactionId,
		Instant createdAt,
		Instant updatedAt) {

	public static TransactionResponse of(Transaction transaction) {
		return new TransactionResponse(transaction.getId(), transaction.getType().name(),
				transaction.getStatus().name(), transaction.getSourceAccountId(), transaction.getTargetAccountId(),
				transaction.getAmount(), transaction.getFee(), transaction.getCurrency(),
				transaction.getDescription(), transaction.getFailureReason(), transaction.getLedgerTransactionId(),
				transaction.getCreatedAt(), transaction.getUpdatedAt());
	}
}
