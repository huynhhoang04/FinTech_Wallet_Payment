package com.fintech.wallet.notification.controller;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.notification.domain.NotificationLog;
import com.fintech.wallet.notification.repository.DeadLetterEventRepository;
import com.fintech.wallet.notification.repository.NotificationLogRepository;
import com.fintech.wallet.notification.service.LedgerAccountsClient;
import com.fintech.wallet.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Notification rows are keyed by the wallet account of the balance event, while the caller is a
 * user id; these tests pin the bridge through the ledger (the list used to return nothing because
 * the query compared the account id against the user id).
 */
class NotificationControllerTest {

	private static final String USER_ID = UUID.randomUUID().toString();
	private static final UUID ACCOUNT_ID = UUID.randomUUID();

	private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
	private final DeadLetterEventRepository deadLetters = mock(DeadLetterEventRepository.class);
	private final NotificationService notifications = mock(NotificationService.class);
	private final LedgerAccountsClient accounts = mock(LedgerAccountsClient.class);
	private final NotificationController controller = new NotificationController(logs, deadLetters, notifications,
			accounts);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void listsNotificationsOfTheAccountsTheCallerOwns() {
		authenticateAs(USER_ID);
		when(accounts.accountIdsOf(USER_ID)).thenReturn(List.of(ACCOUNT_ID));
		when(logs.findByUserIdInOrderByCreatedAtDesc(anyCollection(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(row(ACCOUNT_ID)), PageRequest.of(0, 20), 1));

		var response = controller.list(0, 20);

		assertThat(response.content()).hasSize(1);
		assertThat(response.totalElements()).isEqualTo(1);
	}

	@Test
	void returnsAnEmptyPageWithoutQueryingWhenTheUserOwnsNoWallet() {
		authenticateAs(USER_ID);
		when(accounts.accountIdsOf(USER_ID)).thenReturn(List.of());

		var response = controller.list(0, 20);

		assertThat(response.content()).isEmpty();
		assertThat(response.totalElements()).isZero();
		verify(logs, never()).findByUserIdInOrderByCreatedAtDesc(anyCollection(), any(Pageable.class));
	}

	@Test
	void hidesARowThatBelongsToAnotherWallet() {
		authenticateAs(USER_ID);
		NotificationLog entry = row(UUID.randomUUID());
		when(logs.findById(entry.getId())).thenReturn(Optional.of(entry));
		when(accounts.accountIdsOf(USER_ID)).thenReturn(List.of(ACCOUNT_ID));

		assertThatThrownBy(() -> controller.get(entry.getId()))
				.isInstanceOf(BusinessException.class);
	}

	private static void authenticateAs(String userId) {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(userId, null, List.of()));
	}

	private static NotificationLog row(UUID accountId) {
		NotificationLog entry = new NotificationLog();
		ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
		entry.setUserId(accountId);
		entry.setChannel(NotificationLog.Channel.EMAIL);
		entry.setTemplateCode("BALANCE_CHANGED");
		entry.setRecipient("demo@wallet.local");
		entry.setSubject("Biến động số dư");
		entry.setStatus(NotificationLog.SendStatus.SENT);
		return entry;
	}
}
