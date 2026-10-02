package com.fintech.wallet.ledger;

import com.fintech.wallet.ledger.domain.LedgerStatus;
import com.fintech.wallet.ledger.support.AbstractLedgerIT;
import com.fintech.wallet.proto.common.Money;
import com.fintech.wallet.proto.ledger.LedgerServiceGrpc;
import com.fintech.wallet.proto.ledger.PostTransferRequest;
import com.fintech.wallet.proto.ledger.PostTransferResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LedgerGrpcIT extends AbstractLedgerIT {

	@Test
	void postTransferOverTheWire() {
		UUID source = seedUserAccount(1_000_000);
		UUID target = seedUserAccount(0);
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9099).usePlaintext().build();
		try {
			LedgerServiceGrpc.LedgerServiceBlockingStub stub = LedgerServiceGrpc.newBlockingStub(channel)
					.withDeadlineAfter(3, TimeUnit.SECONDS);

			PostTransferResponse response = stub.postTransfer(PostTransferRequest.newBuilder()
					.setTransactionId(UUID.randomUUID().toString())
					.setSourceAccountId(source.toString())
					.setTargetAccountId(target.toString())
					.setAmount(Money.newBuilder().setAmount(100_000).setCurrency("VND"))
					.setDescription("gRPC integration test")
					.build());

			assertThat(response.getStatus()).isEqualTo(LedgerStatus.POSTED);
			assertThat(response.getSourceBalanceAfter()).isEqualTo(900_000);
			assertThat(response.getTargetBalanceAfter()).isEqualTo(100_000);
			assertThat(response.getErrorCode()).isEmpty();
		} finally {
			channel.shutdownNow();
		}
	}

	@Test
	void insufficientFundsIsReportedInBandNotAsGrpcError() {
		UUID source = seedUserAccount(10_000);
		UUID target = seedUserAccount(0);
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9099).usePlaintext().build();
		try {
			LedgerServiceGrpc.LedgerServiceBlockingStub stub = LedgerServiceGrpc.newBlockingStub(channel)
					.withDeadlineAfter(3, TimeUnit.SECONDS);

			PostTransferResponse response = stub.postTransfer(PostTransferRequest.newBuilder()
					.setTransactionId(UUID.randomUUID().toString())
					.setSourceAccountId(source.toString())
					.setTargetAccountId(target.toString())
					.setAmount(Money.newBuilder().setAmount(999_999).setCurrency("VND"))
					.build());

			assertThat(response.getStatus()).isEqualTo(LedgerStatus.REJECTED);
			assertThat(response.getErrorCode()).isEqualTo("INSUFFICIENT_BALANCE");
		} finally {
			channel.shutdownNow();
		}
	}
}
