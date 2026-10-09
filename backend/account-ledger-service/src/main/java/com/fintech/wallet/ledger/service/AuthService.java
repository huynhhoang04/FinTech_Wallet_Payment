package com.fintech.wallet.ledger.service;

import com.fintech.wallet.common.constant.Roles;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.security.JwtUtils;
import com.fintech.wallet.ledger.domain.Account;
import com.fintech.wallet.ledger.domain.AccountStatus;
import com.fintech.wallet.ledger.domain.AccountType;
import com.fintech.wallet.ledger.domain.User;
import com.fintech.wallet.ledger.dto.LoginRequest;
import com.fintech.wallet.ledger.dto.LoginResponse;
import com.fintech.wallet.ledger.dto.RefreshRequest;
import com.fintech.wallet.ledger.dto.RegisterRequest;
import com.fintech.wallet.ledger.dto.RegisterResponse;
import com.fintech.wallet.ledger.dto.UserProfileResponse;
import com.fintech.wallet.ledger.repository.AccountRepository;
import com.fintech.wallet.ledger.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

	private static final String TOKEN_TYPE_CLAIM = "type";
	private static final String REFRESH_TOKEN_TYPE = "refresh";
	private static final String CURRENCY_VND = "VND";
	private static final String ACCOUNT_NUMBER_PREFIX = "9704";
	private static final int ACCOUNT_NUMBER_ATTEMPTS = 5;
	private static final String INVALID_CREDENTIALS = "Sai số điện thoại hoặc mật khẩu";

	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtils jwtUtils;
	private final Duration accessTokenTtl;
	private final Duration refreshTokenTtl;

	/**
	 * Admin identity is configuration, not a column: docs 03 gives the users table no role, so the
	 * audit and reconciliation endpoints need an allowlist until one exists (docs 07 SEC-03).
	 */
	private final Set<String> adminPhones;

	public AuthService(UserRepository userRepository,
			AccountRepository accountRepository,
			PasswordEncoder passwordEncoder,
			JwtUtils jwtUtils,
			@Value("${app.security.access-token-ttl}") Duration accessTokenTtl,
			@Value("${app.security.refresh-token-ttl}") Duration refreshTokenTtl,
			@Value("${app.security.admin-phones:}") String adminPhones) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtUtils = jwtUtils;
		this.accessTokenTtl = accessTokenTtl;
		this.refreshTokenTtl = refreshTokenTtl;
		this.adminPhones = Arrays.stream(adminPhones.split(","))
				.map(String::trim)
				.filter(phone -> !phone.isBlank())
				.collect(Collectors.toSet());
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		if (userRepository.existsByPhone(request.phone())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số điện thoại đã được đăng ký");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Email đã được đăng ký");
		}
		User user = new User();
		user.setPhone(request.phone());
		user.setEmail(request.email());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setFullName(request.fullName());
		userRepository.save(user);

		Account account = new Account();
		account.setOwnerUserId(user.getId());
		account.setType(AccountType.USER);
		account.setCurrency(CURRENCY_VND);
		account.setStatus(AccountStatus.ACTIVE);
		account.setAccountNumber(nextAccountNumber());
		accountRepository.save(account);

		return new RegisterResponse(user.getId().toString(), account.getId().toString(),
				account.getAccountNumber(), account.getStatus().name());
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByPhone(request.phone())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, INVALID_CREDENTIALS));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED, INVALID_CREDENTIALS);
		}
		return issueTokens(user);
	}

	@Transactional(readOnly = true)
	public LoginResponse refresh(RefreshRequest request) {
		Claims claims = parseToken(request.refreshToken());
		if (!REFRESH_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Token không phải refresh token");
		}
		User user = userRepository.findById(toUserId(claims.getSubject()))
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "Không tìm thấy người dùng"));
		return issueTokens(user);
	}

	@Transactional(readOnly = true)
	public UserProfileResponse profile(String userId) {
		User user = userRepository.findById(toUserId(userId))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
		return new UserProfileResponse(user.getId().toString(), user.getPhone(), user.getEmail(),
				user.getFullName(), user.getKycStatus().name());
	}

	private LoginResponse issueTokens(User user) {
		String subject = user.getId().toString();
		String role = adminPhones.contains(user.getPhone()) ? Roles.ADMIN : Roles.USER;
		String accessToken = jwtUtils.generate(subject, Map.of("role", role), accessTokenTtl);
		String refreshToken = jwtUtils.generate(subject,
				Map.of("role", role, TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE), refreshTokenTtl);
		return LoginResponse.of(accessToken, refreshToken, accessTokenTtl.toSeconds());
	}

	private Claims parseToken(String token) {
		try {
			return jwtUtils.parse(token).getPayload();
		} catch (ExpiredJwtException e) {
			throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
		} catch (JwtException | IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
	}

	private static UUID toUserId(String subject) {
		try {
			return UUID.fromString(subject);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Token không hợp lệ");
		}
	}

	private String nextAccountNumber() {
		for (int attempt = 0; attempt < ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
			String candidate = ACCOUNT_NUMBER_PREFIX
					+ String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
			if (!accountRepository.existsByAccountNumber(candidate)) {
				return candidate;
			}
		}
		throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không sinh được số tài khoản, thử lại sau");
	}
}
