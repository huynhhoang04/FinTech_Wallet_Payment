package com.fintech.wallet.txengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.txengine.domain.FraudAlert;
import com.fintech.wallet.txengine.domain.Transaction;
import com.fintech.wallet.txengine.domain.TransactionStatus;
import com.fintech.wallet.txengine.domain.TransactionType;
import com.fintech.wallet.txengine.dto.DepositRequest;
import com.fintech.wallet.txengine.dto.TransactionResponse;
import com.fintech.wallet.txengine.dto.TransferRequest;
import com.fintech.wallet.txengine.dto.WithdrawRequest;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The money write path (backend skill, section 5): idempotency key, distributed lock ordered by
 * account id, fraud rules, ledger call over gRPC with a deadline, then the state change and the
 * outbox row in one database transaction.
 *
 * <p>The identifier sent to the ledger is generated here rather than taken from the entity: the
 * entity id only exists once Hibernate persists it, and the ledger call must happen before that
 * write. The response therefore carries both ids, as the API contract describes.
 */
@Slf4j
@Service
public class TransactionService {

	private static final String LOCK_PREFIX = "tx:lock:";

	private final IdempotencyService idempotency;
	private final FraudRuleEngine fraud;
	private final LedgerClient ledger;
	private final TransactionRecorder recorder;
	private final RedissonClient redisson;
	private final ObjectMapper objectMapper;
	private final long lockWaitSeconds;
	private final long lockLeaseSeconds;

	public TransactionService(IdempotencyService idempotency,
			FraudRuleEngine fraud,
			LedgerClient ledger,
			TransactionRecorder recorder,
			RedissonClient redisson,
			ObjectMapper objectMapper,
			@Value("${app.lock.wait}") Duration lockWait,
			@Value("${app.lock.lease}") Duration lockLease) {
		this.idempotency = idempotency;
		this.fraud = fraud;
		this.ledger = ledger;
		this.recorder = recorder;
		this.redisson = redisson;
		this.objectMapper = objectMapper;
		this.lockWaitSeconds = lockWait.toSeconds();
		this.lockLeaseSeconds = lockLease.toSeconds();
	}

	public Result transfer(String idempotencyKey, TransferRequest request) {
		String payload = payloadOf(request);
		return idempotently(idempotencyKey, payload, () -> {
			LedgerClient.AccountInfo target = ledger.accountByNumber(request.targetAccountNumber());
			Transaction transaction = newTransaction(idempotencyKey, TransactionType.TRANSFER, request.sourceAccountId(),
					target.accountId(), request.amount(), request.currency(), request.description());

			return withAccountLocks(List.of(request.sourceAccountId(), target.accountId()), () -> {
				UUID ledgerTransactionId = UUID.randomUUID();
				requireNotFraudulent(ledgerTransactionId, transaction);
				LedgerClient.PostingOutcome outcome = ledger.postTransfer(ledgerTransactionId, request.sourceAccountId(),
						target.accountId(), request.amount(), request.currency(), request.description());
				return recordSuccess(transaction, outcome.ledgerTransactionId());
			});
		});
	}

	public Result withdraw(String idempotencyKey, WithdrawRequest request) {
		String payload = payloadOf(request);
		return idempotently(idempotencyKey, payload, () -> {
			Transaction transaction = newTransaction(idempotencyKey, TransactionType.WITHDRAW, request.accountId(), null,
					request.amount(), request.currency(), request.bankCode() + ":" + request.bankAccount());

			return withAccountLocks(List.of(request.accountId()), () -> {
				UUID ledgerTransactionId = UUID.randomUUID();
				requireNotFraudulent(ledgerTransactionId, transaction);
				LedgerClient.PostingOutcome outcome = ledger.postWithdraw(ledgerTransactionId, request.accountId(),
						request.amount(), request.currency(), request.bankCode());
				return recordSuccess(transaction, outcome.ledgerTransactionId());
			});
		});
	}

