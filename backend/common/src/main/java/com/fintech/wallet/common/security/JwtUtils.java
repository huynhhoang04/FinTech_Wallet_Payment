package com.fintech.wallet.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class JwtUtils {

	private final SecretKey key;

	public JwtUtils(String base64Secret) {
		this.key = secretKey(base64Secret);
	}

	public static SecretKey secretKey(String base64Secret) {
		return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
	}

	public String generate(String subject, Map<String, Object> claims, Duration ttl) {
		Instant now = Instant.now();
		return Jwts.builder()
				.claims(claims)
				.subject(subject)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(ttl)))
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	public Jws<Claims> parse(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token);
	}
}
