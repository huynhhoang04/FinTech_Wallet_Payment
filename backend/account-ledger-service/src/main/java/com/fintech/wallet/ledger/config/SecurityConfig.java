package com.fintech.wallet.ledger.config;

import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.error.SecurityErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/**
	 * {@code /internal/**} is permitted here on purpose: {@code InternalTokenFilter} from
	 * {@code common} runs before this chain and refuses a call without the internal token, which is
	 * the gate docs 07 SEC-01 asks for. Requiring a user JWT instead would leave service-to-service
	 * callers with no way in.
	 */
	private static final String[] PUBLIC_PATHS = {
			"/api/v1/auth/register",
			"/api/v1/auth/login",
			"/api/v1/auth/refresh",
			"/internal/**",
			"/actuator/**",
			"/error"
	};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errorWriter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_PATHS).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint((request, response, exception) -> {
							response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
							errorWriter.write(request, response, ErrorCode.UNAUTHENTICATED,
									ErrorCode.UNAUTHENTICATED.defaultMessage());
						})
						.jwt(Customizer.withDefaults()))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint((request, response, exception) -> errorWriter.write(request, response,
								ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.defaultMessage()))
						.accessDeniedHandler((request, response, denied) -> errorWriter.write(request, response,
								ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage())))
				.build();
	}
}
