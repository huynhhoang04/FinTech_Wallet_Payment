package com.fintech.wallet.txengine.grpc;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.proto.common.Money;
import com.fintech.wallet.proto.ledger.AccountSummary;
import com.fintech.wallet.proto.ledger.DepositRequest;
import com.fintech.wallet.proto.ledger.GetAccountByNumberRequest;
import com.fintech.wallet.proto.ledger.GetAccountsByOwnerRequest;
import com.fintech.wallet.proto.ledger.GetBalanceRequest;
import com.fintech.wallet.proto.ledger.GetLedgerEntriesRequest;
import com.fintech.wallet.proto.ledger.GetStatementRequest;
import com.fintech.wallet.proto.ledger.LedgerEntryDto;
import com.fintech.wallet.proto.ledger.LedgerResult;
import com.fintech.wallet.proto.ledger.LedgerServiceGrpc;
import com.fintech.wallet.proto.ledger.PostTransferRequest;
import com.fintech.wallet.proto.ledger.PostTransferResponse;
import com.fintech.wallet.proto.ledger.WithdrawRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * The only door to the ledger. Every call carries a deadline, and a posting call is never retried -
 * replay safety comes from {@code transaction_id}, not from retrying (backend skill, section 6).
 *
 * <p>Failures are recorded by a real circuit breaker, so a slow or dead ledger opens the circuit
 * instead of piling threads up on it; reads additionally retry, postings do not. Both live in the
 * {@code resilience4j} block of application.yml, and a {@link BusinessException} (an expected
 * rejection such as insufficient funds) is ignored so it neither counts as a failure nor retries.
 */
@Component
public class LedgerClient {

	/** The proto carries the posting outcome as a plain string (docs 04 section 4.1). */
	private static final String POSTED = "POSTED";
	private static final long DEADLINE_MILLIS = 3_000;

	private final LedgerServiceGrpc.LedgerServiceBlockingStub stub;
	private final CircuitBreaker circuitBreaker;
	private final Retry retry;

	public LedgerClient(LedgerServiceGrpc.LedgerServiceBlockingStub stub, CircuitBreakerRegistry circuitBreakerRegistry,
			RetryRegistry retryRegistry) {
		this.stub = stub;
		this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("ledger");
		this.retry = retryRegistry.retry("ledgerRead");
	}

	public AccountInfo accountByNumber(String accountNumber) {
		return read(() -> {
			AccountSummary summary = stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
					.getAccountByNumber(GetAccountByNumberRequest.newBuilder()
							.setAccountNumber(accountNumber)
							.build());
			return toAccountInfo(summary);
		});
	}

	public List<AccountInfo> accountsByOwner(UUID ownerUserId) {
		return read(() -> stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
				.getAccountsByOwner(GetAccountsByOwnerRequest.newBuilder().setOwnerUserId(ownerUserId.toString()).build())
				.getAccountsList().stream()
				.map(LedgerClient::toAccountInfo)
				.toList());
	}

	public PostingOutcome postTransfer(UUID transactionId, UUID sourceAccountId, UUID targetAccountId, long amount,
			String currency, String description) {
		return call(() -> {
			PostTransferResponse response = stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
					.postTransfer(PostTransferRequest.newBuilder()
							.setTransactionId(transactionId.toString())
							.setSourceAccountId(sourceAccountId.toString())
							.setTargetAccountId(targetAccountId.toString())
							.setAmount(money(amount, currency))
							.setDescription(description == null ? "" : description)
							.build());
			requirePosted(response.getStatus(), response.getErrorCode());
			return new PostingOutcome(UUID.fromString(response.getLedgerTransactionId()),
					response.getSourceBalanceAfter(), response.getTargetBalanceAfter());
		});
	}

	public PostingOutcome postWithdraw(UUID transactionId, UUID accountId, long amount, String currency,
			String channel) {
		return call(() -> {
			LedgerResult result = stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
					.withdraw(WithdrawRequest.newBuilder()
							.setTransactionId(transactionId.toString())
							.setAccountId(accountId.toString())
							.setAmount(money(amount, currency))
							.setChannel(channel)
							.build());
			requirePosted(result.getStatus(), result.getErrorCode());
			return new PostingOutcome(UUID.fromString(result.getLedgerTransactionId()), result.getBalanceAfter(),
					result.getBalanceAfter());
		});
	}

