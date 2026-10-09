package com.fintech.wallet.notification.service;

import com.fintech.wallet.notification.domain.DeadLetterEvent;
import com.fintech.wallet.notification.domain.NotificationLog;
import com.fintech.wallet.notification.repository.DeadLetterEventRepository;
import com.fintech.wallet.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID TRANSACTION_ID = UUID.randomUUID();

	private final NotificationLogRepository logs = mock(NotificationLogRepository.class);
	private final DeadLetterEventRepository deadLetters = mock(DeadLetterEventRepository.class);
	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final TemplateEngine templateEngine = mock(TemplateEngine.class);
	private final NotificationService service = new NotificationService(logs, deadLetters, mailSender,
			templateEngine, "no-reply@wallet.local", "demo@wallet.local");

	@Test
	void marksTheLogSentWhenTheMailGoesOut() {
		stubPersistence();
		when(mailSender.createMimeMessage()).thenReturn(new org.springframework.mail.javamail.JavaMailSenderImpl()
				.createMimeMessage());
		when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<html>ok</html>");

		service.notify(USER_ID, TRANSACTION_ID, NotificationService.TEMPLATE_BALANCE_CHANGED, "Biến động",
				Map.of("amount", "1000"), "{}");

		assertThat(saved().getStatus()).isEqualTo(NotificationLog.SendStatus.SENT);
		assertThat(saved().getSentAt()).isNotNull();
		verifyNoInteractions(deadLetters);
	}

	@Test
	void recordsADeadLetterWhenTheMailFails() {
		stubPersistence();
		when(mailSender.createMimeMessage()).thenThrow(new MailSendException("smtp down"));

		service.notify(USER_ID, TRANSACTION_ID, NotificationService.TEMPLATE_BALANCE_CHANGED, "Biến động",
				Map.of("amount", "1000"), "{\"raw\":true}");

		assertThat(saved().getStatus()).isEqualTo(NotificationLog.SendStatus.FAILED);
		verify(deadLetters).save(any(DeadLetterEvent.class));
	}

	@Test
	void skipsAnEventThatWasAlreadyNotified() {
		when(logs.existsByTemplateCodeAndRelatedTransactionId(anyString(), any())).thenReturn(true);

		service.notify(USER_ID, TRANSACTION_ID, NotificationService.TEMPLATE_BALANCE_CHANGED, "Biến động",
				Map.of(), "{}");

		verify(logs, never()).save(any(NotificationLog.class));
		verifyNoInteractions(mailSender);
	}

	private void stubPersistence() {
		when(logs.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private NotificationLog saved() {
		var captor = org.mockito.ArgumentCaptor.forClass(NotificationLog.class);
		verify(logs, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		return captor.getValue();
	}
}
