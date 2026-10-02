package com.fintech.wallet.gateway.config;

import com.fintech.wallet.common.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * The gateway is reactive, so {@code oauth2ResourceServer(jwt)} needs a ReactiveJwtDecoder; the
 * imperative JwtDecoder interface used by the servlet services is not injected here.
 */
@Configuration
public class JwtConfig {

	@Bean
	ReactiveJwtDecoder reactiveJwtDecoder(@Value("${app.security.jwt-secret}") String base64Secret) {
		return NimbusReactiveJwtDecoder.withSecretKey(JwtUtils.secretKey(base64Secret))
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}
}
