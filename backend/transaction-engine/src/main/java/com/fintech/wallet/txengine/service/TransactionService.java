package com.fintech.wallet.txengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.security.JwtUtils;
import com.fintech.wallet.txengine.domain.Transaction;
import com.fintech.wallet.txengine.domain.TransactionStatus;
import com.fintech.wallet.txengine.domain.TransactionType;
import com.fintech.wallet.txengine.dto.DepositRequest;
import com.fintech.wallet.txengine.dto.TransactionResponse;
import com.fintech.wallet.txengine.dto.TransferRequest;
import com.fintech.wallet.txengine.dto.WithdrawRequest;
import com.fintech.wallet.txengine.grpc.LedgerClient;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The money write path (backend skill, section 5): idempotency key, distributed lock per account,
 * fraud rules, ledger call over gRPC with a deadline, then the state change and the outbox row in
 * one database transaction.
 *
 * <p>Three things are load bearing. The ledger transaction id is <b>derived from the idempotency
 * key</b>, so a retry after an ambiguous timeout reuses the same id and the ledger replays instead
 * of posting twice (docs 07 FIN-01). Locks are taken <b>per account, ascending</b>, so a withdrawal
 * from A and a transfer A to B exclude each other rather than using two unrelated keys (FIN-07).
 * And the entity id is generated before the ledger call, because Hibernate only assigns it at
 * persist time, which happens after the posting.
 */
@Slf4j
@Service
public class TransactionService {

	private static final String LOCK_PREFIX = "tx:lock:";
	private static final String LEDGER_ID_PREFIX = "ledger-tx:";
	private static final String OTP_CLAIM = "otp";

	private final IdempotencyService idempotency;
	private final FraudRuleEngine fraud;
	private final LedgerClient ledger;
	private final TransactionRecorder recorder;
	private final PaymentGatewayClient paymentGateway;
	private final RedissonClient redisson;
	private final JwtUtils jwtUtils;
	private final ObjectMapper objectMapper;
	private final long lockWaitSeconds;
	private final long lockLeaseSeconds;
	private final long otpThreshold;

	public TransactionService(IdempotencyService idempotency,
			FraudRuleEngine fraud,
			LedgerClient ledger,
			TransactionRecorder recorder,
			PaymentGatewayClient paymentGateway,
			RedissonClient redisson,
			JwtUtils jwtUtils,
			ObjectMapper objectMapper,
			@Value("${app.lock.wait}") Duration lockWait,
			@Value("${app.lock.lease}") Duration lockLease,
			@Value("${app.otp.threshold}") long otpThreshold) {
		this.idempotency = idempotency;
		this.fraud = fraud;
		this.ledger = ledger;
		this.recorder = recorder;
		this.paymentGateway = paymentGateway;
		this.redisson = redisson;
		this.jwtUtils = jwtUtils;
		this.objectMapper = objectMapper;
		this.lockWaitSeconds = lockWait.toSeconds();
		this.lockLeaseSeconds = lockLease.toSeconds();
		this.otpThreshold = otpThreshold;
	}

	public Result transfer(String idempotencyKey, String userId, TransferRequest request) {
		String payload = payloadOf(request);
		return idempotently(idempotencyKey, payload, () -> {
			requireOtpIfLarge(userId, request.amount(), request.otpToken());
			LedgerClient.AccountInfo target = ledger.accountByNumber(request.targetAccountNumber());
			Transaction transaction = newTransaction(idempotencyKey, TransactionType.TRANSFER, request.sourceAccountId(),
					target.accountId(), request.amount(), request.currency(), request.description());

			return withAccountLocks(List.of(request.sourceAccountId(), target.accountId()), () -> {
				UUID ledgerTransactionId = ledgerTransactionId(idempotencyKey);
				requireNotFraudulent(transaction);
				LedgerClient.PostingOutcome outcome = ledger.postTransfer(ledgerTransactionId, request.sourceAccountId(),
						target.accountId(), request.amount(), request.currency(), request.description());
				return recordSuccess(transaction, outcome.ledgerTransactionId());
			});
		});
	}

