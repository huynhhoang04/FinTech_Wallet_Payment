package com.fintech.wallet.txengine.config;

import com.fintech.wallet.proto.ledger.LedgerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

	@Bean(destroyMethod = "shutdownNow")
	ManagedChannel ledgerChannel(@Value("${app.ledger.grpc-address}") String address) {
		return ManagedChannelBuilder.forTarget(address).usePlaintext().build();
	}

	@Bean
	LedgerServiceGrpc.LedgerServiceBlockingStub ledgerStub(ManagedChannel ledgerChannel) {
		return LedgerServiceGrpc.newBlockingStub(ledgerChannel);
	}
}
