package com.fintech.wallet.payment.controller;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.dto.PageResponse;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.security.CurrentUser;
import com.fintech.wallet.payment.dto.CreateIntentRequest;
import com.fintech.wallet.payment.dto.PaymentIntentResponse;
import com.fintech.wallet.payment.dto.WebhookResponse;
import com.fintech.wallet.payment.service.PaymentIntentService;
import com.fintech.wallet.payment.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PaymentController {

	private final PaymentIntentService intents;
	private final WebhookService webhooks;

	public PaymentController(PaymentIntentService intents, WebhookService webhooks) {
		this.intents = intents;
		this.webhooks = webhooks;
	}

	@PostMapping("/api/v1/payments/intents")
	@ResponseStatus(HttpStatus.CREATED)
	public PaymentIntentResponse create(
			@RequestHeader(value = HeaderNames.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody CreateIntentRequest request) {
		return intents.create(requireIdempotencyKey(idempotencyKey), UUID.fromString(CurrentUser.userId()), request);
	}

	@GetMapping("/api/v1/payments/intents/{paymentIntentId}")
	public PaymentIntentResponse get(@PathVariable UUID paymentIntentId) {
		return intents.get(paymentIntentId);
	}

	@GetMapping("/api/v1/payments")
	public PageResponse<PaymentIntentResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return intents.list(UUID.fromString(CurrentUser.userId()), page, size);
	}

	/** Demo only: pretends the provider called back, so the deposit flow works without a sandbox. */
	@PostMapping("/internal/payments/{paymentIntentId}/simulate-callback")
	public WebhookResponse simulateCallback(@PathVariable UUID paymentIntentId) {
		return webhooks.simulateCallback(paymentIntentId);
	}

	private static String requireIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thiếu " + HeaderNames.IDEMPOTENCY_KEY);
		}
		return idempotencyKey;
	}
}
