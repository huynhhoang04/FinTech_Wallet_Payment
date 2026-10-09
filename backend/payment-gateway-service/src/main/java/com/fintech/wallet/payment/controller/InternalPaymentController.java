package com.fintech.wallet.payment.controller;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.payment.dto.CreateIntentRequest;
import com.fintech.wallet.payment.dto.InternalIntentRequest;
import com.fintech.wallet.payment.dto.PaymentIntentResponse;
import com.fintech.wallet.payment.service.PaymentIntentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the transaction engine when a deposit starts, behind the internal token. It exists so
 * that service-to-service calls do not have to forward the user's JWT (docs 04 section 0.1).
 */
@RestController
public class InternalPaymentController {

	private final PaymentIntentService intents;

	public InternalPaymentController(PaymentIntentService intents) {
		this.intents = intents;
	}

	@PostMapping("/internal/payments/intents")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentIntentResponse create(
			@RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody InternalIntentRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu " + HeaderNames.IDEMPOTENCY_KEY);
		}
		return intents.create(idempotencyKey, request.userId(), new CreateIntentRequest(request.accountId(),
				request.amount(), request.currency(), request.provider(), null));
	}
}
