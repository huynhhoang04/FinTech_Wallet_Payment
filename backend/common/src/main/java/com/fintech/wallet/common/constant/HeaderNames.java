package com.fintech.wallet.common.constant;

public final class HeaderNames {

	public static final String AUTHORIZATION = "Authorization";
	public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";
	public static final String TIMESTAMP = "X-Timestamp";
	public static final String SIGNATURE = "X-Signature";
	public static final String REQUEST_ID = "X-Request-Id";
	public static final String IDEMPOTENT_REPLAY = "Idempotent-Replay";
	public static final String PROVIDER_SIGNATURE = "X-Provider-Signature";

	private HeaderNames() {
	}
}
