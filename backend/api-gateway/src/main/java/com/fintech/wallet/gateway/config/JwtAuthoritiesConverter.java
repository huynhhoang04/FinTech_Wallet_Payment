package com.fintech.wallet.gateway.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class JwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	private static final String ROLE_CLAIM = "role";
	private static final String ROLE_PREFIX = "ROLE_";

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		String role = jwt.getClaimAsString(ROLE_CLAIM);
		if (role == null || role.isBlank()) {
			return List.of();
		}
		String authority = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
		return List.of(new SimpleGrantedAuthority(authority));
	}
}
