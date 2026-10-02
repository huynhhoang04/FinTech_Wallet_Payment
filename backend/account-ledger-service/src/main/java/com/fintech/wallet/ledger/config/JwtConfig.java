package com.fintech.wallet.ledger.config;

import com.fintech.wallet.common.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {

	@Bean
	JwtUtils jwtUtils(@Value("${app.security.jwt-secret}") String base64Secret) {
		return new JwtUtils(base64Secret);
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String base64Secret) {
		return NimbusJwtDecoder.withSecretKey(JwtUtils.secretKey(base64Secret))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
