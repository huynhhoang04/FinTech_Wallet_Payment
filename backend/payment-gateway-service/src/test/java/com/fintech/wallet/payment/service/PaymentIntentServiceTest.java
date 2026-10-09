package com.fintech.wallet.payment.service;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.dto.CreateIntentRequest;
import com.fintech.wallet.payment.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentIntentServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID ACCOUNT_ID = UUID.randomUUID();

	private final PaymentIntentRepository intents = mock(PaymentIntentRepository.class);
	private final PaymentIntentService service = new PaymentIntentService(intents);

	@Test
	void createsAPendingIntentWithACheckoutUrl() {
		when(intents.findByIdempotencyKey(any())).thenReturn(Optional.empty());
		when(intents.save(any(PaymentIntent.class))).thenAnswer(invocation -> {
			PaymentIntent intent = invocation.getArgument(0);
			ReflectionTestUtils.setField(intent, "id", UUID.randomUUID());
			return intent;
		});

		var response = service.create("key-1", USER_ID, request());

		assertThat(response.status()).isEqualTo("PENDING");
		assertThat(response.checkoutUrl()).contains("sandbox.vietqr");
		assertThat(response.expiresAt()).isAfter(Instant.now());
		assertThat(response.qrContent()).isNotBlank();
	}

	@Test
	void returnsTheExistingIntentForARepeatedKey() {
		when(intents.findByIdempotencyKey("key-1")).thenReturn(Optional.of(stored()));

		var response = service.create("key-1", USER_ID, request());

		assertThat(response.paymentIntentId()).isNotNull();
		verify(intents, never()).save(any(PaymentIntent.class));
	}

	@Test
	void expiresIntentsWhoseCheckoutWindowPassed() {
		PaymentIntent overdue = stored();
		when(intents.findByStatusAndExpiresAtBefore(any(), any())).thenReturn(List.of(overdue));

		service.expireOverdueIntents();

		assertThat(overdue.getStatus()).isEqualTo(PaymentIntent.PaymentStatus.EXPIRED);
		verify(intents).saveAll(List.of(overdue));
	}

	@Test
	void returnsTheIntentToItsOwner() {
		PaymentIntent intent = stored();
		when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));

		var response = service.getOwned(intent.getId(), USER_ID);

		assertThat(response.paymentIntentId()).isEqualTo(intent.getId());
	}

	@Test
	void hidesAnIntentFromAnotherUser() {
		PaymentIntent intent = stored();
		when(intents.findById(intent.getId())).thenReturn(Optional.of(intent));

		assertThatThrownBy(() -> service.getOwned(intent.getId(), UUID.randomUUID()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	private static PaymentIntent stored() {
		PaymentIntent intent = new PaymentIntent();
		intent.setIdempotencyKey("key-1");
		intent.setUserId(USER_ID);
		intent.setWalletAccountId(ACCOUNT_ID);
		intent.setProvider(PaymentIntent.Provider.VIETQR);
		intent.setProviderRef("ref-1");
		intent.setAmount(500_000L);
		intent.setCurrency("VND");
		intent.setStatus(PaymentIntent.PaymentStatus.PENDING);
		intent.setCheckoutUrl("https://sandbox.vietqr/pay/ref-1");
		intent.setExpiresAt(Instant.now().plusSeconds(600));
		ReflectionTestUtils.setField(intent, "id", UUID.randomUUID());
		return intent;
	}

	private static CreateIntentRequest request() {
		return new CreateIntentRequest(ACCOUNT_ID, 500_000, "VND", PaymentIntent.Provider.VIETQR, "app://result");
	}
}
