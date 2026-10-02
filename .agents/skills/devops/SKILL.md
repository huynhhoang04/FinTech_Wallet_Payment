---
name: devops
description: DevOps workflow for this repo - Docker Compose on a single VPS, Maven/Jib images to GHCR, GitHub Actions CI/CD with health-gated blue/green deploy, Nginx edge (REST + gRPC), Prometheus/Grafana/Alertmanager/Loki. Use when editing docker-compose, workflows, nginx config, monitoring config, deploy/rollback scripts, or when debugging a failed pipeline, a bad deployment, RAM pressure on the VPS, or missing metrics and alerts.
license: Proprietary
metadata:
  project: fintech-wallet-payment
  version: "1.0"
  owner: devops-academy
---

# DevOps

The delivery pipeline for the FinTech Wallet & Payment Gateway. This is a **DevOps project**:
the infrastructure story is graded as heavily as the code. Everything must be reproducible from
the repo - no manual steps that only exist on one machine.

Authoritative plan: `docs/05-project-structure.md` (section 5-6), `docs/06-milestones.md`.

## 1. Ground rules

1. **One VPS, Docker Compose.** No Kubernetes, no service mesh. If a proposal starts with
   "install a cluster", it is out of scope.
2. **No Dockerfile for Java.** Images are built by **Jib** from the Maven build.
3. **No secret in the repo.** Compose reads `.env`; CI reads GitHub secrets.
4. **Every container has a healthcheck**, and nothing is considered deployed until it is healthy.
5. **The VPS has 8 GB RAM.** Check the budget before adding a component (section 6).
6. **Never deploy an untested image.** `mvn verify` must pass before an image is built.

## 2. Layout

```
infra/
├─ docker/
│  ├─ docker-compose.yml          # base: postgres, redis, kafka, prometheus, grafana, loki, alertmanager
│  ├─ docker-compose.app.yml      # the 7 backend services + nginx
│  ├─ docker-compose.prod.yml     # VPS overrides: restart policy, mem limits, no host port for services
│  └─ .env.example
├─ nginx/
│  ├─ nginx.conf
│  └─ conf.d/{api.conf,grpc.conf,upstream.conf}
├─ monitoring/
│  ├─ prometheus/{prometheus.yml,alert-rules.yml}
│  ├─ alertmanager/alertmanager.yml
│  ├─ grafana/{provisioning,dashboards}
│  ├─ loki/loki-config.yml
│  └─ promtail/promtail-config.yml
└─ scripts/{deploy.sh,rollback.sh,backup-db.sh,seed.sh}
```

Run everything from the repo root:

```bash
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml up -d
```

## 3. Compose conventions

- Base file = infrastructure only; app file = the services. Prod file only overrides.
- Every service: `restart: unless-stopped`, an explicit `mem_limit`, `healthcheck`, and
  `logging` capped (`max-size: 10m`, `max-file: 3`) so logs cannot fill the disk.
- Services talk over the compose network by **service name** (`postgres:5432`, `kafka:9092`) -
  the `docker` Spring profile uses those hostnames.
- **Only** `nginx` publishes host ports (80/443). Postgres, Redis, Kafka, Prometheus, Grafana
  are not published in prod.
- JVM services get `-XX:MaxRAMPercentage=70` and a `mem_limit` that matches.
- Postgres: the init script creates the five databases (`tx_db`, `ledger_db`, `payment_db`,
  `notification_db`, `audit_db`) on first boot; the volume must be removed to re-run it.
- Kafka runs **KRaft** (no ZooKeeper container).

## 4. Images (Jib)

```bash
# Build + push one service
./mvnw -pl transaction-engine -am jib:build \
  -Djib.to.image=ghcr.io/<org>/fintech-wallet-transaction-engine:$GIT_SHA

# Build into the local Docker daemon (for compose on the VPS or local testing)
./mvnw -pl transaction-engine -am jib:dockerBuild \
  -Djib.to.image=fintech-wallet-transaction-engine:local
```

- Tag with the **git SHA** for deploys, plus a moving `latest` for convenience.
- Jib must be configured with a non-root user, a JRE base image, and no `.env` in the image.
- `-am` is mandatory: it builds `proto` and `common` first.

## 5. CI/CD

### CI - `.github/workflows/backend-ci.yml` (push / PR)
```
checkout -> setup-java 21 + cache ~/.m2 -> ./mvnw -B verify
        -> jacoco report (artifact) -> trivy fs/image -> gitleaks -> upload artifacts
```
- `verify` runs unit tests (surefire) **and** integration tests (failsafe + Testcontainers).
- Required status check on `main`; a red pipeline blocks merge.
- Reproduce locally with the same command: `cd backend && ./mvnw -B verify`.

