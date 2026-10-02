package com.fintech.wallet.common.hmac;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HmacUtils {

	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final String SHA_256 = "SHA-256";

	private HmacUtils() {
	}

	public static String sha256Hex(byte[] data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance(SHA_256).digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String sha256Hex(String data) {
		return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
	}

	public static String sign(String secret, String stringToSign) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static boolean verify(String secret, String stringToSign, String signatureHex) {
		if (signatureHex == null) {
			return false;
		}
		byte[] expected = sign(secret, stringToSign).getBytes(StandardCharsets.UTF_8);
		byte[] actual = signatureHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expected, actual);
	}
}
