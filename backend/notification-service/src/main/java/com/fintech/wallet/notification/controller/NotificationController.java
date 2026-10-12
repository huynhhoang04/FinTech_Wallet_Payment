package com.fintech.wallet.notification.controller;

import com.fintech.wallet.common.dto.PageResponse;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.security.CurrentUser;
import com.fintech.wallet.notification.domain.DeadLetterEvent;
import com.fintech.wallet.notification.domain.NotificationLog;
import com.fintech.wallet.notification.repository.DeadLetterEventRepository;
import com.fintech.wallet.notification.repository.NotificationLogRepository;
import com.fintech.wallet.notification.service.LedgerAccountsClient;
import com.fintech.wallet.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The subject comes from the JWT, not from a query parameter (docs 07 SEC-03). Notification rows are
 * keyed by the wallet account the balance event belongs to, so the caller's accounts are resolved
 * through the ledger before filtering. The internal endpoints still name the user explicitly, and are
 * reachable only with the internal token.
 */
@RestController
public class NotificationController {

	private static final int MAX_PAGE_SIZE = 100;

	private final NotificationLogRepository logs;
	private final DeadLetterEventRepository deadLetters;
	private final NotificationService notifications;
	private final LedgerAccountsClient accounts;

	public NotificationController(NotificationLogRepository logs, DeadLetterEventRepository deadLetters,
			NotificationService notifications, LedgerAccountsClient accounts) {
		this.logs = logs;
		this.deadLetters = deadLetters;
		this.notifications = notifications;
		this.accounts = accounts;
	}

	@GetMapping("/api/v1/notifications")
	public PageResponse<NotificationView> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		List<UUID> accountIds = accounts.accountIdsOf(CurrentUser.userId());
		if (accountIds.isEmpty()) {
			return PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
		}
		Page<NotificationLog> found = logs.findByUserIdInOrderByCreatedAtDesc(accountIds, pageable);
		return PageResponse.of(found.getContent().stream().map(NotificationView::of).toList(),
				found.getNumber(), found.getSize(), found.getTotalElements());
	}

	@GetMapping("/api/v1/notifications/{id}")
	public NotificationView get(@PathVariable UUID id) {
		return NotificationView.of(requireOwned(id));
	}

	@PostMapping("/internal/notifications/test")
	public NotificationView test(@RequestParam UUID userId) {
		notifications.notify(userId, null, NotificationService.TEMPLATE_BALANCE_CHANGED, "Thông báo thử",
				Map.of("transactionId", "demo", "amount", "1000", "currency", "VND"), "{}");
		return logs.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1)).getContent().stream()
				.findFirst().map(NotificationView::of)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không gửi được thông báo"));
	}

	@GetMapping("/internal/notifications/dead-letters")
	public List<DeadLetterView> deadLetters() {
		return deadLetters.findTop50ByOrderByCreatedAtDesc().stream().map(DeadLetterView::of).toList();
	}

	private NotificationLog requireOwned(UUID id) {
		NotificationLog entry = logs.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo"));
		if (!accounts.accountIdsOf(CurrentUser.userId()).contains(entry.getUserId())) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo");
		}
		return entry;
	}

	public record NotificationView(UUID id, String channel, String templateCode, String subject, String status,
			String recipient, UUID relatedTransactionId, String sentAt) {

		static NotificationView of(NotificationLog entry) {
			return new NotificationView(entry.getId(), entry.getChannel().name(), entry.getTemplateCode(),
					entry.getSubject(), entry.getStatus().name(), entry.getRecipient(),
					entry.getRelatedTransactionId(), String.valueOf(entry.getSentAt()));
		}
	}

	public record DeadLetterView(UUID id, String topic, String eventKey, String errorMessage, int retryCount) {

		static DeadLetterView of(DeadLetterEvent event) {
			return new DeadLetterView(event.getId(), event.getTopic(), event.getEventKey(),
					event.getErrorMessage(), event.getRetryCount());
		}
	}
}
