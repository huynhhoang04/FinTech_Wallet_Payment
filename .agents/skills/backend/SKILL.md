---
name: backend
description: Java 21 and Spring Boot 3.5 backend conventions for this repo - Maven multi-module layout, package rules, JPA entities, double-entry ledger invariants, Redis idempotency, Redisson lock ordering, gRPC via proto, Kafka with the outbox pattern, and Testcontainers testing. Use when adding or editing a service, endpoint, entity, repository, proto contract, Kafka producer/consumer, or backend test, and when asked how a backend piece should be implemented.
license: Proprietary
metadata:
  project: fintech-wallet-payment
  version: "1.0"
  owner: devops-academy
---

# Backend

Conventions for the Java services. The two rules that outrank everything else:

> **Money must never be created, destroyed, or double-spent.**
> **Every write path that touches money must be transactional and idempotent.**

Authoritative specs: `docs/02-service-breakdown.md` (services), `docs/03-jpa-schema.md`
(entities), `docs/04-api-contract.md` (API), `docs/05-project-structure.md` (layout).

## 1. Stack

Java 21 (LTS) · Spring Boot 3.5.x · Spring Cloud 2025.0.x · Maven multi-module ·
PostgreSQL 16 · Redis 7.4 + Redisson · Kafka 4 (KRaft) · gRPC (proto3) · Jib · Testcontainers.

## 2. Module map and build order

```
proto -> common -> { eureka-server, api-gateway, transaction-engine,
                     account-ledger-service, payment-gateway-service,
                     notification-service, audit-service }
```

- `common` holds DTOs, error model, Kafka topic constants, HMAC/JWT helpers. It must **not**
  depend on `spring-boot-starter-web` or `-data-jpa`.
- Services must **not** depend on each other in Maven. Cross-service calls go over gRPC, REST,
  or Kafka only.
- Every service declares dependencies **without a version** - the parent POM's BOM owns versions.
- Build one service and its dependencies with `-am`.

## 3. Package layout inside a service

```
com.fintech.wallet.<short>/
├─ <Name>Application.java
├─ config/        # RedisConfig, KafkaConfig, RedissonConfig, SecurityConfig, JacksonConfig
├─ controller/    # REST, thin - validate + delegate only
├─ grpc/          # gRPC clients/servers and interceptors
├─ service/       # business logic, @Transactional boundary
├─ domain/        # @Entity classes
├─ repository/    # Spring Data repos + JdbcTemplate for the money path
├─ dto/           # request/ and response/
├─ mapper/        # MapStruct
├─ event/         # Kafka producers/consumers, OutboxPublisher
├─ exception/     # service-specific exceptions
└─ util/
```

Layering is one-directional: `controller -> service -> repository -> domain`. `config`,
`exception`, `util` never call back up into `service`.

Putting business logic in a controller, or returning an `@Entity` from a controller, is a
review blocker.

## 4. Money and entities

- Amounts are `Long` / `BIGINT` in the **smallest unit** (VND = dong). Never `double`/`float`/
  `BigDecimal`-from-string for stored money.
- `@Version long version` on every mutable entity (optimistic locking).
- `LedgerEntry` and `AuditLog` are `@Immutable` and append-only - never update or delete them.
- `@Enumerated(EnumType.STRING)` always.
- Timestamps are `Instant`; audit fields via `@CreatedDate`/`@LastModifiedDate` and
  `@EnableJpaAuditing`.
