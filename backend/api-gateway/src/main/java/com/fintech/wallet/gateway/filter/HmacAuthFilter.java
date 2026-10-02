package com.fintech.wallet.gateway.filter;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.hmac.CanonicalRequest;
import com.fintech.wallet.common.hmac.HmacUtils;
import com.fintech.wallet.gateway.error.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class HmacAuthFilter implements GlobalFilter, Ordered {

	private static final Set<String> EXCLUDED_PREFIXES = Set.of("/actuator", "/webhooks");
	private static final String MISSING_HEADERS = "Missing " + HeaderNames.SIGNATURE + " or " + HeaderNames.TIMESTAMP;
	private static final String INVALID_TIMESTAMP = "Invalid " + HeaderNames.TIMESTAMP;
	private static final String STALE_TIMESTAMP = "Timestamp outside allowed window";
	private static final String SIGNATURE_MISMATCH = "Signature mismatch";

	private final String secret;
	private final long maxSkewSeconds;
	private final ApiErrorWriter errorWriter;

	public HmacAuthFilter(@Value("${app.hmac.secret}") String secret,
			@Value("${app.hmac.max-skew-seconds:300}") long maxSkewSeconds,
			ApiErrorWriter errorWriter) {
		this.secret = secret;
		this.maxSkewSeconds = maxSkewSeconds;
		this.errorWriter = errorWriter;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		if (isExcluded(exchange.getRequest().getPath().value())) {
			return chain.filter(exchange);
		}
		return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, request ->
				DataBufferUtils.join(request.getBody())
						.map(HmacAuthFilter::readBytes)
						.defaultIfEmpty(new byte[0])
						.flatMap(body -> {
							String rejection = rejectionReason(request, body);
							if (rejection != null) {
								return errorWriter.write(exchange, HttpStatus.BAD_REQUEST,
										ErrorCode.INVALID_SIGNATURE, rejection);
							}
							return chain.filter(exchange.mutate().request(request).build());
						}));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	private boolean isExcluded(String path) {
		return EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
	}

	private String rejectionReason(ServerHttpRequest request, byte[] body) {
		HttpHeaders headers = request.getHeaders();
		String signature = headers.getFirst(HeaderNames.SIGNATURE);
		String timestamp = headers.getFirst(HeaderNames.TIMESTAMP);
		if (signature == null || timestamp == null) {
			return MISSING_HEADERS;
		}
		long epochSeconds;
		try {
			epochSeconds = Long.parseLong(timestamp);
		} catch (NumberFormatException e) {
			return INVALID_TIMESTAMP;
		}
		if (Math.abs(Instant.now().getEpochSecond() - epochSeconds) > maxSkewSeconds) {
			return STALE_TIMESTAMP;
		}
		CanonicalRequest canonicalRequest = CanonicalRequest.of(
				request.getMethod().name(),
				request.getPath().value(),
				singleValuedQuery(request.getQueryParams()),
				epochSeconds,
				body);
		return HmacUtils.verify(secret, canonicalRequest.toSigningString(), signature) ? null : SIGNATURE_MISMATCH;
	}

	private static byte[] readBytes(DataBuffer buffer) {
		try {
			byte[] bytes = new byte[buffer.readableByteCount()];
			buffer.read(bytes);
			return bytes;
		} finally {
			DataBufferUtils.release(buffer);
		}
	}

	private static Map<String, String> singleValuedQuery(MultiValueMap<String, String> queryParams) {
		Map<String, String> flattened = new HashMap<>();
		queryParams.forEach((key, values) -> {
			if (!values.isEmpty()) {
				flattened.put(key, values.getFirst());
			}
		});
		return flattened;
	}
}
