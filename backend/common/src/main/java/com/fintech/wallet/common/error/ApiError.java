package com.fintech.wallet.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		String requestId,
		String traceId,
		List<Detail> details) {

	public record Detail(String field, String issue) {
	}

	public static ApiError of(ErrorCode errorCode, String message, String path, String requestId, String traceId) {
		return of(errorCode, message, path, requestId, traceId, null);
	}

	public static ApiError of(ErrorCode errorCode, String message, String path, String requestId, String traceId,
			List<Detail> details) {
		return new ApiError(Instant.now(), errorCode.httpStatus(), errorCode.name(),
				message != null ? message : errorCode.defaultMessage(), path, requestId, traceId, details);
	}
}
