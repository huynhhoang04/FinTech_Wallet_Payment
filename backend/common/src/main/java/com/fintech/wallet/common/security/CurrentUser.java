package com.fintech.wallet.common.security;

import com.fintech.wallet.common.error.BusinessException;
import com.fintech.wallet.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public final class CurrentUser {

	private CurrentUser() {
	}

	public static String userId() {
		return authentication().getName();
	}

	public static List<String> roles() {
		return authentication().getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.toList();
	}

	public static boolean hasRole(String role) {
		return roles().contains(role);
	}

	private static Authentication authentication() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		return authentication;
	}
}
