package com.fintech.wallet.common.web;

import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.util.IdUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Puts the request id into the logging MDC so a request can be traced across services (the gateway
 * issues it, each servlet service echoes it back in the response and into its logs). Only servlet
 * services scan this package; the reactive gateway has its own GlobalFilter.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String MDC_KEY = "requestId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String requestId = resolve(request);
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HeaderNames.REQUEST_ID, requestId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	private static String resolve(HttpServletRequest request) {
		String header = request.getHeader(HeaderNames.REQUEST_ID);
		return header == null || header.isBlank() ? IdUtils.newRequestId() : header;
	}
}
