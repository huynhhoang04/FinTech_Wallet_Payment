package com.fintech.wallet.common.web;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.error.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * {@code /internal/**} is not public: it exposes balance and ledger verification, and in
 * payment-gateway it used to include an endpoint that mints money. Every internal call must carry
 * {@code X-Internal-Token}, compared in constant time.
 *
 * <p>The token has no default: a service that does not configure {@code app.internal.token} refuses
 * to start rather than run with the endpoints open (docs 07 SEC-01).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalTokenFilter extends OncePerRequestFilter {

	private static final String INTERNAL_PREFIX = "/internal/";

	private final byte[] expectedToken;
	private final SecurityErrorWriter errorWriter;

	public InternalTokenFilter(@Value("${app.internal.token:}") String token, SecurityErrorWriter errorWriter) {
		if (token == null || token.isBlank()) {
			throw new IllegalStateException(
					"app.internal.token must be set: /internal/** endpoints are not public");
		}
		this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
		this.errorWriter = errorWriter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith(INTERNAL_PREFIX);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String presented = request.getHeader(HeaderNames.INTERNAL_TOKEN);
		if (presented == null || !MessageDigest.isEqual(expectedToken, presented.getBytes(StandardCharsets.UTF_8))) {
			errorWriter.write(request, response, ErrorCode.UNAUTHENTICATED, "Endpoint nội bộ yêu cầu "
					+ HeaderNames.INTERNAL_TOKEN);
			return;
		}
		chain.doFilter(request, response);
	}
}
