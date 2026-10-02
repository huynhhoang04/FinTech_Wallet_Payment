package com.fintech.wallet.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Configuration
public class RateLimitConfig {

	@Bean
	KeyResolver ipKeyResolver() {
		return exchange -> Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
				.map(address -> address.getAddress().getHostAddress())
				.orElse("unknown"));
	}
}
