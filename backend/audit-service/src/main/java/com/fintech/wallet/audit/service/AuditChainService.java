package com.fintech.wallet.audit.service;

import com.fintech.wallet.audit.domain.AuditLog;
import com.fintech.wallet.audit.repository.AuditLogRepository;
import com.fintech.wallet.common.hmac.HmacUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The hash chain of docs 03 section 7.1: {@code hash = SHA256(prevHash + payload)}. Editing any row
 * breaks every hash after it, which is what {@link #verifyChain()} detects.
 */
@Service
public class AuditChainService {

	private final AuditLogRepository logs;

	public AuditChainService(AuditLogRepository logs) {
		this.logs = logs;
	}

	/** Empty when the event was already recorded, so a Kafka redelivery changes nothing. */
	@Transactional
	public Optional<AuditLog> append(UUID eventId, String eventType, UUID aggregateId, String actor, String payload,
			Instant occurredAt) {
		if (logs.existsByEventId(eventId)) {
			return Optional.empty();
		}
		AuditLog previous = logs.findFirstByOrderByRecordedAtDesc();
		String prevHash = previous == null ? null : previous.getHash();

		AuditLog entry = new AuditLog();
		entry.setEventId(eventId);
		entry.setEventType(eventType);
		entry.setAggregateId(aggregateId);
		entry.setActor(actor);
		entry.setPayload(payload);
		entry.setPrevHash(prevHash);
		entry.setHash(hash(prevHash, payload));
		entry.setOccurredAt(occurredAt);
		entry.setRecordedAt(Instant.now());
		return Optional.of(logs.save(entry));
	}

	@Transactional(readOnly = true)
	public ChainVerification verifyChain() {
		List<AuditLog> entries = logs.findAllByOrderByRecordedAtAsc();
		String expectedPrevHash = null;
		int checked = 0;
		for (AuditLog entry : entries) {
			boolean linkBroken = !Objects.equals(expectedPrevHash, entry.getPrevHash());
			boolean hashBroken = !hash(expectedPrevHash, entry.getPayload()).equals(entry.getHash());
			if (linkBroken || hashBroken) {
				return new ChainVerification(false, checked, entry.getId());
			}
			expectedPrevHash = entry.getHash();
			checked++;
		}
		return new ChainVerification(true, checked, null);
	}

	public static String hash(String prevHash, String payload) {
		return HmacUtils.sha256Hex((prevHash == null ? "" : prevHash) + payload);
	}

	public record ChainVerification(boolean valid, int checkedEntries, UUID firstBrokenAt) {
	}
}
