package com.fintech.wallet.txengine.service;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Asks the payment gateway for a checkout session through its internal API. The call carries the
 * internal token rather than the caller's JWT, because this is a service-to-service call (docs 04
 * section 0.1).
 */
@Component
public class PaymentGatewayClient {

	private final RestClient client;
	private final String internalToken;

	public PaymentGatewayClient(RestClient.Builder builder,
			@Value("${app.payment-gateway.base-url}") String baseUrl,
			@Value("${app.internal.token}") String internalToken) {
		this.client = builder.baseUrl(baseUrl).build();
		this.internalToken = internalToken;
	}

	public IntentCreation createIntent(String idempotencyKey, UUID userId, UUID accountId, long amount, String currency,
			String provider) {
		try {
			IntentCreation intent = client.post()
					.uri("/internal/payments/intents")
					.header(HeaderNames.INTERNAL_TOKEN, internalToken)
					.header(HeaderNames.IDEMPOTENCY_KEY, idempotencyKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("userId", userId.toString(), "accountId", accountId.toString(), "amount", amount,
							"currency", currency, "provider", provider))
					.retrieve()
					.body(IntentCreation.class);
			if (intent == null || intent.paymentIntentId() == null) {
				throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Payment gateway trả về rỗng");
			}
			return intent;
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			throw new BusinessException(ErrorCode.UPSTREAM_UNAVAILABLE, "Payment gateway lỗi: " + e);
		}
	}

	public record IntentCreation(UUID paymentIntentId, String status, String checkoutUrl, String qrContent) {
	}
}
