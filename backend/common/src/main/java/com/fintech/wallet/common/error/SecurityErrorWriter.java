package com.fintech.wallet.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Renders {@link ApiError} for failures raised by the servlet security filter chain (401/403), which
 * never reach {@code GlobalExceptionHandler}. Without this Spring Security answers with an empty
 * body, breaking the error contract of docs 04 section 0.4.
 */
@Component
public class SecurityErrorWriter {

	private final ObjectMapper objectMapper;

	public SecurityErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode, String message) {
		response.setStatus(errorCode.httpStatus());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		try {
			objectMapper.writeValue(response.getOutputStream(), ApiError.of(errorCode, message,
					request.getRequestURI(), request.getHeader(HeaderNames.REQUEST_ID), null));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot render ApiError", e);
		}
	}
}
