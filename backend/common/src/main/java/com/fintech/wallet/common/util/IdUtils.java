package com.fintech.wallet.common.util;

import java.util.UUID;

public final class IdUtils {

	private IdUtils() {
	}

	public static String newId() {
		return UUID.randomUUID().toString();
	}

	public static String newIdempotencyKey() {
		return UUID.randomUUID().toString();
	}

	public static String newRequestId() {
		return UUID.randomUUID().toString();
	}
}