	/**
	 * A deposit does not touch the wallet directly: it opens a PENDING flow that the payment provider
	 * completes through its webhook (docs 04 section 3.2), so no ledger call happens here.
	 */
	public Result deposit(String idempotencyKey, DepositRequest request) {
		String payload = payloadOf(request);
		return idempotently(idempotencyKey, payload, () -> {
			Transaction transaction = newTransaction(idempotencyKey, TransactionType.DEPOSIT, null, request.accountId(),
					request.amount(), request.currency(), "provider=" + request.provider());
			transaction.setStatus(TransactionStatus.PENDING);
			return TransactionResponse.of(recorder.complete(transaction));
		});
	}

	private TransactionResponse recordSuccess(Transaction transaction, UUID ledgerTransactionId) {
		transaction.setLedgerTransactionId(ledgerTransactionId);
		transaction.setStatus(TransactionStatus.SUCCESS);
		return TransactionResponse.of(recorder.complete(transaction));
	}

	private void requireNotFraudulent(UUID ledgerTransactionId, Transaction transaction) {
		Optional<FraudAlert> alert = fraud.evaluate(ledgerTransactionId, transaction.getSourceAccountId(),
				transaction.getAmount());
		if (alert.isPresent()) {
			recorder.fail(transaction, "FRAUD_BLOCKED:" + alert.get().getRuleCode());
			throw new BusinessException(ErrorCode.FRAUD_BLOCKED);
		}
	}

	/** Runs the work under one Redis-held lock over the sorted account ids, so A-&gt;B and B-&gt;A serialise. */
	private <T> T withAccountLocks(List<UUID> accountIds, Supplier<T> action) {
		String lockName = LOCK_PREFIX + accountIds.stream().distinct().sorted().map(UUID::toString)
				.collect(Collectors.joining(","));
		RLock lock = redisson.getLock(lockName);
		boolean acquired = false;
		try {
			acquired = lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS);
			if (!acquired) {
				throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Giao dịch đang được xử lý");
			}
			return action.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Bị ngắt khi chờ lock");
		} finally {
			if (acquired && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	private Result idempotently(String key, String payload, Supplier<TransactionResponse> work) {
		String payloadHash = IdempotencyService.payloadHash(payload);
		Optional<IdempotencyService.StoredEntry> existing = idempotency.reserve(key, payloadHash);
		if (existing.isPresent()) {
			return new Result(replay(existing.get(), payloadHash), true);
		}
		try {
			TransactionResponse response = work.get();
			idempotency.complete(key, payloadHash, write(Outcome.of(response)));
			return new Result(response, false);
		} catch (BusinessException e) {
			if (e.errorCode() == ErrorCode.UPSTREAM_UNAVAILABLE) {
				idempotency.release(key);
			} else {
				idempotency.complete(key, payloadHash, write(Outcome.rejected(e)));
			}
			throw e;
		}
	}

	private TransactionResponse replay(IdempotencyService.StoredEntry entry, String payloadHash) {
		if (!entry.payloadHash().equals(payloadHash)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
		}
		if (entry.inFlight()) {
			throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
		}
		Outcome outcome = read(entry.response(), Outcome.class);
		if (!outcome.successful()) {
			throw new BusinessException(ErrorCode.valueOf(outcome.errorCode()), outcome.message());
		}
		return outcome.response();
	}

	private static Transaction newTransaction(String idempotencyKey, TransactionType type, UUID sourceAccountId,
			UUID targetAccountId, long amount, String currency, String description) {
		Transaction transaction = new Transaction();
		transaction.setIdempotencyKey(idempotencyKey);
		transaction.setType(type);
		transaction.setStatus(TransactionStatus.PROCESSING);
		transaction.setSourceAccountId(sourceAccountId);
		transaction.setTargetAccountId(targetAccountId);
		transaction.setAmount(amount);
		transaction.setFee(0L);
		transaction.setCurrency(currency);
		transaction.setDescription(description);
		return transaction;
	}

	private String payloadOf(Object request) {
		return write(request);
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise payload", e);
		}
	}

	private <T> T read(String json, Class<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot read stored outcome", e);
		}
	}

	public record Result(TransactionResponse response, boolean replayed) {
	}

	private record Outcome(boolean successful, TransactionResponse response, String errorCode, String message) {

		static Outcome of(TransactionResponse response) {
			return new Outcome(true, response, null, null);
		}

		static Outcome rejected(BusinessException e) {
			return new Outcome(false, null, e.errorCode().name(), e.getMessage());
		}
	}
}
