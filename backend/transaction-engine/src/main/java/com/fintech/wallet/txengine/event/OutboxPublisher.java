package com.fintech.wallet.txengine.event;

import com.fintech.wallet.txengine.domain.OutboxEvent;
import com.fintech.wallet.txengine.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Publishes the outbox after the fact, never from inside the business transaction. An event that
 * cannot be sent stays NEW and is retried, so a Kafka outage never loses it; after a few attempts it
 * is moved to FAILED instead of being retried forever (docs 07 FIN-08).
 *
 * <p>Two instances would let two schedulers publish the same row. Consumers are idempotent so that
 * is not a correctness problem, but a real cluster wants ShedLock or an atomic claim - deliberately
 * out of scope while the deployment is a single instance.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

	private static final int MAX_ATTEMPTS = 5;

	private final OutboxEventRepository outbox;
	private final KafkaTemplate<String, String> kafka;
	private final Map<UUID, Integer> failedAttempts = new ConcurrentHashMap<>();

	public OutboxPublisher(OutboxEventRepository outbox, KafkaTemplate<String, String> kafka) {
		this.outbox = outbox;
		this.kafka = kafka;
	}

	@Scheduled(fixedDelayString = "${app.outbox.interval-ms:10000}")
	public void publishNewEvents() {
		for (OutboxEvent event : outbox.findTop50ByStatusOrderByCreatedAtAsc(OutboxEvent.Status.NEW)) {
			publish(event);
		}
	}

	private void publish(OutboxEvent event) {
		try {
			kafka.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload())
					.get(5, TimeUnit.SECONDS);
			event.setStatus(OutboxEvent.Status.PUBLISHED);
			event.setPublishedAt(Instant.now());
			outbox.save(event);
			failedAttempts.remove(event.getId());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			int attempts = failedAttempts.merge(event.getId(), 1, Integer::sum);
			if (attempts >= MAX_ATTEMPTS) {
				event.setStatus(OutboxEvent.Status.FAILED);
				outbox.save(event);
				failedAttempts.remove(event.getId());
				log.error("Outbox event {} moved to FAILED after {} attempts: {}", event.getId(), attempts,
						e.toString());
			} else {
				log.warn("Outbox event {} still pending (attempt {}), will retry: {}", event.getId(), attempts,
						e.toString());
			}
		}
	}
}
