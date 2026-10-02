# AGENTS.md

Instructions for AI coding agents (and humans) working in this repository. Read this first, then
load the matching skill before writing code.

## 1. What this project is

**FinTech Wallet & Payment Gateway** - a microservices system for a DevOps academy capstone.
It models a wallet: accounts, deposits, transfers, double-entry bookkeeping, payment-provider
webhooks, fraud detection, and immutable audit.

It is graded as a **DevOps project**, not an app-building project. The infrastructure story -
containerization, CI/CD, deployment, observability - carries as much weight as the code. Prefer
the simpler implementation that keeps the pipeline and the money correct.

The assignment brief (Vietnamese) is in `README.MD`.

## 2. Hard constraints (do not violate)

| Constraint | Value |
|---|---|
| Backend language | **Java 21 LTS + Spring Boot 3.5.x** - all services, one toolchain |
| Architecture | **Microservices** |
| Database | **PostgreSQL** for all services |
| Client | **React Native (Expo)** in `app/` |
| Deployment | **Docker Compose on a single VPS** (4 vCPU / 8 GB RAM) |
| Event bus | **Apache Kafka (KRaft mode)** |
| Internal RPC | **gRPC (proto3)** |
| Build | **Maven multi-module** |
| Deadline | Everything done by **2026-10-12** (15 days from 2026-09-28) |

Do not introduce Kubernetes, MongoDB, Elasticsearch, Keycloak, a second backend language, or a
service mesh. These were considered and rejected - see `docs/01-tech-stack.md` section 9.

## 3. The two rules that outrank everything

> **1. Money must never be created, destroyed, or spent twice.**
> **2. Every write path that touches money must be transactional and idempotent.**

If a change risks either, stop and read `.agents/skills/backend/references/ledger-invariants.md`.

## 4. Repository map

```
app/                        React Native (Expo) client - demo UI
backend/                    Maven multi-module Java services
├─ proto/                   gRPC contracts (.proto)
├─ common/                  shared DTOs, errors, HMAC/JWT helpers, Kafka topics
├─ eureka-server/           service discovery
├─ api-gateway/             HMAC + JWT + rate limit + routing
├─ transaction-engine/      transfer/deposit/withdraw, idempotency, fraud rules
├─ account-ledger-service/  double-entry ledger (gRPC, PostgreSQL)
├─ payment-gateway-service/ provider integration + webhooks
├─ notification-service/    Kafka consumer, email/push
└─ audit-service/           immutable audit log + reconciliation
infra/                      compose, nginx, monitoring, scripts
.github/workflows/          CI/CD
docs/                       project documentation (01-06)
.agents/skills/             agent skills (see section 6)
```

## 5. Documentation index (`docs/`)

| Doc | Contents |
|---|---|
| `01-tech-stack.md` | Chosen technologies, versions, rationale, RAM budget |
| `02-service-breakdown.md` | The 7 services, their jobs, dependencies, ports |
| `03-jpa-schema.md` | Entities per service, constraints, seed data |
| `04-api-contract.md` | REST endpoints, gRPC proto, HMAC spec, error codes |
| `05-project-structure.md` | Repo layout, Maven modules, package rules, commands |
| `06-milestones.md` | 15-day schedule, per-day DoD, cut list, risk register |

Note: these docs are written in Vietnamese (project requirement). This file and the skills are in
English.

## 6. Skills - load the matching one before you start

Skills live in `.agents/skills/<name>/SKILL.md` (canonical). `.claude/skills`, `.agent/skills`
and `.commandcode/skills` are symlinks to that directory.

| Skill | Load it when the task involves |
|---|---|
| `security` | HMAC signing, JWT, auth, secrets, crypto, webhook verification, PII, security review |
| `devops` | docker-compose, GitHub Actions, nginx, monitoring/alerting, deploy, rollback, VPS |
| `backend` | any Java/Spring service, entity, endpoint, proto, Kafka, Redis, backend test |
| `frontend` | anything under `app/`, screens, API calls, client signing, tokens |

Rule of thumb: **read the skill before editing files in its domain.** Each skill opens with the
non-negotiables and links deeper reference material only when needed.

## 7. Conventions you must follow

- **Maven:** child modules declare dependencies **without versions** (the parent BOM owns them);
  a service is built with `-am`. No service depends on another service in Maven.
- **Packages:** `com.fintech.wallet.<short>`; layering is `controller -> service -> repository
  -> domain`. Do not return entities from controllers.
- **Money:** integer `Long`/`BIGINT` in the smallest unit (VND = dong). Never floating point.
  Format with `Intl.NumberFormat('vi-VN')` only at the edge.
- **Entities:** `@Version` on mutable entities; `@Immutable` + append-only for `LedgerEntry` and
  `AuditLog`; `@Enumerated(EnumType.STRING)`; no cross-database foreign keys.
- **Schema:** owned by JPA (`ddl-auto=update`). Invariants JPA cannot express are enforced in the
  service and verified by the reconciliation job.
- **Kafka:** publish through the outbox pattern; topic names come from `KafkaTopics`.
- **gRPC:** always set a deadline; never retry a posting call.
- **Errors:** throw `BusinessException(ErrorCode)`; response shape is fixed by
  `docs/04-api-contract.md` section 0.4.
- **Config:** environment variables with `local` / `docker` / `prod` Spring profiles; no secret
  in the repo.
- **Code style:** Lombok + MapStruct; constructor injection; no comments that restate the code.

## 8. Commands

```bash
# Backend
cd backend
./mvnw clean verify                                   # build + unit + integration tests
./mvnw -pl transaction-engine -am spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl account-ledger-service -am jib:build       # build + push the image

# Infrastructure
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml up -d
docker compose -f infra/docker/docker-compose.yml logs -f transaction-engine

# Client
cd app && npm install && npx expo start && npx tsc --noEmit
```

## 9. How to work here

1. **Read before writing.** Read the relevant doc and skill, and the files you are about to
   change. Never propose an edit to a file you have not read.
2. **Match the plan.** `docs/06-milestones.md` is the schedule; if a request conflicts with a
   decision already recorded in `docs/`, say so instead of silently diverging.
3. **Keep scope tight.** Implement what was asked. No speculative abstractions, no unrequested
   refactors, no extra configuration knobs.
4. **Validate before declaring done.** Run `./mvnw verify` (backend), `npx tsc --noEmit` (app),
   or `docker compose config` (infra). Fix what you break.
5. **Keep `main` green.** Never leave a failing build or a red pipeline behind.
6. **Document contract changes.** If an API, proto, topic, or entity changes, update the matching
   doc in the same change.
7. **Clean up after yourself.** Stop any dev server or background process you started.

## 10. Anti-patterns

- `UPDATE accounts SET balance = balance - ?` without writing ledger entries.
- Locking accounts in anything other than ascending `accountId` order.
- Publishing to Kafka inside a database transaction (use the outbox).
- A second idempotency key generated per retry.
- Retrying `PostTransfer`.
- Committing `.env`, a secret, or a real account number.
- Adding a service that is not in `docs/02-service-breakdown.md` without a recorded decision.
- Editing an existing `ledger_entries` or `audit_logs` row.
