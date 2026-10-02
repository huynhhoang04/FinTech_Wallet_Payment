package com.fintech.wallet.ledger.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.security.JwtUtils;
import com.fintech.wallet.ledger.dto.OtpRequestResponse;
import com.fintech.wallet.ledger.dto.OtpVerifyRequest;
import com.fintech.wallet.ledger.dto.OtpVerifyResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpServiceTest {

	private static final String SECRET = Base64.getEncoder()
			.encodeToString("ledger-test-jwt-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));

	private final JwtUtils jwtUtils = new JwtUtils(SECRET);
	private final OtpService otpService = new OtpService(jwtUtils);
	private final String userId = UUID.randomUUID().toString();

	@Test
	void requestIssuesReferenceWithTwoMinuteTtl() {
		AtomicReference<String> code = new AtomicReference<>();

		OtpRequestResponse issued = issueCapturingCode(code);

		assertThat(issued.otpRef()).isNotBlank();
		assertThat(issued.expiresIn()).isEqualTo(120);
		assertThat(code.get()).hasSize(6);
	}

	@Test
	void verifyAcceptsIssuedCodeAndMintsOtpToken() {
		AtomicReference<String> code = new AtomicReference<>();
		OtpRequestResponse issued = issueCapturingCode(code);

		OtpVerifyResponse verified = otpService.verify(userId, new OtpVerifyRequest(issued.otpRef(), code.get()));

		assertThat(verified.valid()).isTrue();
		assertThat(jwtUtils.parse(verified.otpToken()).getPayload().getSubject()).isEqualTo(userId);
		assertThat(jwtUtils.parse(verified.otpToken()).getPayload().get("otp")).isEqualTo(true);
	}

	@Test
	void verifyRejectsReuseOfAConsumedChallenge() {
		AtomicReference<String> code = new AtomicReference<>();
		OtpRequestResponse issued = issueCapturingCode(code);
		otpService.verify(userId, new OtpVerifyRequest(issued.otpRef(), code.get()));

		assertThatThrownBy(() -> otpService.verify(userId, new OtpVerifyRequest(issued.otpRef(), code.get())))
				.isInstanceOfSatisfying(BusinessException.class,
						throwable -> assertThat(throwable.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

	@Test
	void verifyRejectsUnknownReference() {
		assertThatThrownBy(() -> otpService.verify(userId, new OtpVerifyRequest("no-such-ref", "123456")))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void verifyRejectsChallengeIssuedToAnotherUser() {
		AtomicReference<String> code = new AtomicReference<>();
		OtpRequestResponse issued = issueCapturingCode(code);

		assertThatThrownBy(() -> otpService.verify(UUID.randomUUID().toString(),
				new OtpVerifyRequest(issued.otpRef(), code.get())))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void verifyRejectsWrongCodeWithoutConsumingTheChallenge() {
		AtomicReference<String> code = new AtomicReference<>();
		OtpRequestResponse issued = issueCapturingCode(code);

		assertThatThrownBy(() -> otpService.verify(userId, new OtpVerifyRequest(issued.otpRef(), "000000")))
				.isInstanceOf(BusinessException.class);

		assertThat(otpService.verify(userId, new OtpVerifyRequest(issued.otpRef(), code.get())).valid()).isTrue();
	}

	/** The demo has no SMS gateway, so the code is read from the service log - the same way a developer would. */
	private OtpRequestResponse issueCapturingCode(AtomicReference<String> code) {
		Logger logger = (Logger) LoggerFactory.getLogger(OtpService.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			OtpRequestResponse issued = otpService.request(userId);
			code.set(lastToken(appender));
			return issued;
		} finally {
			logger.detachAppender(appender);
		}
	}

	private static String lastToken(ListAppender<ILoggingEvent> appender) {
		return appender.list.stream()
				.map(ILoggingEvent::getFormattedMessage)
				.reduce((first, second) -> second)
				.map(message -> message.substring(message.lastIndexOf(' ') + 1))
				.orElseThrow(() -> new IllegalStateException("OTP code was not logged"));
	}
}