- Schema is owned by JPA (`ddl-auto=update`). Constraints JPA cannot express (e.g. "entries of a
  transaction sum to zero") are enforced in the service and verified by the reconciliation job.
- Cross-database references are plain `UUID` columns - **no cross-service foreign keys**.
- Uniqueness is a correctness tool, not just an index: `transactions.idempotency_key UNIQUE` and
  `webhook_logs UNIQUE(provider, event_id)` are the last line of defense against double-spend.

Details and proof obligations: [references/ledger-invariants.md](references/ledger-invariants.md).

## 5. The money write path (checklist for any new one)

1. Validate the DTO; reject `400 VALIDATION_ERROR`.
2. Require `X-Idempotency-Key`. `SETNX` in Redis with TTL 24h, storing the response.
   - Key present with the same payload -> return the stored response, `Idempotent-Replay: true`.
   - Key present with a different payload -> `409 IDEMPOTENCY_CONFLICT`.
3. Acquire a Redisson lock; for a two-account operation, order by `accountId` ascending.
4. Evaluate fraud rules (velocity, limits) before posting; block with `422 FRAUD_BLOCKED`.
5. Call the ledger over gRPC with a deadline. Do **not** retry `PostTransfer` - it is guarded by
   `transaction_id`.
6. Write the state change and the outbox event in **one** transaction.
7. Update status and publish from the outbox; never publish directly from the business method.

## 6. gRPC

- Contracts live in `proto/src/main/proto`; stubs are generated into `target/` and never committed.
- Every call sets a deadline (default 3s).
- Retry is allowed only for idempotent reads (`GetBalance`, `GetStatement`). Never retry a
  posting call - replay safety is provided by `transaction_id`, not by retry logic.
- Map gRPC errors to domain errors: `NOT_FOUND -> 404`, `FAILED_PRECONDITION -> 422`, deadline
  exceeded -> `503 UPSTREAM_UNAVAILABLE`.
- Use `resilience4j` around the client so a slow ledger opens a circuit instead of exhausting
  threads.

## 7. Kafka and the outbox

- Event payloads are defined in `common/event` and used by both producer and consumer.
- Producers never publish from inside the business transaction. Write an `OutboxEvent` row in the
  same transaction; an `OutboxPublisher` scheduler sends and marks `PUBLISHED`.
- Consumers are idempotent: `audit_logs.event_id` and the notification log key make a redelivery a
  no-op.
- Configure a dead-letter topic and store failures in `dead_letter_events`.
- Topic names come from `KafkaTopics` constants - never inline strings.

## 8. Errors

- Throw `BusinessException(ErrorCode)`; `GlobalExceptionHandler` in `common` renders the contract
  from `docs/04-api-contract.md` section 0.4.
- Add a new `ErrorCode` before inventing a new response shape.
- Never return a stack trace, a SQL error, or a provider's raw error to a client.

## 9. Testing

| Kind | Suffix | Runner | Expectations |
|---|---|---|---|
| Unit | `*Test` | surefire | No Spring context, fast, business logic and mappers |
| Integration | `*IT` | failsafe | Testcontainers (Postgres/Kafka/Redis), real transactions |

Minimum integration tests for the money path:
- Debit + Credit of a transaction sum to `0`.
- Snapshot balance equals `SUM(ledger_entries.amount)` after posting.
- Same idempotency key twice -> one posting.
- Concurrent `A->B` and `B->A` complete without a deadlock.
- Ledger unavailable -> `503`, no money moved.

See [references/testing.md](references/testing.md).

## 10. Code style

- Lombok for boilerplate, MapStruct for DTO mapping. Annotate mapper processors in order
  (lombok, mapstruct, binding) in the compiler plugin - a wrong order silently produces broken
  mappers.
- No comments that restate the code. Add one only where the reasoning is non-obvious (a
  concurrency decision, a financial invariant).
- Prefer constructor injection; no field injection.
- `open-in-view: false`; fetch what you need explicitly.

## 11. Commands

```bash
cd backend
./mvnw -pl transaction-engine -am clean verify            # build + unit + integration
./mvnw -pl transaction-engine -am spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl account-ledger-service verify -Dit.test=LedgerPostingIT
./mvnw -pl . -am clean package -DskipTests                # fast compile check (never in CI)
```

## 12. Definition of done for a backend change

- [ ] Layering respected; no entity leaked from a controller.
- [ ] Money path is transactional, idempotent, and lock-ordered.
- [ ] New/changed entity follows the naming, `@Version`, and enum conventions.
- [ ] New error case maps to an existing or newly added `ErrorCode`.
- [ ] Unit + integration tests added; `./mvnw verify` green.
- [ ] No secrets, no PII in logs, no cross-service Maven dependency.
- [ ] Docs updated if a contract changed (`docs/04-api-contract.md`).

## 13. References

- [references/ledger-invariants.md](references/ledger-invariants.md) - double-entry rules,
  lock ordering, isolation levels.
- [references/testing.md](references/testing.md) - Testcontainers setup and the required tests.
