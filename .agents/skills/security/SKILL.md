---
name: security
description: Security rules for this FinTech wallet project - HMAC request signing, JWT authentication, idempotency, secret handling, SQL/injection safety, PII-safe logging, webhook verification, and container hardening. Use when writing or reviewing the API Gateway HMAC filter, auth code, crypto, secret/config handling, webhook callbacks, or any endpoint that moves money; also use when asked to security-review, threat-model, or harden a service.
license: Proprietary
metadata:
  project: fintech-wallet-payment
  version: "1.0"
  owner: devops-academy
---

# Security

Security rules for the FinTech Wallet & Payment Gateway system. This project moves real money
in its domain model, so "works on my machine" is not the bar - **no unauthorized write, no
double spend, no leaked secret**.

The authoritative API-level spec lives in `docs/04-api-contract.md` (sections 0.2-0.4 for
headers, signature, error codes).

## 1. Non-negotiables

1. Every money-moving request is **HMAC-SHA256 signed** and rejected at the Gateway before it
   reaches any service.
2. Every money-moving request carries **`X-Idempotency-Key`**; a replay must never move money twice.
3. **No secret in git.** Not in `application.yml`, not in tests, not in `docker-compose.yml`.
4. Signatures, tokens, OTP codes and API keys are compared with **`MessageDigest.isEqual`**
   (constant time). Never `String.equals`.
5. All SQL that touches money uses **parameter placeholders** (`?`). Never string concatenation.
6. Provider webhooks are **untrusted input**: verify the provider signature *before* acting on
   the payload.
7. Never trust a `userId`/`accountId` from the request body for authorization - take it from the
   verified JWT.

## 2. Where security lives in this repo

| Concern | Location |
|---|---|
| HMAC verification, rate limiting, JWT check | `backend/api-gateway/.../filter/HmacAuthFilter.java`, `SecurityConfig` |
| JWT issue/verify helpers | `backend/common/.../security/JwtUtils.java` |
| Canonical string + HMAC primitives | `backend/common/.../hmac/CanonicalRequest.java`, `HmacUtils.java` |
| Idempotency + distributed lock | `backend/transaction-engine/.../service/IdempotencyService.java` (Redis + Redisson) |
| Provider webhook signature | `backend/payment-gateway-service/.../webhook/*` |
| AuthN endpoints (register/login/refresh/OTP) | `account-ledger-service`, module `identity` |
| Client-side signing | `app/src/api/hmac.ts` |

## 3. HMAC request signing

`stringToSign` is built from the **raw** request, in this exact order:

```
METHOD \n PATH \n CANONICAL_QUERY \n TIMESTAMP \n SHA256_HEX(rawBody)
```

Rules:
- `CANONICAL_QUERY`: keys sorted ascending, joined `key=value&...`; empty string when no query.
- `X-Timestamp` is unix **seconds**; reject when `|now - ts| > 300`.
- Reject when `X-Signature` or `X-Timestamp` is missing, malformed, or mismatched.
- Recompute from the raw body **before** the framework deserializes it (cache the body).
- Response: `400 INVALID_SIGNATURE`. Log `requestId`, never the signature or secret.

Full spec, client/server examples and test vectors:
[references/hmac-request-signing.md](references/hmac-request-signing.md).

## 4. JWT

- Access token TTL **15 minutes**, refresh token TTL **7 days**. Refresh tokens are stored
  hashed, never in plaintext.
- Gateway validates signature, `exp`, `iss`, `aud`; downstream services **re-validate** for
  internal calls (defense in depth), they do not blindly trust gateway headers.
- Algorithm pinned (HS256 with a 32-byte random secret, or RS256). Never accept `alg: none`.
- Never put PII (phone, email, account number) in the token payload.
- Token secrets come from the environment: `${JWT_SECRET}`, rotated between environments.

## 5. Idempotency and replay safety

- Redis: `SET key value NX EX 86400`. Store the **serialized response**, so a replay returns the
  original result (`Idempotent-Replay: true`), not a re-execution.
