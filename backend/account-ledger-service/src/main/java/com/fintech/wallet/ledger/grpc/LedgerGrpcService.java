package com.fintech.wallet.ledger.grpc;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.ledger.domain.LedgerStatus;
import com.fintech.wallet.ledger.service.AccountPostingResult;
import com.fintech.wallet.ledger.service.BalanceView;
import com.fintech.wallet.ledger.service.LedgerPostingService;
import com.fintech.wallet.ledger.service.LedgerQueryService;
import com.fintech.wallet.ledger.service.StatementView;
import com.fintech.wallet.ledger.service.TransferResult;
import com.fintech.wallet.proto.ledger.DepositRequest;
import com.fintech.wallet.proto.ledger.GetBalanceRequest;
import com.fintech.wallet.proto.ledger.GetBalanceResponse;
import com.fintech.wallet.proto.ledger.GetStatementRequest;
import com.fintech.wallet.proto.ledger.GetStatementResponse;
import com.fintech.wallet.proto.ledger.LedgerEntryDto;
import com.fintech.wallet.proto.ledger.LedgerResult;
import com.fintech.wallet.proto.ledger.LedgerServiceGrpc;
import com.fintech.wallet.proto.ledger.PostTransferRequest;
import com.fintech.wallet.proto.ledger.PostTransferResponse;
import com.fintech.wallet.proto.ledger.WithdrawRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Instant;
import java.util.UUID;

@GrpcService
public class LedgerGrpcService extends LedgerServiceGrpc.LedgerServiceImplBase {

	private final LedgerPostingService postingService;
	private final LedgerQueryService queryService;

	public LedgerGrpcService(LedgerPostingService postingService, LedgerQueryService queryService) {
		this.postingService = postingService;
		this.queryService = queryService;
	}

	@Override
	public void postTransfer(PostTransferRequest request, StreamObserver<PostTransferResponse> responseObserver) {
		try {
			TransferResult result = postingService.postTransfer(
					toUuid(request.getTransactionId()),
					toUuid(request.getSourceAccountId()),
					toUuid(request.getTargetAccountId()),
					request.getAmount().getAmount(),
					request.getAmount().getCurrency(),
					request.getDescription());
			respond(responseObserver, PostTransferResponse.newBuilder()
					.setLedgerTransactionId(result.ledgerTransactionId().toString())
					.setStatus(result.status())
					.setSourceBalanceAfter(result.sourceBalanceAfter())
					.setTargetBalanceAfter(result.targetBalanceAfter())
					.build());
		} catch (BusinessException e) {
			respond(responseObserver, PostTransferResponse.newBuilder()
					.setLedgerTransactionId(request.getTransactionId())
					.setStatus(LedgerStatus.REJECTED)
					.setErrorCode(e.errorCode().name())
					.build());
		} catch (IllegalArgumentException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void deposit(DepositRequest request, StreamObserver<LedgerResult> responseObserver) {
		try {
			AccountPostingResult result = postingService.deposit(
					toUuid(request.getTransactionId()),
					toUuid(request.getAccountId()),
					request.getAmount().getAmount(),
					request.getAmount().getCurrency(),
					request.getChannel());
			respond(responseObserver, toLedgerResult(result));
		} catch (BusinessException e) {
			respond(responseObserver, rejected(request.getTransactionId(), e));
		} catch (IllegalArgumentException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void withdraw(WithdrawRequest request, StreamObserver<LedgerResult> responseObserver) {
		try {
			AccountPostingResult result = postingService.withdraw(
					toUuid(request.getTransactionId()),
					toUuid(request.getAccountId()),
					request.getAmount().getAmount(),
					request.getAmount().getCurrency(),
					request.getChannel());
			respond(responseObserver, toLedgerResult(result));
		} catch (BusinessException e) {
			respond(responseObserver, rejected(request.getTransactionId(), e));
		} catch (IllegalArgumentException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void getBalance(GetBalanceRequest request, StreamObserver<GetBalanceResponse> responseObserver) {
		try {
			BalanceView balance = queryService.getBalance(toUuid(request.getAccountId()));
			respond(responseObserver, GetBalanceResponse.newBuilder()
					.setAccountId(balance.accountId().toString())
					.setCurrency(balance.currency())
					.setAvailable(balance.available())
					.setPending(balance.pending())
					.build());
		} catch (BusinessException e) {
			responseObserver.onError(grpcStatusOf(e).asRuntimeException());
		} catch (IllegalArgumentException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void getStatement(GetStatementRequest request, StreamObserver<GetStatementResponse> responseObserver) {
		try {
			long toEpochMs = request.getToEpochMs() > 0 ? request.getToEpochMs() : System.currentTimeMillis();
			StatementView statement = queryService.getStatement(
					toUuid(request.getAccountId()),
					Instant.ofEpochMilli(request.getFromEpochMs()),
					Instant.ofEpochMilli(toEpochMs),
					request.getPage(),
					request.getSize());
			GetStatementResponse.Builder response = GetStatementResponse.newBuilder()
					.setOpeningBalance(statement.openingBalance())
					.setClosingBalance(statement.closingBalance());
			statement.entries().forEach(entry -> response.addEntries(LedgerEntryDto.newBuilder()
					.setEntryId(entry.getId().toString())
					.setAmountSigned(entry.getAmount())
					.setDirection(entry.getDirection().name())
					.setBalanceAfter(entry.getBalanceAfter())
					.setAtEpochMs(entry.getCreatedAt().toEpochMilli())
					.build()));
			respond(responseObserver, response.build());
		} catch (BusinessException e) {
			responseObserver.onError(grpcStatusOf(e).asRuntimeException());
		} catch (IllegalArgumentException e) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	private static LedgerResult toLedgerResult(AccountPostingResult result) {
		return LedgerResult.newBuilder()
				.setLedgerTransactionId(result.ledgerTransactionId().toString())
				.setStatus(result.status())
				.setBalanceAfter(result.balanceAfter())
				.build();
	}

	private static LedgerResult rejected(String transactionId, BusinessException e) {
		return LedgerResult.newBuilder()
				.setLedgerTransactionId(transactionId)
				.setStatus(LedgerStatus.REJECTED)
				.setErrorCode(e.errorCode().name())
				.build();
	}

	private static Status grpcStatusOf(BusinessException e) {
		return switch (e.errorCode()) {
			case ACCOUNT_NOT_FOUND, NOT_FOUND -> Status.NOT_FOUND;
			case VALIDATION_ERROR, INSUFFICIENT_BALANCE, CURRENCY_MISMATCH, FRAUD_BLOCKED, ACCOUNT_LOCKED ->
					Status.FAILED_PRECONDITION;
			case UPSTREAM_UNAVAILABLE -> Status.UNAVAILABLE;
			default -> Status.INTERNAL;
		};
	}

	private static UUID toUuid(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid UUID: " + value);
		}
	}

	private static <T> void respond(StreamObserver<T> observer, T message) {
		observer.onNext(message);
		observer.onCompleted();
	}
}
