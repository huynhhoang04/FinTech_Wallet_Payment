# HMAC Request Signing - Full Spec

Reference for the `security` skill. Implements `docs/04-api-contract.md` section 0.3.

## 1. Inputs

| Input | Source |
|---|---|
| `METHOD` | Uppercase HTTP method, e.g. `POST` |
| `PATH` | Path only, no host, no query, e.g. `/api/v1/transactions/transfer` |
| `CANONICAL_QUERY` | Query string, keys sorted ascending, `key=value` joined by `&` |
| `TIMESTAMP` | `X-Timestamp`, unix seconds, as a decimal string |
| `rawBody` | Exactly the bytes received; `""` when there is no body |

`SHA256_HEX(rawBody)` is the lowercase hex digest of the raw body (empty string -> digest of
the empty input, **not** an empty string).

## 2. Canonical query construction

Given `?b=2&a=1&a=3`:

```
a=1&a=3&b=2
```

Rules:
- Sort by key; for repeated keys preserve their relative order and repeat the key.
- Percent-encode values using the same encoder on both sides (RFC 3986 unreserved set).
- If there is no query string the result is the empty string, and the separator `\n` is still
  emitted (so the `stringToSign` always has exactly 5 lines).

## 3. stringToSign

```
stringToSign = METHOD + "\n" + PATH + "\n" + CANONICAL_QUERY + "\n" + TIMESTAMP + "\n" + SHA256_HEX(rawBody)
```

Signature:

```
X-Signature = lowercase_hex( HMAC_SHA256(key = clientSecret, message = stringToSign) )
```

## 4. Java (server, in `common/hmac`)

```java
public final class HmacUtils {

    public static String sign(String secret, String stringToSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC init failed", e);
        }
    }

    /** Constant-time comparison - never use String.equals for signatures. */
    public static boolean verify(String secret, String stringToSign, String providedHex) {
        byte[] expected = HexFormat.of().parseHex(sign(secret, stringToSign));
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(providedHex);
        } catch (IllegalArgumentException e) {
            return false;                       // malformed hex is simply invalid
        }
        return MessageDigest.isEqual(expected, provided);
    }
}
```

```java
public final class CanonicalRequest {
    public static String stringToSign(String method, String path, String canonicalQuery,
                                      String timestamp, byte[] rawBody) {
        return String.join("\n", method.toUpperCase(), path, canonicalQuery, timestamp,
                           sha256Hex(rawBody));
    }
}
```

## 5. TypeScript (client, `app/src/api/hmac.ts`)

```ts
import CryptoJS from 'crypto-js';

export function stringToSign(method: string, path: string, query: string, ts: string, body: string) {
  const bodyHash = CryptoJS.SHA256(body).toString(CryptoJS.enc.Hex);
  return [method.toUpperCase(), path, query, ts, bodyHash].join('\n');
}

export function sign(secret: string, payload: string) {
  return CryptoJS.HmacSHA256(payload, secret).toString(CryptoJS.enc.Hex);
}
```

The client must sign the **exact** body string it sends. Serialize once, keep the string, send
that string - do not re-serialize for the request.

## 6. curl (manual verification / demo)

```bash
BODY='{"sourceAccountId":"a1","targetAccountNumber":"9704002","amount":100000,"currency":"VND"}'
TS=$(date +%s)
BODY_HASH=$(printf '%s' "$BODY" | sha256sum | cut -d' ' -f1)
STS=$(printf '%s\n%s\n%s\n%s\n%s' "POST" "/api/v1/transactions/transfer" "" "$TS" "$BODY_HASH")
SIG=$(printf '%s' "$STS" | openssl dgst -sha256 -hmac "$CLIENT_SECRET" | awk '{print $2}')

curl -X POST https://api.<domain>/api/v1/transactions/transfer \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: $TS" \
  -H "X-Signature: $SIG" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -d "$BODY"
```

Debugging tip: print `STS` with `printf '%q'` (or `cat -A`) when a signature unexpectedly
fails - invisible whitespace and CRLF are the usual cause.

## 7. Replay protection

- Reject when `|now - X-Timestamp| > 300` seconds -> `400 INVALID_SIGNATURE`.
- Optional extra: Redis `SETNX sig:<signature> 1 EX 300` to make a signature single-use.
- The timestamp window plus the idempotency key is the minimum bar for this project.

## 8. Common failure modes

| Symptom | Cause |
|---|---|
| Signature valid locally, `400` on server | Body re-serialized after hashing (key order / whitespace differs) |
| Always invalid | `PATH` includes the query string, or the host |
| Intermittent invalid | Client and server disagree on the timestamp unit (ms vs s) |
| Invalid after a proxy | Nginx or the gateway rewrote/normalized the body before hashing |
| Invalid on empty body | Hashing `""` as a string instead of the empty byte array - the digest is still required |
| Trailing `\n` mismatch | `CANONICAL_QUERY` omitted instead of emitted as an empty line |

## 9. Test vectors

Keep a small fixture in the gateway module so client and server cannot drift:

```
secret        : test-secret
method        : POST
path          : /api/v1/transactions/transfer
canonicalQuery: (empty)
timestamp     : 1759032000
body          : {"amount":100000}
bodySha256    : <compute once, freeze>
stringToSign  : <compute once, freeze>
signature     : <compute once, freeze>
```

Assert `HmacUtils.verify(...)` returns `true` for the frozen signature and `false` after
mutating one character of the body.
