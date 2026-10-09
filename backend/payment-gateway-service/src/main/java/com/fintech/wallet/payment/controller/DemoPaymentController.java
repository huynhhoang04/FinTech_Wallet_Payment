package com.fintech.wallet.payment.controller;

import com.fintech.wallet.payment.dto.WebhookResponse;
import com.fintech.wallet.payment.service.WebhookService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Demo only. It exists so the deposit flow can be exercised without a provider sandbox, and it is
 * switched off outside dev: a bean that mints money must not be reachable in production
 * (docs 07 SEC-01). With {@code app.payment.demo-endpoints=false} the endpoint simply is not there.
 */
@RestController
@ConditionalOnProperty(name = "app.payment.demo-endpoints", havingValue = "true", matchIfMissing = true)
public class DemoPaymentController {

	private final WebhookService webhooks;

	public DemoPaymentController(WebhookService webhooks) {
		this.webhooks = webhooks;
	}

	@PostMapping("/internal/payments/{paymentIntentId}/simulate-callback")
	public WebhookResponse simulateCallback(@PathVariable UUID paymentIntentId) {
		return webhooks.simulateCallback(paymentIntentId);
	}
}
