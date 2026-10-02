package com.fintech.wallet.txengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.common.hmac.HmacUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency keys live in Redis with a 24h TTL (docs 03 section 3.3). The value holds the payload
 * hash and, once the work is done, the serialised response.
 *
 * <ul>
 *   <li>key absent -&gt; reserved by this request, proceed
 *   <li>key present, same payload, response stored -&gt; replay it
 *   <li>key present, different payload -&gt; IDEMPOTENCY_CONFLICT
 *   <li>key present, response not stored yet -&gt; the first request is still running
 * </ul>
 */
@Service
public class IdempotencyService {

	private static final Duration TTL = Duration.ofHours(24);
	private static final String KEY_PREFIX = "idem:";

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	public IdempotencyService(StringRedisTemplate redis, ObjectMapper objectMapper) {
		this.redis = redis;
		this.objectMapper = objectMapper;
	}

	public static String payloadHash(String payload) {
		return HmacUtils.sha256Hex(payload);
	}

	/** Empty when the key was free and is now owned by this request. */
	public Optional<StoredEntry> reserve(String key, String payloadHash) {
		boolean acquired = Boolean.TRUE.equals(
				redis.opsForValue().setIfAbsent(redisKey(key), write(new StoredEntry(payloadHash, null)), TTL));
		if (acquired) {
			return Optional.empty();
		}
		return Optional.of(read(redis.opsForValue().get(redisKey(key))));
	}

	public void complete(String key, String payloadHash, String responseJson) {
		redis.opsForValue().set(redisKey(key), write(new StoredEntry(payloadHash, responseJson)), TTL);
	}

	/** Frees the key after a transient failure so the client can retry it without waiting for the TTL. */
	public void release(String key) {
		redis.delete(redisKey(key));
	}

	private String redisKey(String key) {
		return KEY_PREFIX + key;
	}

	private String write(StoredEntry entry) {
		try {
			return objectMapper.writeValueAsString(entry);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot serialise idempotency entry", e);
		}
	}

	private StoredEntry read(String value) {
		try {
			return objectMapper.readValue(value, StoredEntry.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Cannot read idempotency entry", e);
		}
	}

	public record StoredEntry(String payloadHash, String response) {

		public boolean inFlight() {
			return response == null;
		}
	}
}
