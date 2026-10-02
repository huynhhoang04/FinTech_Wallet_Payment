package com.fintech.wallet.txengine.event;

import com.fintech.wallet.txengine.domain.OutboxEvent;
import com.fintech.wallet.txengine.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Publishes the outbox after the fact, never from inside the business transaction. An event that
 * cannot be sent stays NEW and is retried on the next tick, so a Kafka outage never loses it.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

	private final OutboxEventRepository outbox;
	private final KafkaTemplate<String, String> kafka;

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
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.warn("Outbox event {} ({}) still pending, will retry: {}", event.getId(), event.getEventType(),
					e.toString());
		}
	}
}
