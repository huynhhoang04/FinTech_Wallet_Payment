package com.fintech.wallet.notification.service;

import com.fintech.wallet.notification.domain.DeadLetterEvent;
import com.fintech.wallet.notification.domain.NotificationLog;
import com.fintech.wallet.notification.repository.DeadLetterEventRepository;
import com.fintech.wallet.notification.repository.NotificationLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sends the email and records the attempt. A send that fails leaves a FAILED log row and a
 * dead-letter row, so nothing disappears quietly (docs 02 section 7).
 *
 * <p>Recipient resolution is a known gap: identity lives in the ledger and the events carry account
 * ids only, so the demo addresses a configured mailbox. A real build would resolve the wallet owner
 * through identity before sending.
 */
@Slf4j
@Service
public class NotificationService {

	public static final String TEMPLATE_BALANCE_CHANGED = "BALANCE_CHANGED";
	public static final String TEMPLATE_PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
	public static final String TEMPLATE_RISK_ALERT = "RISK_ALERT";

	private static final String EMAIL_TEMPLATE = "email/notification";

	private final NotificationLogRepository logs;
	private final DeadLetterEventRepository deadLetters;
	private final JavaMailSender mailSender;
	private final TemplateEngine templateEngine;
	private final String from;
	private final String demoRecipient;

	public NotificationService(NotificationLogRepository logs,
			DeadLetterEventRepository deadLetters,
			JavaMailSender mailSender,
			TemplateEngine templateEngine,
			@Value("${app.notification.from}") String from,
			@Value("${app.notification.demo-recipient}") String demoRecipient) {
		this.logs = logs;
		this.deadLetters = deadLetters;
		this.mailSender = mailSender;
		this.templateEngine = templateEngine;
		this.from = from;
		this.demoRecipient = demoRecipient;
	}

	public void notify(UUID subjectAccountId, UUID relatedTransactionId, String templateCode, String subject,
			Map<String, Object> model, String rawPayload) {
		if (relatedTransactionId != null
				&& logs.existsByTemplateCodeAndRelatedTransactionId(templateCode, relatedTransactionId)) {
			log.debug("Notification {} for {} already sent, skipping", templateCode, relatedTransactionId);
			return;
		}

		NotificationLog entry = new NotificationLog();
		entry.setUserId(subjectAccountId);
		entry.setChannel(NotificationLog.Channel.EMAIL);
		entry.setTemplateCode(templateCode);
		entry.setRecipient(demoRecipient);
		entry.setSubject(subject);
		entry.setStatus(NotificationLog.SendStatus.PENDING);
		entry.setRelatedTransactionId(relatedTransactionId);
		entry = logs.save(entry);

		try {
			mailSender.send(buildMessage(demoRecipient, subject, model));
			entry.setStatus(NotificationLog.SendStatus.SENT);
			entry.setSentAt(Instant.now());
			entry.setProviderMessageId("smtp-" + entry.getId());
			logs.save(entry);
		} catch (Exception e) {
			entry.setStatus(NotificationLog.SendStatus.FAILED);
			entry.setErrorMessage(truncate(e.toString()));
			logs.save(entry);
			recordDeadLetter(templateCode, relatedTransactionId, rawPayload, e);
		}
	}

	private MimeMessage buildMessage(String recipient, String subject, Map<String, Object> model) throws Exception {
		Context context = new Context();
		context.setVariables(model);
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
		helper.setFrom(from);
		helper.setTo(recipient);
		helper.setSubject(subject);
		helper.setText(templateEngine.process(EMAIL_TEMPLATE, context), true);
		return message;
	}

	private void recordDeadLetter(String templateCode, UUID relatedTransactionId, String rawPayload, Exception cause) {
		DeadLetterEvent deadLetter = new DeadLetterEvent();
		deadLetter.setTopic(templateCode);
		deadLetter.setEventKey(relatedTransactionId == null ? null : relatedTransactionId.toString());
		deadLetter.setPayload(rawPayload == null ? "{}" : rawPayload);
		deadLetter.setErrorMessage(truncate(cause.toString()));
		deadLetter.setRetryCount(0);
		deadLetters.save(deadLetter);
		log.warn("Notification {} failed and was dead-lettered: {}", templateCode, cause.toString());
	}

	private static String truncate(String value) {
		return value == null || value.length() <= 500 ? value : value.substring(0, 500);
	}
}