	/** Credits the wallet from SUSPENSE once a provider confirms the money arrived (docs 02 section 4.5). */
	public PostingOutcome deposit(UUID transactionId, UUID accountId, long amount, String currency, String channel) {
		return call(() -> {
			LedgerResult result = stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
					.deposit(DepositRequest.newBuilder()
							.setTransactionId(transactionId.toString())
							.setAccountId(accountId.toString())
							.setAmount(money(amount, currency))
							.setChannel(channel)
							.build());
			requirePosted(result.getStatus(), result.getErrorCode());
			return new PostingOutcome(UUID.fromString(result.getLedgerTransactionId()), result.getBalanceAfter(),
					result.getBalanceAfter());
		});
	}

	public BalanceInfo balance(UUID accountId) {
		return read(() -> {
			var response = stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
					.getBalance(GetBalanceRequest.newBuilder().setAccountId(accountId.toString()).build());
			return new BalanceInfo(UUID.fromString(response.getAccountId()), response.getCurrency(),
					response.getAvailable(), response.getPending());
		});
	}

	public LedgerEntriesInfo ledgerEntries(UUID transactionId) {
		return read(() -> {
			var response = stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
					.getLedgerEntries(GetLedgerEntriesRequest.newBuilder()
							.setTransactionId(transactionId.toString())
							.build());
			return new LedgerEntriesInfo(transactionId,
					response.getEntriesList().stream().map(LedgerClient::toEntry).toList(), response.getSum());
		});
	}

	public StatementInfo statement(UUID accountId, long fromEpochMs, long toEpochMs, int page, int size) {
		return read(() -> {
			var response = stub.withDeadlineAfter(DEADLINE_MILLIS, MILLISECONDS)
					.getStatement(GetStatementRequest.newBuilder()
							.setAccountId(accountId.toString())
							.setFromEpochMs(fromEpochMs)
							.setToEpochMs(toEpochMs)
							.setPage(page)
							.setSize(size)
							.build());
			return new StatementInfo(response.getOpeningBalance(), response.getClosingBalance(),
					response.getEntriesList().stream().map(LedgerClient::toEntry).toList());
		});
	}

	private static AccountInfo toAccountInfo(AccountSummary summary) {
		return new AccountInfo(UUID.fromString(summary.getAccountId()), summary.getAccountNumber(),
				summary.getCurrency(), summary.getStatus());
	}

	private static void requirePosted(String status, String errorCode) {
		if (POSTED.equals(status)) {
			return;
		}
		throw new BusinessException(mapErrorCode(errorCode), "Ledger từ chối: " + errorCode);
	}

	private static ErrorCode mapErrorCode(String ledgerErrorCode) {
		return switch (ledgerErrorCode) {
			case "INSUFFICIENT_FUNDS" -> ErrorCode.INSUFFICIENT_BALANCE;
			case "ACCOUNT_LOCKED" -> ErrorCode.ACCOUNT_LOCKED;
			case "ACCOUNT_NOT_FOUND" -> ErrorCode.ACCOUNT_NOT_FOUND;
			case "CURRENCY_MISMATCH" -> ErrorCode.CURRENCY_MISMATCH;
			default -> ErrorCode.VALIDATION_ERROR;
		};
	}

	/** Reads may be retried; postings must not (the ledger is idempotent by transaction id). */
	private <T> T read(Callable<T> action) {
		try {
			return retry.executeCallable(() -> call(action));
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Ledger lỗi: " + e);
		}
	}

	private <T> T call(Callable<T> action) {
		try {
			return circuitBreaker.executeCallable(action);
		} catch (BusinessException e) {
			throw e;
		} catch (CallNotPermittedException e) {
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Mạch bảo vệ ledger đang mở");
		} catch (StatusRuntimeException e) {
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Ledger không phản hồi: " + e.getStatus());
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Ledger lỗi: " + e);
		}
	}

	private static Money money(long amount, String currency) {
		return Money.newBuilder().setAmount(amount).setCurrency(currency).build();
	}

	private static EntryInfo toEntry(LedgerEntryDto entry) {
		return new EntryInfo(UUID.fromString(entry.getEntryId()), entry.getAmountSigned(), entry.getDirection(),
				entry.getBalanceAfter(), entry.getAtEpochMs());
	}

	public record AccountInfo(UUID accountId, String accountNumber, String currency, String status) {
	}

	public record PostingOutcome(UUID ledgerTransactionId, long sourceBalanceAfter, long targetBalanceAfter) {
	}

	public record BalanceInfo(UUID accountId, String currency, long available, long pending) {
	}

	public record EntryInfo(UUID entryId, long amountSigned, String direction, long balanceAfter, long atEpochMs) {
	}

	public record LedgerEntriesInfo(UUID transactionId, List<EntryInfo> entries, long sum) {
	}

	public record StatementInfo(long openingBalance, long closingBalance, List<EntryInfo> entries) {
	}
}
