package com.fintech.wallet.audit.config;

import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.error.SecurityErrorWriter;
import com.fintech.wallet.common.security.RoleClaimAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Audit data and the reconciliation trigger are admin only - they were reachable by anyone who
 * could open the port (docs 07 SEC-03). {@code /internal/**} is left to the security chain because
 * {@code InternalTokenFilter} from {@code common} guards it before this chain runs.
 *
 * <p>The {@code role} claim is mapped by {@link RoleClaimAuthenticationConverter}; Spring's default
 * mapper reads {@code scope}/{@code scp}, which our tokens do not carry, so without it every caller
 * would be authenticated with no authority and every admin rule would answer 403.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] OPEN_PATHS = { "/actuator/**", "/internal/**", "/error" };
	private static final String[] ADMIN_PATHS = { "/api/v1/audit/**", "/api/v1/reconciliation/**" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errorWriter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(OPEN_PATHS).permitAll()
						.requestMatchers(ADMIN_PATHS).hasRole("ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint((request, response, exception) -> {
							response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
							errorWriter.write(request, response, ErrorCode.UNAUTHENTICATED,
									ErrorCode.UNAUTHENTICATED.defaultMessage());
						})
						.jwt(jwt -> jwt.jwtAuthenticationConverter(new RoleClaimAuthenticationConverter())))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint((request, response, exception) -> errorWriter.write(request, response,
								ErrorCode.UNAUTHENTICATED, ErrorCode.UNAUTHENTICATED.defaultMessage()))
						.accessDeniedHandler((request, response, denied) -> errorWriter.write(request, response,
								ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage())))
				.build();
	}
}
