package com.fintech.wallet.common.error;

import com.fintech.wallet.common.constant.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
		ApiError body = ApiError.of(ex.errorCode(), ex.getMessage(), request.getRequestURI(),
				request.getHeader(HeaderNames.REQUEST_ID), null);
		return ResponseEntity.status(body.status()).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiError.Detail(error.getField(), error.getDefaultMessage()))
				.toList();
		ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR, null, request.getRequestURI(),
				request.getHeader(HeaderNames.REQUEST_ID), null, details);
		return ResponseEntity.status(body.status()).body(body);
	}
}