### CD - `.github/workflows/backend-cd.yml` (main only)
```
./mvnw -B verify (again, on the merge commit) -> jib:build -> push GHCR
        -> ssh VPS: scripts/deploy.sh <service> <sha> -> smoke test -> notify Telegram
```
- Deploy authenticates with an SSH key held in GitHub secrets.
- Failed smoke test triggers `scripts/rollback.sh` automatically.

### Blue/green - honest scope
A full dual stack does **not** fit in 8 GB, so blue/green is applied **per service**, not to the
whole system:

1. Bring up the new version of the changed service on the inactive port (e.g. green 8181).
2. Wait for `/actuator/health` to report `UP`.
3. Swap `infra/nginx/conf.d/upstream.conf` and `nginx -s reload` (zero-downtime switch).
4. Keep the previous container stopped (not removed) for the rollback window, then clean up.

Rollback = point the upstream back and start the old container, or redeploy the previous image
tag. Steps are in [references/deploy-runbook.md](references/deploy-runbook.md).

## 6. RAM budget (8 GB VPS)

| Component | ~RAM |
|---|---|
| OS + Docker | 800 MB |
| Nginx | 50 MB |
| 5 JVM services | ~2.3 GB |
| PostgreSQL | 500 MB |
| Redis | 150 MB |
| Kafka KRaft | 1.2 GB |
| Prometheus + Grafana | 500 MB |
| Loki + Promtail | 300 MB |
| Alertmanager | 50 MB |
| **Total** | **~5.9 GB** |

Headroom is thin. When it runs short, cut in this order: Loki -> Grafana/Prometheus (run them off
the VPS against the VPS targets) -> tracing. Never cut Postgres, Redis, or Kafka.

```bash
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}'   # who is eating RAM
free -h
```

## 7. Nginx

- `api.conf`: TLS (Certbot), HTTP/2, HSTS, redirect 80 -> 443, `proxy_pass` to the gateway,
  rate limiting at the edge, body size cap.
- `grpc.conf`: `grpc_pass grpc://account-ledger-service:9090;` with `listen 443 ssl http2`,
  and `error_page 502 = /error502grpc;`.
- TLS certificates are renewed by a Certbot timer; validate config with `nginx -t` before reload.

## 8. Monitoring

- Prometheus scrapes `/actuator/prometheus` on every service, plus `node-exporter` (VPS) and
  `cadvisor` (containers).
- Grafana dashboards: transaction throughput, p95 latency, error rate, VPS resources, and the
  **ledger imbalance** panel.
- Alertmanager routes to Telegram. The financial alerts are non-negotiable:
  `ledger_imbalance_total > 0`, transaction error rate > 2%, service down, RAM/disk > 85%.
- Loki + Promtail collect JSON logs; search by `requestId`/`traceId` across services.

Alert rule definitions and dashboard queries:
[references/monitoring-alerts.md](references/monitoring-alerts.md).

## 9. Twelve-factor check (how this repo complies)

| Factor | Here |
|---|---|
| Config | Environment variables; `local`/`docker`/`prod` Spring profiles |
| Dependencies | Maven, declared in the parent POM, no system-wide jars |
| Backing services | Postgres/Redis/Kafka addressed by env - swappable, no code change |
| Build/release/run | Jib build -> GHCR release -> Compose run; strictly separated |
| Processes | Stateless services; state only in Postgres/Redis/Kafka |
| Port binding | Services bind their own port and self-register with Eureka |
| Disposability | Healthchecks + restart policy + health-gated deploy |
| Logs | JSON to stdout, shipped by Promtail to Loki - never to a file inside the container |
| Admin processes | `scripts/` and `mvn` one-off tasks, never a long-lived debug shell |

## 10. Common tasks

```bash
# Full stack up / down
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml up -d
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml down

# Logs for one service
docker compose -f infra/docker/docker-compose.yml logs -f transaction-engine

# Health of everything
docker compose ps --format 'table {{.Name}}\t{{.Status}}'

# Reload nginx after a config change
docker compose exec nginx nginx -t && docker compose exec nginx nginx -s reload

# Backup all databases
./infra/scripts/backup-db.sh
```

## 11. References

- [references/deploy-runbook.md](references/deploy-runbook.md) - first-time VPS setup, deploy,
  rollback, incident triage.
- [references/monitoring-alerts.md](references/monitoring-alerts.md) - scrape config, alert rules,
  dashboard queries, log search.
- Milestone schedule and cut list: `docs/06-milestones.md`.
