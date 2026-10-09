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
		UUID paymentIntentId,
		String checkoutUrl,
		Instant createdAt,
		Instant updatedAt) {

	public static TransactionResponse of(Transaction transaction) {
		return of(transaction, null);
	}

	public static TransactionResponse of(Transaction transaction, String checkoutUrl) {
		return new TransactionResponse(transaction.getId(), transaction.getType().name(),
				transaction.getStatus().name(), transaction.getSourceAccountId(), transaction.getTargetAccountId(),
				transaction.getAmount(), transaction.getFee(), transaction.getCurrency(),
				transaction.getDescription(), transaction.getFailureReason(), transaction.getLedgerTransactionId(),
				transaction.getPaymentIntentId(), checkoutUrl, transaction.getCreatedAt(), transaction.getUpdatedAt());
	}
}
