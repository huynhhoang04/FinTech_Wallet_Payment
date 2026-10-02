package com.fintech.wallet.txengine.config;

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

	private static final String[] PUBLIC_PATHS = { "/actuator/**", "/internal/**" };

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
