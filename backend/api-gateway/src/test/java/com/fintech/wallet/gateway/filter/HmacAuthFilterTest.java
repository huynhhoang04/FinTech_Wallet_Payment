package com.fintech.wallet.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.hmac.CanonicalRequest;
import com.fintech.wallet.common.hmac.HmacUtils;
import com.fintech.wallet.gateway.error.ApiErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HmacAuthFilterTest {

	private static final String SECRET = "test-hmac-secret";
	private static final long MAX_SKEW_SECONDS = 300;

	private final ApiErrorWriter errorWriter = new ApiErrorWriter(new ObjectMapper().findAndRegisterModules());
	private final HmacAuthFilter filter = new HmacAuthFilter(SECRET, MAX_SKEW_SECONDS, errorWriter);

	@Test
	void acceptsValidSignatureAndKeepsBodyReadableDownstream() {
		byte[] body = "{\"phone\":\"0912345678\"}".getBytes(StandardCharsets.UTF_8);
		long timestamp = Instant.now().getEpochSecond();
		MockServerWebExchange exchange = post("/api/v1/auth/login?a=1&b=2", body,
				sign("POST", "/api/v1/auth/login", Map.of("a", "1", "b", "2"), timestamp, body), timestamp);

		AtomicReference<byte[]> downstreamBody = new AtomicReference<>(new byte[0]);
		filter.filter(exchange, current -> DataBufferUtils.join(current.getRequest().getBody())
						.map(buffer -> {
							byte[] bytes = new byte[buffer.readableByteCount()];
							buffer.read(bytes);
							DataBufferUtils.release(buffer);
							downstreamBody.set(bytes);
							return true;
						})
						.defaultIfEmpty(false)
						.then())
				.block();

		assertThat(exchange.getResponse().getStatusCode()).isNull();
		assertThat(new String(downstreamBody.get(), StandardCharsets.UTF_8)).isEqualTo(new String(body, StandardCharsets.UTF_8));
	}

	@Test
	void acceptsGetWithoutBody() {
		long timestamp = Instant.now().getEpochSecond();
		MockServerWebExchange exchange = get("/api/v1/accounts",
				sign("GET", "/api/v1/accounts", Map.of(), timestamp, new byte[0]), timestamp);

		AtomicBoolean called = new AtomicBoolean(false);
		filter.filter(exchange, current -> {
			called.set(true);
			return Mono.empty();
		}).block();

		assertThat(called).isTrue();
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void rejectsRequestWithoutSignature() {
		MockServerWebExchange exchange = post("/api/v1/auth/login", "{}".getBytes(StandardCharsets.UTF_8), null,
				Instant.now().getEpochSecond());

		filter.filter(exchange, current -> Mono.empty()).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exchange.getResponse().getBodyAsString().block())
				.contains(ErrorCode.INVALID_SIGNATURE.name());
	}

	@Test
	void rejectsRequestWithTamperedSignature() {
		MockServerWebExchange exchange = post("/api/v1/auth/login", "{}".getBytes(StandardCharsets.UTF_8),
				"deadbeef", Instant.now().getEpochSecond());

		filter.filter(exchange, current -> Mono.empty()).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exchange.getResponse().getBodyAsString().block())
				.contains(ErrorCode.INVALID_SIGNATURE.name());
	}

	@Test
	void rejectsStaleTimestampEvenWithValidSignature() {
		byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
		long timestamp = Instant.now().getEpochSecond() - MAX_SKEW_SECONDS - 1;
		MockServerWebExchange exchange = post("/api/v1/auth/login", body,
				sign("POST", "/api/v1/auth/login", Map.of(), timestamp, body), timestamp);

		filter.filter(exchange, current -> Mono.empty()).block();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(exchange.getResponse().getBodyAsString().block())
				.contains(ErrorCode.INVALID_SIGNATURE.name());
	}

	@Test
	void skipsSignatureCheckForExcludedPath() {
		MockServerWebExchange exchange = post("/webhooks/vietqr", "{}".getBytes(StandardCharsets.UTF_8), null,
				Instant.now().getEpochSecond());

		AtomicBoolean called = new AtomicBoolean(false);
		filter.filter(exchange, current -> {
			called.set(true);
			return Mono.empty();
		}).block();

		assertThat(called).isTrue();
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	private static String sign(String method, String path, Map<String, String> query, long timestamp, byte[] body) {
		return HmacUtils.sign(SECRET, CanonicalRequest.of(method, path, query, timestamp, body).toSigningString());
	}

	private static MockServerWebExchange post(String url, byte[] body, String signature, long timestamp) {
		return MockServerWebExchange.from(MockServerHttpRequest.post(url)
				.headers(headers(signature, timestamp))
				.body(new String(body, StandardCharsets.UTF_8)));
	}

	private static MockServerWebExchange get(String url, String signature, long timestamp) {
		return MockServerWebExchange.from(MockServerHttpRequest.get(url)
				.headers(headers(signature, timestamp))
				.build());
	}

	private static HttpHeaders headers(String signature, long timestamp) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HeaderNames.TIMESTAMP, String.valueOf(timestamp));
		if (signature != null) {
			headers.set(HeaderNames.SIGNATURE, signature);
		}
		return headers;
	}
}
