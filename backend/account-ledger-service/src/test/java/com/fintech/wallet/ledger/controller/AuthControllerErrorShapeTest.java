package com.fintech.wallet.ledger.controller;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import com.fintech.wallet.common.error.GlobalExceptionHandler;
import com.fintech.wallet.ledger.dto.LoginRequest;
import com.fintech.wallet.ledger.dto.LoginResponse;
import com.fintech.wallet.ledger.service.AuthService;
import com.fintech.wallet.ledger.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerErrorShapeTest {

	private final AuthService authService = mock(AuthService.class);
	private final OtpService otpService = mock(OtpService.class);
	private final MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new AuthController(authService, otpService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void businessExceptionIsRenderedWithDocumentedApiErrorShape() throws Exception {
		when(authService.login(any(LoginRequest.class)))
				.thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED, "Sai số điện thoại hoặc mật khẩu"));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"0912345678\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.message").value("Sai số điện thoại hoặc mật khẩu"))
				.andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.details").doesNotExist());
	}

	@Test
	void loginResponseMatchesDocumentedContract() throws Exception {
		when(authService.login(any(LoginRequest.class)))
				.thenReturn(LoginResponse.of("access-token", "refresh-token", 900));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"0912345678\",\"password\":\"S3cret!23\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token"))
				.andExpect(jsonPath("$.refreshToken").value("refresh-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900));
	}

	@Test
	void otpRequestRequiresAnAuthenticatedUser() throws Exception {
		mockMvc.perform(post("/api/v1/auth/otp/request"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
				.andExpect(jsonPath("$.path").value("/api/v1/auth/otp/request"));
	}
}
