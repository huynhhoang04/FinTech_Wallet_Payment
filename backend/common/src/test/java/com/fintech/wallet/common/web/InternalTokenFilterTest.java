package com.fintech.wallet.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.HeaderNames;
import com.fintech.wallet.common.error.SecurityErrorWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenFilterTest {

	private static final String TOKEN = "s3cret-internal";

	private final SecurityErrorWriter writer = new SecurityErrorWriter(new ObjectMapper().findAndRegisterModules());
	private final InternalTokenFilter filter = new InternalTokenFilter(TOKEN, writer);

	@Test
	void rejectsInternalCallWithoutToken() throws Exception {
		MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/internal/ledger/verify-chain"), null);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
	}

	@Test
	void rejectsInternalCallWithAWrongToken() throws Exception {
		MockHttpServletResponse response = run(new MockHttpServletRequest("GET", "/internal/ledger/verify-chain"),
				"not-the-token");

		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void acceptsInternalCallWithTheToken() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/ledger/verify-chain");
		request.addHeader(HeaderNames.INTERNAL_TOKEN, TOKEN);
		AtomicBoolean reached = new AtomicBoolean(false);

		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (servletRequest, servletResponse) -> reached.set(true));

		assertThat(reached).isTrue();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void leavesPublicPathsAlone() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
		AtomicBoolean reached = new AtomicBoolean(false);

		filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> reached.set(true));

		assertThat(reached).isTrue();
	}

	@Test
	void refusesToStartWhenNoTokenIsConfigured() {
		assertThatThrownBy(() -> new InternalTokenFilter("  ", writer))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("app.internal.token");
	}

	private MockHttpServletResponse run(MockHttpServletRequest request, String token) throws Exception {
		if (token != null) {
			request.addHeader(HeaderNames.INTERNAL_TOKEN, token);
		}
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
		});
		return response;
	}
}