- Same key + different payload -> `409 IDEMPOTENCY_CONFLICT`.
- Key still in flight -> `409 DUPLICATE_REQUEST`.
- Second line of defense: `transactions.idempotency_key UNIQUE`, `webhook_logs UNIQUE(provider, event_id)`.
- The client is responsible for generating a **UUID v4** key per logical operation - retries
  reuse it, new operations must not.

## 6. Secrets and configuration

- `.env` is never committed; only `.env.example` with placeholder values.
- Locally: `.env` next to `infra/docker/`. On the VPS: `.env` with `chmod 600`, owned by the
  deploy user.
- CI reads secrets from **GitHub Actions secrets** only.
- Production config is injected as environment variables (`${DB_PASSWORD}`), never baked into an
  image. Jib images must not contain `.env`.
- Scan on every pipeline run: `gitleaks detect` (repo) and `trivy image` (container).
- HMAC secrets are **per client** (never one global key); rotate on suspicion.

## 7. Input validation and injection

- Validate at the boundary with `jakarta.validation` annotations on DTOs (`@NotNull`, `@Positive`,
  `@Size`). Reject early with `400 VALIDATION_ERROR`.
- `amount`: positive integer, within the per-transaction and daily limits, currency whitelisted.
- Configure Jackson with `FAIL_ON_UNKNOWN_PROPERTIES = true` so unexpected fields are rejected.
- SQL: `JdbcTemplate` with `?` placeholders. Never `"... WHERE id = " + id`.
- Never pass user input into a shell, a URL, or a path without validation (SSRF/path traversal).

## 8. PII-safe logging

- Mask identifiers: `9704****001`, `0912***678`. Never log OTP, tokens, signatures, secrets, or
  full webhook payloads of auth flows.
- Log `requestId`/`traceId` on every line so an incident can be reconstructed without bodies.
- JSON logs (logback encoder) -> Loki; never `System.out.println` sensitive data.
- Do not echo the reason a signature failed in detail (`INVALID_SIGNATURE`, nothing more).

## 9. Transport and infrastructure

- TLS terminates at Nginx, HTTP/2 enabled, HSTS on. Plain HTTP redirects to HTTPS.
- Only two things are exposed publicly: the REST API and `/webhooks/**`. Services and gRPC are
  reachable only inside the Docker network.
- VPS: SSH key only (password login disabled), UFW allowing 22/80/443 only, fail2ban for SSH.
- Containers run as a non-root user (configure the Jib base image user).
- PostgreSQL/Redis/Kafka are never published on a public interface.

## 10. Review checklist

Run through this before declaring any security-relevant change done:

- [ ] Money endpoint requires HMAC + `X-Idempotency-Key`; replay returns the stored result.
- [ ] Signature comparison is constant-time.
- [ ] Authorization derives the user from the JWT, not the request body.
- [ ] New SQL is parameterized.
- [ ] No secret, token, or real PII added to the repo or logs.
- [ ] New env var documented in `.env.example`.
- [ ] `gitleaks` and `trivy` pass in CI.
- [ ] Error responses leak nothing beyond the `ErrorCode` in `docs/04-api-contract.md` (section 0.4).

## 11. Commands

```bash
# Secret scan (repo)
gitleaks detect --source . -v

# Image scan
trivy image ghcr.io/<org>/fintech-wallet-transaction-engine:1.0.0

# Dependency / CVE scan
cd backend && ./mvnw -q dependency-check:check

# Verify a signature by hand (debugging a 400 INVALID_SIGNATURE)
printf '%s' "$STS" | openssl dgst -sha256 -hmac "$CLIENT_SECRET"
```

## 12. References

- [references/hmac-request-signing.md](references/hmac-request-signing.md) - full signing spec,
  Java/JS/curl implementations, common failure modes.
- [references/owasp-api-checklist.md](references/owasp-api-checklist.md) - OWASP API Security
  Top 10 (2023) mapped to this codebase, as a review checklist.
- Project docs: `docs/04-api-contract.md` (sections 0.2-0.4), `docs/02-service-breakdown.md`
  (section 3, gateway), `docs/03-jpa-schema.md` (uniqueness constraints used as idempotency guards).