	public Result withdraw(String idempotencyKey, String userId, WithdrawRequest request) {
		String payload = payloadOf(request);
		return idempotently(idempotencyKey, payload, () -> {
			requireOtpIfLarge(userId, request.amount(), request.otpToken());
			Transaction transaction = newTransaction(idempotencyKey, TransactionType.WITHDRAW, request.accountId(), null,
					request.amount(), request.currency(), request.bankCode() + ":" + request.bankAccount());

			return withAccountLocks(List.of(request.accountId()), () -> {
				UUID ledgerTransactionId = ledgerTransactionId(idempotencyKey);
				requireNotFraudulent(transaction);
				LedgerClient.PostingOutcome outcome = ledger.postWithdraw(ledgerTransactionId, request.accountId(),
						request.amount(), request.currency(), request.bankCode());
				return recordSuccess(transaction, outcome.ledgerTransactionId());
			});
		});
	}

	/**
	 * Opens a PENDING deposit and asks the payment gateway for a checkout session. The wallet is
	 * credited later, by the {@code payment.succeeded} consumer, once the provider confirms the money
	 * arrived - this is what closes the deposit loop (docs 07 FIN-03).
	 */
	public Result deposit(String idempotencyKey, String userId, DepositRequest request) {
		String payload = payloadOf(request);
		return idempotently(idempotencyKey, payload, () -> {
			PaymentGatewayClient.IntentCreation intent = paymentGateway.createIntent(idempotencyKey,
					UUID.fromString(userId), request.accountId(), request.amount(), request.currency(),
					request.provider());
			Transaction transaction = newTransaction(idempotencyKey, TransactionType.DEPOSIT, null, request.accountId(),
					request.amount(), request.currency(), "provider=" + request.provider());
			transaction.setStatus(TransactionStatus.PENDING);
			transaction.setPaymentIntentId(intent.paymentIntentId());
			return TransactionResponse.of(recorder.complete(transaction), intent.checkoutUrl());
		});
	}

	private TransactionResponse recordSuccess(Transaction transaction, UUID ledgerTransactionId) {
		transaction.setLedgerTransactionId(ledgerTransactionId);
		transaction.setStatus(TransactionStatus.SUCCESS);
		return TransactionResponse.of(recorder.complete(transaction), null);
	}

	private void requireNotFraudulent(Transaction transaction) {
		Optional<FraudRuleEngine.FraudDecision> decision = fraud.evaluate(transaction.getSourceAccountId(),
				transaction.getAmount());
		if (decision.isPresent()) {
			recorder.recordFraud(transaction, decision.get());
			throw new BusinessException(ErrorCode.FRAUD_BLOCKED);
		}
	}

	private void requireOtpIfLarge(String userId, long amount, String otpToken) {
		if (amount <= otpThreshold) {
			return;
		}
		if (otpToken == null || otpToken.isBlank() || !isValidOtp(userId, otpToken)) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Giao dịch lớn cần OTP hợp lệ");
		}
	}

	private boolean isValidOtp(String userId, String otpToken) {
		try {
			Claims claims = jwtUtils.parse(otpToken).getPayload();
			return Boolean.TRUE.equals(claims.get(OTP_CLAIM, Boolean.class)) && userId.equals(claims.getSubject());
		} catch (RuntimeException e) {
			return false;
		}
	}

	/**
	 * One Redis lock per account, taken in ascending accountId order and released in reverse, so any
	 * two operations touching the same account exclude each other whatever shape they have.
	 */
	private <T> T withAccountLocks(List<UUID> accountIds, Supplier<T> action) {
		List<RLock> locks = new ArrayList<>();
		try {
			for (UUID accountId : accountIds.stream().distinct().sorted().toList()) {
				RLock lock = redisson.getLock(LOCK_PREFIX + accountId);
				if (!lock.tryLock(lockWaitSeconds, lockLeaseSeconds, TimeUnit.SECONDS)) {
					throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "Giao dịch đang được xử lý");
				}
				locks.add(lock);
			}
			return action.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Bị ngắt khi chờ lock");
		} finally {
			for (int index = locks.size() - 1; index >= 0; index--) {
				RLock lock = locks.get(index);
				if (lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
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
				// Safe to free: the ledger id is derived from this key, so a retry replays rather than reposts.
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

	/** Deterministic on purpose: the same idempotency key always maps to the same ledger posting. */
	private static UUID ledgerTransactionId(String idempotencyKey) {
		return UUID.nameUUIDFromBytes((LEDGER_ID_PREFIX + idempotencyKey).getBytes(StandardCharsets.UTF_8));
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
