package com.fintech.wallet.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.hmac.HmacUtils;
import com.fintech.wallet.payment.config.PaymentProperties;
import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.domain.WebhookLog;
import com.fintech.wallet.payment.repository.PaymentIntentRepository;
import com.fintech.wallet.payment.repository.WebhookLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebhookServiceTest {

	private static final String SECRET = "dev-vietqr-secret";
	private static final UUID INTENT_ID = UUID.randomUUID();
	private static final String BODY = """
			{"eventId":"evt-1","paymentIntentId":"%s","status":"SUCCEEDED","amount":500000,"currency":"VND"}
			""".formatted(INTENT_ID);

	private final WebhookLogRepository webhooks = mock(WebhookLogRepository.class);
	private final PaymentIntentRepository intents = mock(PaymentIntentRepository.class);
	private final WebhookRecorder recorder = mock(WebhookRecorder.class);
	private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
	private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
	private final ValueOperations<String, String> values = mock(ValueOperations.class);
	private final WebhookService service = new WebhookService(webhooks, intents, recorder, publisher, redis,
			new ObjectMapper(), new PaymentProperties(Map.of("VIETQR", SECRET), true, 10_000));

	@BeforeEach
	void setUp() {
		when(redis.opsForValue()).thenReturn(values);
	}

	@Test
	void rejectsAForgedSignatureWithoutTouchingTheDedupeSlot() {
		assertThatThrownBy(() -> service.receive(PaymentIntent.Provider.VIETQR, "forged", BODY))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SIGNATURE));

		// The whole point of docs 07 SEC-02: a forged callback must not consume the key, or the real
		// one that follows would be swallowed.
		verify(values, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
		verifyNoInteractions(webhooks, recorder, publisher);
	}

	@Test
	void processesASignedCallbackAndPublishesTheEvent() {
		WebhookLog receipt = new WebhookLog();
		when(webhooks.findByProviderAndEventId(any(), anyString())).thenReturn(Optional.empty());
		when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
		when(recorder.record(any(), any(), anyString(), anyString())).thenReturn(receipt);

		var response = service.receive(PaymentIntent.Provider.VIETQR, sign(BODY), BODY);

		assertThat(response.duplicate()).isFalse();
		verify(publisher).publish(receipt);
	}

	@Test
	void answersDuplicateWhenTheEventWasSeenBefore() {
		when(webhooks.findByProviderAndEventId(any(), anyString())).thenReturn(Optional.of(new WebhookLog()));

		var response = service.receive(PaymentIntent.Provider.VIETQR, sign(BODY), BODY);

		assertThat(response.duplicate()).isTrue();
		verifyNoInteractions(recorder, publisher);
	}

	@Test
	void answersDuplicateInsteadOf500WhenTwoCallbacksRace() {
		when(webhooks.findByProviderAndEventId(any(), anyString())).thenReturn(Optional.empty());
		when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
		when(recorder.record(any(), any(), anyString(), anyString()))
				.thenThrow(new DataIntegrityViolationException("uq_webhook_provider_event"));

		var response = service.receive(PaymentIntent.Provider.VIETQR, sign(BODY), BODY);

		assertThat(response.duplicate()).isTrue();
	}

	@Test
	void doesNotPublishWhenTheProviderReportsSomethingOtherThanSucceeded() {
		String failed = BODY.replace("SUCCEEDED", "FAILED");
		WebhookLog receipt = new WebhookLog();
		when(webhooks.findByProviderAndEventId(any(), anyString())).thenReturn(Optional.empty());
		when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
		when(recorder.record(any(), any(), anyString(), anyString())).thenReturn(receipt);

		var response = service.receive(PaymentIntent.Provider.VIETQR, sign(failed), failed);

		assertThat(response.duplicate()).isFalse();
		verifyNoInteractions(publisher);
	}

	private static String sign(String body) {
		return HmacUtils.sign(SECRET, body);
	}
}
