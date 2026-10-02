package com.fintech.wallet.common.hmac;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HmacUtilsTest {

	@Test
	void signatureMatchesReferenceVector() {
		// printf 'abc' | openssl dgst -sha256 -hmac 'secret'
		assertThat(HmacUtils.sign("secret", "abc"))
				.isEqualTo("9946dad4e00e913fc8be8e5d3f7e110a4a9e832f83fb09c345285d78638d8a0e");
	}

	@Test
	void verifyAcceptsValidSignatureAndRejectsTamperedOne() {
		String signature = HmacUtils.sign("secret", "abc");

		assertThat(HmacUtils.verify("secret", "abc", signature)).isTrue();
		assertThat(HmacUtils.verify("secret", "abc", "deadbeef")).isFalse();
		assertThat(HmacUtils.verify("secret", "abc", null)).isFalse();
	}

	@Test
	void canonicalRequestMatchesDocumentedSigningFormat() {
		CanonicalRequest request = CanonicalRequest.of("POST", "/api/v1/transactions/transfer",
				Map.of("b", "2", "a", "1"), 1700000000L, "{\"amount\":100000}".getBytes(StandardCharsets.UTF_8));

		assertThat(request.toSigningString())
				.isEqualTo("POST\n/api/v1/transactions/transfer\na=1&b=2\n1700000000\n"
						+ HmacUtils.sha256Hex("{\"amount\":100000}"));
	}

	@Test
	void canonicalRequestHashesEmptyBodyWhenAbsent() {
		CanonicalRequest request = CanonicalRequest.of("GET", "/api/v1/accounts", null, 1700000000L, null);

		assertThat(request.canonicalQuery()).isEmpty();
		assertThat(request.bodyHash()).isEqualTo(HmacUtils.sha256Hex(new byte[0]));
	}
}
