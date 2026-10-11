package com.fintech.wallet.txengine.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The payment gateway is reached through Eureka, not a pinned host:port - blue/green moves the HTTP
 * port on every deploy, so a literal localhost:8083 breaks inside Docker (docs 09 section 13).
 * A load-balanced builder resolves the current instance from the service registry.
 */
@Configuration
public class PaymentWebConfig {

	@Bean
	@LoadBalanced
	RestClient.Builder paymentGatewayRestClientBuilder() {
		return RestClient.builder();
	}
}
