package com.fintech.wallet.common.hmac;

import java.util.Map;
import java.util.TreeMap;

public record CanonicalRequest(String method, String path, String canonicalQuery, String timestamp, String bodyHash) {

	public String toSigningString() {
		return method + "\n" + path + "\n" + canonicalQuery + "\n" + timestamp + "\n" + bodyHash;
	}

	public static CanonicalRequest of(String method, String path, Map<String, String> query, long timestampSeconds,
			byte[] rawBody) {
		return new CanonicalRequest(method, path, canonicalQuery(query), Long.toString(timestampSeconds),
				HmacUtils.sha256Hex(rawBody == null ? new byte[0] : rawBody));
	}

	private static String canonicalQuery(Map<String, String> query) {
		if (query == null || query.isEmpty()) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		new TreeMap<>(query).forEach((key, value) -> {
			if (!builder.isEmpty()) {
				builder.append('&');
			}
			builder.append(key).append('=').append(value);
		});
		return builder.toString();
	}
}
