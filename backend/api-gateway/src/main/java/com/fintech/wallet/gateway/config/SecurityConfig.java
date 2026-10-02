package com.fintech.wallet.gateway.config;

import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.gateway.error.ApiErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_PATHS = { "/actuator/**", "/webhooks/**", "/api/v1/auth/**" };

	@Bean
	SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
			ApiErrorWriter errorWriter,
			JwtAuthoritiesConverter authoritiesConverter) {
		return http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.authorizeExchange(exchange -> exchange
						.pathMatchers(PUBLIC_PATHS).permitAll()
						.anyExchange().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
						.jwtAuthenticationConverter(reactiveConverter(authoritiesConverter))))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint((exchange, exception) -> errorWriter.write(exchange,
								HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, exception.getMessage()))
						.accessDeniedHandler((exchange, denied) -> errorWriter.write(exchange,
								HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, denied.getMessage())))
				.build();
	}

	private static Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveConverter(
			JwtAuthoritiesConverter authoritiesConverter) {
		JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
		delegate.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return new ReactiveJwtAuthenticationConverterAdapter(delegate);
	}
}
