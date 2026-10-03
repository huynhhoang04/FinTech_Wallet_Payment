package com.fintech.wallet.payment.controller;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.dto.WebhookResponse;
import com.fintech.wallet.payment.service.WebhookService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
public class WebhookController {

	private final WebhookService webhooks;

	public WebhookController(WebhookService webhooks) {
		this.webhooks = webhooks;
	}

	/**
	 * The body is taken as a raw string on purpose: the partner signature is computed over the exact
	 * bytes received, so anything that re-serialises the JSON would break verification.
	 */
	@PostMapping(value = "/webhooks/{provider}", consumes = MediaType.ALL_VALUE)
	public WebhookResponse receive(@PathVariable String provider,
			@RequestHeader(value = HeaderNames.PROVIDER_SIGNATURE, required = false) String signature,
			@RequestBody String rawBody) {
		return webhooks.receive(parseProvider(provider), signature, rawBody);
	}

	private static PaymentIntent.Provider parseProvider(String provider) {
		try {
			return PaymentIntent.Provider.valueOf(provider.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Provider không hỗ trợ: " + provider);
		}
	}
}
