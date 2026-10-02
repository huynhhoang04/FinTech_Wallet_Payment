package com.fintech.wallet.gateway.filter;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.util.IdUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (exchange.getRequest().getHeaders().getFirst(HeaderNames.REQUEST_ID) != null) {
			return chain.filter(exchange);
		}
		ServerWebExchange mutated = exchange.mutate()
				.request(exchange.getRequest().mutate().header(HeaderNames.REQUEST_ID, IdUtils.newRequestId()).build())
				.build();
		return chain.filter(mutated);
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}
}
