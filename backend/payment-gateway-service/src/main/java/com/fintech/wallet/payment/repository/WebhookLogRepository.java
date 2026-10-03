package com.fintech.wallet.payment.repository;

import com.fintech.wallet.payment.domain.PaymentIntent;
import com.fintech.wallet.payment.domain.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {

	Optional<WebhookLog> findByProviderAndEventId(PaymentIntent.Provider provider, String eventId);

	List<WebhookLog> findTop50BySignatureValidIsTrueAndPublishedAtIsNullOrderByCreatedAtAsc();
}
