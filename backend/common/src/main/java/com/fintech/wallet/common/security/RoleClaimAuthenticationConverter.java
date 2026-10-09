package com.fintech.wallet.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Servlet services that need role based rules (audit wants ROLE_ADMIN) map the {@code role} claim
 * the same way the gateway does. Spring's default mapper reads {@code scope}/{@code scp}, which our
 * tokens do not carry, so without this every token would be authenticated but have no authority.
 */
public class RoleClaimAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private static final String ROLE_CLAIM = "role";
	private static final String ROLE_PREFIX = "ROLE_";

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		String role = jwt.getClaimAsString(ROLE_CLAIM);
		List<GrantedAuthority> authorities = role == null || role.isBlank()
				? List.of()
				: List.of(new SimpleGrantedAuthority(role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role));
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}
}
