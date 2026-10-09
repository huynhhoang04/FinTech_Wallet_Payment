package com.fintech.wallet.payment.service;

import com.fintech.wallet.common.dto.PageResponse;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.util.IdUtils;
import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.dto.CreateIntentRequest;
import com.fintech.wallet.payment.dto.PaymentIntentResponse;
import com.fintech.wallet.payment.repository.PaymentIntentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * An intent is a request to pay, not a payment: nothing is credited here. The provider confirms it
 * through a webhook, and the transaction engine credits the wallet from the resulting event
 * (docs 02 section 11).
 */
@Slf4j
@Service
public class PaymentIntentService {

	private static final Duration INTENT_TTL = Duration.ofMinutes(15);
	private static final int MAX_PAGE_SIZE = 100;

	private final PaymentIntentRepository intents;

	public PaymentIntentService(PaymentIntentRepository intents) {
		this.intents = intents;
	}

	@Transactional
	public PaymentIntentResponse create(String idempotencyKey, UUID userId, CreateIntentRequest request) {
		Optional<PaymentIntent> existing = intents.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return PaymentIntentResponse.of(existing.get());
		}

		PaymentIntent intent = new PaymentIntent();
		intent.setIdempotencyKey(idempotencyKey);
		intent.setUserId(userId);
		intent.setWalletAccountId(request.accountId());
		intent.setProvider(request.provider());
		intent.setProviderRef(IdUtils.newId());
		intent.setAmount(request.amount());
		intent.setCurrency(request.currency());
		intent.setStatus(PaymentIntent.PaymentStatus.PENDING);
		intent.setCheckoutUrl("https://sandbox." + request.provider().name().toLowerCase() + "/pay/"
				+ intent.getProviderRef());
		intent.setExpiresAt(Instant.now().plus(INTENT_TTL));
		return PaymentIntentResponse.of(intents.save(intent));
	}

	/** The caller must own the intent; answers 404 so a probe cannot tell intents apart (docs 07 SEC-04). */
	@Transactional(readOnly = true)
	public PaymentIntentResponse getOwned(UUID paymentIntentId, UUID userId) {
		PaymentIntent intent = require(paymentIntentId);
		if (!userId.equals(intent.getUserId())) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên nạp tiền");
		}
		return PaymentIntentResponse.of(intent);
	}

	@Transactional(readOnly = true)
	public PageResponse<PaymentIntentResponse> list(UUID userId, int page, int size) {
		Page<PaymentIntent> found = intents.findByUserIdOrderByCreatedAtDesc(userId,
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
		return PageResponse.of(found.getContent().stream().map(PaymentIntentResponse::of).toList(),
				found.getNumber(), found.getSize(), found.getTotalElements());
	}

	/** A checkout session that was never paid should not sit in PENDING forever (docs 07 FIN-13). */
	@Scheduled(fixedDelayString = "${app.payment.expiry-interval-ms:60000}")
	@Transactional
	public void expireOverdueIntents() {
		List<PaymentIntent> overdue = intents.findByStatusAndExpiresAtBefore(PaymentIntent.PaymentStatus.PENDING,
				Instant.now());
		if (overdue.isEmpty()) {
			return;
		}
		overdue.forEach(intent -> intent.setStatus(PaymentIntent.PaymentStatus.EXPIRED));
		intents.saveAll(overdue);
		log.info("Expired {} payment intents", overdue.size());
	}

	PaymentIntent require(UUID paymentIntentId) {
		return intents.findById(paymentIntentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên nạp tiền"));
	}
}
