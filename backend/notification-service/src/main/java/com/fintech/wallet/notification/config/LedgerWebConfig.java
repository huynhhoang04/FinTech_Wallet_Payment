package com.fintech.wallet.notification.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The ledger is addressed by Eureka service id. A literal host:port would go stale as soon as the
 * blue/green deploy moves the HTTP port (docs 09 section 13).
 */
@Configuration
public class LedgerWebConfig {

	@Bean
	@LoadBalanced
	RestClient.Builder ledgerRestClientBuilder() {
		return RestClient.builder();
	}
}
