package com.fintech.wallet.common.web;

import com.fintech.wallet.common.constant.HeaderNames;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

	private final RequestIdFilter filter = new RequestIdFilter();

	@Test
	void generatesRequestIdWhenHeaderIsMissingAndClearsMdcAfterwards() throws Exception {
		AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

		MockHttpServletResponse response = run(new MockHttpServletRequest(), mdcDuringRequest);

		assertThat(response.getHeader(HeaderNames.REQUEST_ID)).isNotBlank();
		assertThat(mdcDuringRequest.get()).isEqualTo(response.getHeader(HeaderNames.REQUEST_ID));
		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void keepsRequestIdIssuedByTheGateway() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(HeaderNames.REQUEST_ID, "gateway-issued-id");

		MockHttpServletResponse response = run(request, new AtomicReference<>());

		assertThat(response.getHeader(HeaderNames.REQUEST_ID)).isEqualTo("gateway-issued-id");
	}

	private MockHttpServletResponse run(MockHttpServletRequest request, AtomicReference<String> mdcDuringRequest)
			throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response,
				(servletRequest, servletResponse) -> mdcDuringRequest.set(MDC.get(RequestIdFilter.MDC_KEY)));
		return response;
	}
}
