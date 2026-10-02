package com.fintech.wallet.ledger.controller;

import com.fintech.wallet.common.security.CurrentUser;
import com.fintech.wallet.ledger.dto.LoginRequest;
import com.fintech.wallet.ledger.dto.LoginResponse;
import com.fintech.wallet.ledger.dto.RefreshRequest;
import com.fintech.wallet.ledger.dto.RegisterRequest;
import com.fintech.wallet.ledger.dto.RegisterResponse;
import com.fintech.wallet.ledger.dto.UserProfileResponse;
import com.fintech.wallet.ledger.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request);
	}

	@GetMapping("/me")
	public UserProfileResponse me() {
		return authService.profile(CurrentUser.userId());
	}
}
