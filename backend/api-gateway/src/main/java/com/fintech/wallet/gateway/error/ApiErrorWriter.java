package com.fintech.wallet.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.ApiError;
import com.fintech.wallet.common.error.ErrorCode;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class ApiErrorWriter {

	private final ObjectMapper objectMapper;

	public ApiErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode, String message) {
		ServerHttpResponse response = exchange.getResponse();
		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		DataBuffer buffer = response.bufferFactory().wrap(serialize(errorCode,
				ApiError.of(errorCode, message, exchange.getRequest().getPath().value(),
						exchange.getRequest().getHeaders().getFirst(HeaderNames.REQUEST_ID), null)));
		return response.writeWith(Mono.just(buffer));
	}

	private byte[] serialize(ErrorCode errorCode, ApiError body) {
		try {
			return objectMapper.writeValueAsBytes(body);
		} catch (JsonProcessingException e) {
			return ("{\"code\":\"" + errorCode.name() + "\"}").getBytes(StandardCharsets.UTF_8);
		}
	}
}
