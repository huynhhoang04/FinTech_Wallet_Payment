package com.fintech.wallet.txengine.config;

import com.fintech.wallet.common.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {

	/** Also used to verify the short lived OTP token minted by the ledger (docs 04 section 2.5). */
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
}
