package com.fintech.wallet.audit.service;

import com.fintech.wallet.audit.domain.AuditLog;
import com.fintech.wallet.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditChainServiceTest {

	private final AuditLogRepository logs = mock(AuditLogRepository.class);
	private final AuditChainService chain = new AuditChainService(logs);
	private final List<AuditLog> stored = new ArrayList<>();

	@Test
	void appendsEntriesThatLinkToTheirPredecessor() {
		when(logs.findFirstByOrderByRecordedAtDesc()).thenAnswer(invocation -> stored.isEmpty() ? null : stored.get(stored.size() - 1));
		when(logs.save(any(AuditLog.class))).thenAnswer(invocation -> {
			AuditLog entry = invocation.getArgument(0);
			stored.add(entry);
			return entry;
		});

		chain.append(UUID.randomUUID(), "tx.completed", null, "SYSTEM", "{\"amount\":100}", Instant.now());
		chain.append(UUID.randomUUID(), "tx.completed", null, "SYSTEM", "{\"amount\":200}", Instant.now());

		assertThat(stored).hasSize(2);
		assertThat(stored.get(0).getPrevHash()).isNull();
		assertThat(stored.get(1).getPrevHash()).isEqualTo(stored.get(0).getHash());
		assertThat(stored.get(1).getHash())
				.isEqualTo(AuditChainService.hash(stored.get(0).getHash(), "{\"amount\":200}"));
	}

	@Test
	void detectsATamperedPayload() {
		when(logs.findFirstByOrderByRecordedAtDesc()).thenAnswer(invocation -> stored.isEmpty() ? null : stored.get(stored.size() - 1));
		when(logs.save(any(AuditLog.class))).thenAnswer(invocation -> {
			AuditLog entry = invocation.getArgument(0);
			stored.add(entry);
			return entry;
		});
		chain.append(UUID.randomUUID(), "tx.completed", null, "SYSTEM", "{\"amount\":100}", Instant.now());
		chain.append(UUID.randomUUID(), "tx.completed", null, "SYSTEM", "{\"amount\":200}", Instant.now());

		when(logs.findAllByOrderByRecordedAtAsc()).thenReturn(stored);
		assertThat(chain.verifyChain().valid()).isTrue();

		stored.get(0).setPayload("{\"amount\":999}");
		var verification = chain.verifyChain();

		assertThat(verification.valid()).isFalse();
		assertThat(verification.checkedEntries()).isZero();
		assertThat(verification.firstBrokenAt()).isEqualTo(stored.get(0).getId());
	}
}
