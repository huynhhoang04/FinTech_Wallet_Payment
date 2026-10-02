package com.fintech.wallet.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

	private static final String SECRET = Base64.getEncoder()
			.encodeToString("fintech-wallet-demo-jwt-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));

	@Test
	void generatesAndParsesToken() {
		JwtUtils jwt = new JwtUtils(SECRET);

		String token = jwt.generate("user-1", Map.of("role", "USER"), Duration.ofMinutes(15));
		Jws<Claims> parsed = jwt.parse(token);

		assertThat(parsed.getPayload().getSubject()).isEqualTo("user-1");
		assertThat(parsed.getPayload().get("role")).isEqualTo("USER");
	}

	@Test
	void rejectsTokenSignedWithAnotherSecret() {
		String otherSecret = Base64.getEncoder()
				.encodeToString("another-different-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));
		String token = new JwtUtils(SECRET).generate("user-1", Map.of(), Duration.ofMinutes(15));

		assertThatThrownBy(() -> new JwtUtils(otherSecret).parse(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsSecretShorterThan256Bits() {
		String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> new JwtUtils(shortSecret)).isInstanceOf(WeakKeyException.class);
	}

	@Test
	void alwaysSignsWithHs256EvenWithASecretLongEnoughForStrongerAlgorithms() {
		// 49 bytes would make jjwt pick HS384 on its own, while NimbusJwtDecoder defaults to HS256.
		String longSecret = Base64.getEncoder()
				.encodeToString("fintech-wallet-dev-only-jwt-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));

		String token = new JwtUtils(longSecret).generate("user-1", Map.of(), Duration.ofMinutes(5));

		assertThat(Jwts.parser().verifyWith(JwtUtils.secretKey(longSecret)).build()
				.parseSignedClaims(token).getHeader().getAlgorithm()).isEqualTo("HS256");
	}
}
