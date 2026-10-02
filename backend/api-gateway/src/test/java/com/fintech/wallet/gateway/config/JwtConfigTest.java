package com.fintech.wallet.gateway.config;

import com.fintech.wallet.common.constant.Roles;
import com.fintech.wallet.common.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

	private static final String SECRET = Base64.getEncoder()
			.encodeToString("fintech-wallet-test-jwt-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));

	private final JwtDecoder decoder = new JwtConfig().jwtDecoder(SECRET);
	private final JwtAuthoritiesConverter authoritiesConverter = new JwtAuthoritiesConverter();

	@Test
	void decodesTokenIssuedByCommonJwtUtils() {
		String token = new JwtUtils(SECRET).generate("user-1", Map.of("role", Roles.USER), Duration.ofMinutes(5));

		Jwt jwt = decoder.decode(token);

		assertThat(jwt.getSubject()).isEqualTo("user-1");
		assertThat(jwt.getClaimAsString("role")).isEqualTo(Roles.USER);
	}

	@Test
	void rejectsTokenSignedWithAnotherSecret() {
		String otherSecret = Base64.getEncoder()
				.encodeToString("another-different-test-secret-key-0123456789".getBytes(StandardCharsets.UTF_8));
		String token = new JwtUtils(otherSecret).generate("user-1", Map.of(), Duration.ofMinutes(5));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void mapsRoleClaimToGrantedAuthority() {
		assertThat(convert("ROLE_USER")).containsExactly("ROLE_USER");
		assertThat(convert("ADMIN")).containsExactly("ROLE_ADMIN");
		assertThat(convert(null)).isEmpty();
	}

	private List<String> convert(String role) {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject("user-1")
				.claim("role", role)
				.build();
		Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
		return authorities.stream().map(GrantedAuthority::getAuthority).toList();
	}
}
