package com.fintech.wallet.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.constant.HeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorWriterTest {

	private final SecurityErrorWriter writer = new SecurityErrorWriter(new ObjectMapper().findAndRegisterModules());

	@Test
	void rendersDocumentedApiErrorShapeForUnauthenticated() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/otp/request");
		request.addHeader(HeaderNames.REQUEST_ID, "req-1");
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(request, response, ErrorCode.UNAUTHENTICATED, "Full authentication is required");

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/json").contains("UTF-8");
		assertThat(response.getContentAsString())
				.contains("\"code\":\"UNAUTHENTICATED\"")
				.contains("\"status\":401")
				.contains("\"path\":\"/api/v1/auth/otp/request\"")
				.contains("\"requestId\":\"req-1\"")
				.contains("\"timestamp\"");
	}

	@Test
	void rendersForbidden() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(new MockHttpServletRequest("GET", "/internal/ledger/verify-chain"), response,
				ErrorCode.FORBIDDEN, "Access Denied");

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
	}
}
