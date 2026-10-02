# OWASP API Security Top 10 (2023) - Project Checklist

Reference for the `security` skill. Each item states the risk, then how this specific codebase
addresses it, then the evidence to look for during review.

## API1:2023 - Broken Object Level Authorization (BOLA)

Risk: a user reads or moves money on an account that is not theirs by changing an `accountId`.

- Derive the caller from the verified JWT (`sub`), never from the request body.
- Every account lookup is scoped: `account.ownerUserId == jwt.sub` (or the caller is admin).
- Internal admin endpoints require an admin role claim and are not routed publicly.
- Evidence: gateway route table exposes no `/internal/**`; service tests cover "user B reads
  user A's account" -> `403 FORBIDDEN`.

## API2:2023 - Broken Authentication

Risk: weak tokens, no expiry, credential stuffing, OTP brute force.

- Access 15 min / refresh 7 days; refresh stored hashed.
- Algorithm pinned; `alg: none` rejected.
- OTP is rate limited and single-use with a short TTL.
- Login throttled by IP + phone (Redis counter).
- Evidence: `JwtUtils` validation includes `exp`/`iss`/`aud`; `429` after N failed logins.

## API3:2023 - Broken Object Property Level Authorization

Risk: mass assignment - client sends `status: "SUCCESS"` or `balance` and the server honors it.

- DTOs are explicit; entities are never bound directly from request bodies.
- `FAIL_ON_UNKNOWN_PROPERTIES = true` -> unexpected fields produce `400`.
- Response DTOs never expose internal fields (`version`, `failureReason` internals, hashes).
- Evidence: `TransferRequest` has no `status`/`fee` field the client can set.

## API4:2023 - Unrestricted Resource Consumption

Risk: one client saturates the ledger; huge payloads; unbounded pagination.

- Gateway `RequestRateLimiter` (Redis) per IP/user.
- `size` capped at 100; `page` bounded.
- Request body size limited at Nginx and Spring.
- gRPC deadlines (3s) on every call to the ledger.
- Evidence: `429 RATE_LIMITED` under k6 load; oversized body -> `413`.

## API5:2023 - Broken Function Level Authorization

Risk: a normal user reaches admin functions (reconciliation run, audit chain, balance verify).

- Admin endpoints live under `/internal/**` or require an admin role; routing keeps them off the
  public edge.
- Method + path allowlist in `SecurityConfig`.
- Evidence: calling `POST /api/v1/reconciliation/run` with a user token -> `403`.

## API6:2023 - Unrestricted Access to Sensitive Business Flows

Risk: automated abuse of a legitimate flow - e.g. velocity abuse, card testing, fake deposits.

- Fraud rule engine (`FraudRuleEngine`): VELOCITY, AMOUNT_LIMIT, NEW_DEVICE, GEO_ANOMALY.
- Idempotency + velocity limits make scripted abuse visible and blockable.
- Evidence: 10 transfers in a minute -> `422 FRAUD_BLOCKED` and a `fraud_alerts` row.

## API7:2023 - Server Side Request Forgery (SSRF)

Risk: user-supplied URL fetched by the server (e.g. a "returnUrl" or bank endpoint).

- Never fetch a user-supplied URL. Provider base URLs come from configuration.
- `returnUrl` is validated against an allowlist of schemes/hosts (e.g. `app://wallet/`).
- Evidence: grep for `RestClient`/`openFeign` targets - all from `@Value` config, not from a DTO.

## API8:2023 - Security Misconfiguration

Risk: default credentials, verbose stack traces, exposed actuator, permissive CORS.

- Actuator: only `health` and `prometheus` exposed; everything else disabled.
- `server.error.include-stacktrace=never`; errors follow the `ErrorCode` contract.
- CORS restricted to known origins; no `*` with credentials.
- Containers run non-root; no default passwords in compose (use `.env`).
- Evidence: `GET /actuator/env` -> `404`; error response has no stack trace.

## API9:2023 - Improper Inventory Management

Risk: old `/v1` left open, a dev service accidentally published, unmanaged gRPC port.

- Single versioned entry point (`/api/v1`); deprecated paths removed, not left running.
- Only the Gateway and `/webhooks/**` are exposed; service ports and gRPC stay on the Docker
  network and are not published in `docker-compose.prod.yml`.
- Documented inventory: `docs/04-api-contract.md` section 8.
- Evidence: `docker compose ps` shows no host-published port for services other than gateway/nginx.

## API10:2023 - Unsafe Consumption of APIs

Risk: trusting a third party (bank, payment provider) blindly.

- Webhook signature verified before any business logic; payload treated as untrusted.
- Provider responses validated; timeouts + circuit breaker (`resilience4j`) so a hung provider
  cannot exhaust threads.
- Duplicate provider events ignored via `UNIQUE(provider, event_id)`.
- Evidence: replayed webhook -> `duplicate: true`, no second credit.

## Quick review grep

```bash
# Any string-concatenated SQL?
grep -rn "createQuery\|JdbcTemplate" backend --include=*.java | grep -n "\" +"

# Any .equals on signatures/tokens?
grep -rn "signature.equals\|token.equals" backend --include=*.java

# Any hardcoded secret?
grep -rniE "secret|password" backend --include=*.yml | grep -v "\${"
```
