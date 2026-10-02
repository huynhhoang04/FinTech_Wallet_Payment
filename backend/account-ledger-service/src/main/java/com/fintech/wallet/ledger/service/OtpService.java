package com.fintech.wallet.ledger.service;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.security.JwtUtils;
import com.fintech.wallet.common.util.IdUtils;
import com.fintech.wallet.ledger.dto.OtpRequestResponse;
import com.fintech.wallet.ledger.dto.OtpVerifyRequest;
import com.fintech.wallet.ledger.dto.OtpVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OTP for large transactions (docs 04 section 2.5). Challenges live in memory with a short TTL:
 * there is no SMS/provider gateway in the demo and no Redis dependency in this service, so a restart
 * drops pending codes. Single-instance only.
 */
@Slf4j
@Service
public class OtpService {

	private static final int CODE_BOUND = 1_000_000;
	private static final String CODE_FORMAT = "%06d";
	private static final Duration TTL = Duration.ofSeconds(120);
	private static final String OTP_CLAIM = "otp";

	private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
	private final JwtUtils jwtUtils;

	public OtpService(JwtUtils jwtUtils) {
		this.jwtUtils = jwtUtils;
	}

	public OtpRequestResponse request(String userId) {
		String code = String.format(CODE_FORMAT, ThreadLocalRandom.current().nextInt(CODE_BOUND));
		String reference = IdUtils.newId();
		challenges.put(reference, new Challenge(userId, code, Instant.now().plus(TTL)));
		// No SMS gateway in the demo: log the code so it can be read from the service log.
		log.info("OTP issued for user {} (ref {}): {}", userId, reference, code);
		return new OtpRequestResponse(reference, TTL.toSeconds());
	}

	public OtpVerifyResponse verify(String userId, OtpVerifyRequest request) {
		Challenge challenge = challenges.get(request.otpRef());
		if (challenge == null || !challenge.userId().equals(userId) || Instant.now().isAfter(challenge.expiresAt())) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED, "OTP không hợp lệ hoặc đã hết hạn");
		}
		if (!MessageDigest.isEqual(bytes(challenge.code()), bytes(request.code()))) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Mã OTP không đúng");
		}
		challenges.remove(request.otpRef());
		return new OtpVerifyResponse(jwtUtils.generate(userId, Map.of(OTP_CLAIM, true), TTL), true);
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private record Challenge(String userId, String code, Instant expiresAt) {
	}
}
