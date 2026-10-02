package com.fintech.wallet.ledger.service;

import com.fintech.wallet.common.constant.Roles;
import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.security.JwtUtils;
import com.fintech.wallet.ledger.domain.Account;
import com.fintech.wallet.ledger.domain.KycStatus;
import com.fintech.wallet.ledger.domain.User;
import com.fintech.wallet.ledger.dto.LoginRequest;
import com.fintech.wallet.ledger.dto.LoginResponse;
import com.fintech.wallet.ledger.dto.RefreshRequest;
import com.fintech.wallet.ledger.dto.RegisterRequest;
import com.fintech.wallet.ledger.dto.RegisterResponse;
import com.fintech.wallet.ledger.repository.AccountRepository;
import com.fintech.wallet.ledger.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

	private static final String SECRET = Base64.getEncoder()
			.encodeToString("ledger-test-jwt-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));
	private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
	private static final Duration REFRESH_TTL = Duration.ofDays(7);

	private final UserRepository userRepository = mock(UserRepository.class);
	private final AccountRepository accountRepository = mock(AccountRepository.class);
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final JwtUtils jwtUtils = new JwtUtils(SECRET);
	private final AuthService authService = new AuthService(userRepository, accountRepository, passwordEncoder,
			jwtUtils, ACCESS_TTL, REFRESH_TTL);

	@Test
	void registerCreatesUserWithHashedPasswordAndWalletAccount() {
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
			return user;
		});
		when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
			Account account = invocation.getArgument(0);
			ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
			return account;
		});

		RegisterResponse response = authService.register(
				new RegisterRequest("0912345678", "a@b.com", "S3cret!23", "Nguyen Van A"));

		assertThat(response.userId()).isNotBlank();
		assertThat(response.walletAccountId()).isNotBlank();
		assertThat(response.accountNumber()).startsWith("9704").hasSize(13);
		assertThat(response.status()).isEqualTo("ACTIVE");

		ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(savedUser.capture());
		assertThat(savedUser.getValue().getPasswordHash()).isNotEqualTo("S3cret!23");
		assertThat(passwordEncoder.matches("S3cret!23", savedUser.getValue().getPasswordHash())).isTrue();
		assertThat(savedUser.getValue().getKycStatus()).isEqualTo(KycStatus.PENDING);
	}

	@Test
	void registerRejectsDuplicatePhone() {
		when(userRepository.existsByPhone("0912345678")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(
				new RegisterRequest("0912345678", "a@b.com", "S3cret!23", "Nguyen Van A")))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void loginReturnsTokensCarryingUserIdAndRole() {
		User user = userWithId();
		user.setPhone("0912345678");
		user.setPasswordHash(passwordEncoder.encode("S3cret!23"));
		when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(user));

		LoginResponse response = authService.login(new LoginRequest("0912345678", "S3cret!23"));

		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(ACCESS_TTL.toSeconds());
		Claims claims = jwtUtils.parse(response.accessToken()).getPayload();
		assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
		assertThat(claims.get("role")).isEqualTo(Roles.USER);
	}

	@Test
	void loginRejectsWrongPassword() {
		User user = userWithId();
		user.setPasswordHash(passwordEncoder.encode("S3cret!23"));
		when(userRepository.findByPhone(anyString())).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> authService.login(new LoginRequest("0912345678", "wrong")))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

	@Test
	void refreshIssuesNewTokensForRefreshToken() {
		User user = userWithId();
		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		String refreshToken = jwtUtils.generate(user.getId().toString(),
				Map.of("role", Roles.USER, "type", "refresh"), REFRESH_TTL);

		LoginResponse response = authService.refresh(new RefreshRequest(refreshToken));

		assertThat(response.accessToken()).isNotBlank();
		assertThat(jwtUtils.parse(response.accessToken()).getPayload().getSubject())
				.isEqualTo(user.getId().toString());
	}

	@Test
	void refreshRejectsAccessToken() {
		String accessToken = jwtUtils.generate(UUID.randomUUID().toString(), Map.of("role", Roles.USER), ACCESS_TTL);

		assertThatThrownBy(() -> authService.refresh(new RefreshRequest(accessToken)))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
	}

	@Test
	void refreshRejectsExpiredTokenWithTokenExpiredCode() {
		String expiredRefreshToken = jwtUtils.generate(UUID.randomUUID().toString(),
				Map.of("role", Roles.USER, "type", "refresh"), Duration.ofSeconds(-1));

		assertThatThrownBy(() -> authService.refresh(new RefreshRequest(expiredRefreshToken)))
				.isInstanceOfSatisfying(BusinessException.class,
						ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
	}

	private static User userWithId() {
		User user = new User();
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		return user;
	}
}
