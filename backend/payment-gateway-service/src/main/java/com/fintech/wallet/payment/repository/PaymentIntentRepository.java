package com.fintech.wallet.payment.repository;

import com.fintech.wallet.payment.domain.PaymentIntent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

	Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);

	Page<PaymentIntent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
