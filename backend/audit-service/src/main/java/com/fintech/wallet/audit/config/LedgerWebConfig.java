package com.fintech.wallet.audit.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The ledger is addressed by its Eureka service id, never by a literal host:port. The reconciliation
 * client used to be built from {@code LEDGER_BASE_URL=http://account-ledger-service:8082}, so as soon
 * as the blue/green deploy moved the ledger off the base port every reconciliation ran against a
 * dead address and was stored as FAILED (docs 09 section 13).
 */
@Configuration
public class LedgerWebConfig {

	@Bean
	@LoadBalanced
	RestClient.Builder ledgerRestClientBuilder() {
		return RestClient.builder();
	}
}
