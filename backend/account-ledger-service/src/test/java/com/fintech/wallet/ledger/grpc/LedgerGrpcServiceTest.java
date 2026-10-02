package com.fintech.wallet.ledger.grpc;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.ledger.domain.LedgerStatus;
import com.fintech.wallet.ledger.service.AccountPostingResult;
import com.fintech.wallet.ledger.service.BalanceView;
import com.fintech.wallet.ledger.service.LedgerPostingService;
import com.fintech.wallet.ledger.service.LedgerQueryService;
import com.fintech.wallet.ledger.service.TransferResult;
import com.fintech.wallet.proto.common.Money;
import com.fintech.wallet.proto.ledger.DepositRequest;
import com.fintech.wallet.proto.ledger.GetBalanceRequest;
import com.fintech.wallet.proto.ledger.GetBalanceResponse;
import com.fintech.wallet.proto.ledger.LedgerResult;
import com.fintech.wallet.proto.ledger.PostTransferRequest;
import com.fintech.wallet.proto.ledger.PostTransferResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerGrpcServiceTest {

	private final LedgerPostingService postingService = mock(LedgerPostingService.class);
	private final LedgerQueryService queryService = mock(LedgerQueryService.class);
	private final LedgerGrpcService grpcService = new LedgerGrpcService(postingService, queryService);

	private final CapturingObserver<PostTransferResponse> transferObserver = new CapturingObserver<>();
	private final CapturingObserver<LedgerResult> ledgerObserver = new CapturingObserver<>();
	private final CapturingObserver<GetBalanceResponse> balanceObserver = new CapturingObserver<>();

	@Test
	void postTransferReturnsPostedWithBalances() {
		UUID txId = UUID.randomUUID();
		when(postingService.postTransfer(any(), any(), any(), anyLong(), anyString(), any()))
				.thenReturn(new TransferResult(txId, LedgerStatus.POSTED, 900_000, 100_000));

		grpcService.postTransfer(transferRequest(txId), transferObserver);

		assertThat(transferObserver.values).hasSize(1);
		assertThat(transferObserver.values.get(0).getStatus()).isEqualTo(LedgerStatus.POSTED);
		assertThat(transferObserver.values.get(0).getSourceBalanceAfter()).isEqualTo(900_000);
		assertThat(transferObserver.values.get(0).getTargetBalanceAfter()).isEqualTo(100_000);
		assertThat(transferObserver.error).isNull();
	}

	@Test
	void postTransferReportsBusinessRejectionInBand() {
		when(postingService.postTransfer(any(), any(), any(), anyLong(), anyString(), any()))
				.thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_BALANCE));

		grpcService.postTransfer(transferRequest(UUID.randomUUID()), transferObserver);

		assertThat(transferObserver.error).isNull();
		assertThat(transferObserver.values.get(0).getStatus()).isEqualTo(LedgerStatus.REJECTED);
		assertThat(transferObserver.values.get(0).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE.name());
	}

	@Test
	void postTransferRejectsMalformedUuidAsInvalidArgument() {
		PostTransferRequest request = PostTransferRequest.newBuilder()
				.setTransactionId("not-a-uuid")
				.setSourceAccountId(UUID.randomUUID().toString())
				.setTargetAccountId(UUID.randomUUID().toString())
				.setAmount(money())
				.build();

		grpcService.postTransfer(request, transferObserver);

		assertThat(transferObserver.values).isEmpty();
		assertThat(Status.fromThrowable(transferObserver.error).getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
	}

	@Test
	void depositReturnsPostedResult() {
		UUID txId = UUID.randomUUID();
		when(postingService.deposit(any(), any(), anyLong(), anyString(), any()))
				.thenReturn(new AccountPostingResult(txId, LedgerStatus.POSTED, 500_000));

		grpcService.deposit(DepositRequest.newBuilder()
				.setTransactionId(txId.toString())
				.setAccountId(UUID.randomUUID().toString())
				.setAmount(money())
				.setChannel("VIETQR")
				.build(), ledgerObserver);

		assertThat(ledgerObserver.values).hasSize(1);
		assertThat(ledgerObserver.values.get(0).getStatus()).isEqualTo(LedgerStatus.POSTED);
		assertThat(ledgerObserver.values.get(0).getBalanceAfter()).isEqualTo(500_000);
	}

	@Test
	void getBalanceMapsMissingAccountToNotFound() {
		when(queryService.getBalance(any())).thenThrow(new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

		grpcService.getBalance(GetBalanceRequest.newBuilder()
				.setAccountId(UUID.randomUUID().toString())
				.build(), balanceObserver);

		assertThat(balanceObserver.values).isEmpty();
		assertThat(Status.fromThrowable(balanceObserver.error).getCode()).isEqualTo(Status.Code.NOT_FOUND);
	}

	@Test
	void getBalanceReturnsSnapshot() {
		UUID accountId = UUID.randomUUID();
		when(queryService.getBalance(accountId)).thenReturn(new BalanceView(accountId, "VND", 700_000, 0));

		grpcService.getBalance(GetBalanceRequest.newBuilder().setAccountId(accountId.toString()).build(),
				balanceObserver);

		assertThat(balanceObserver.values.get(0).getAvailable()).isEqualTo(700_000);
		assertThat(balanceObserver.values.get(0).getPending()).isZero();
		assertThat(balanceObserver.values.get(0).getCurrency()).isEqualTo("VND");
	}

	private static PostTransferRequest transferRequest(UUID transactionId) {
		return PostTransferRequest.newBuilder()
				.setTransactionId(transactionId.toString())
				.setSourceAccountId(UUID.randomUUID().toString())
				.setTargetAccountId(UUID.randomUUID().toString())
				.setAmount(money())
				.build();
	}

	private static Money money() {
		return Money.newBuilder().setAmount(100_000).setCurrency("VND").build();
	}

	private static final class CapturingObserver<T> implements StreamObserver<T> {

		private final List<T> values = new ArrayList<>();
		private Throwable error;

		@Override
		public void onNext(T value) {
			values.add(value);
		}

		@Override
		public void onError(Throwable throwable) {
			this.error = throwable;
		}

		@Override
		public void onCompleted() {
		}
	}
}
