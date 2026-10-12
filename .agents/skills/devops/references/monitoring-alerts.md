# Monitoring and Alerts

Reference for the `devops` skill. Defines what is scraped, what is alerted, and which queries
answer the money questions.

## 1. Scrape targets (`infra/monitoring/prometheus/prometheus.yml`)

```yaml
scrape_configs:
  - job_name: backend-services
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - api-gateway:8080
          - transaction-engine:8081
          - account-ledger-service:8082
          - payment-gateway-service:8083
          - notification-service:8084
          - audit-service:8085
  - job_name: node
    static_configs: [{ targets: ['node-exporter:9100'] }]
  - job_name: cadvisor
    static_configs: [{ targets: ['cadvisor:8080'] }]
```

- Services expose metrics via `management.endpoints.web.exposure.include=health,prometheus`.
- The `server.port` override for Prometheus must not collide with the gRPC port (9090) - use 9091
  for Prometheus itself if it runs on the host network.

## 2. Alerts (`alert-rules.yml`)

```yaml
groups:
  - name: financial
    rules:
      - alert: LedgerImbalance
        expr: ledger_imbalance > 0
        for: 1m
        labels: { severity: critical }
        annotations:
          summary: "Ledger Debit != Credit (imbalance {{ $value }})"

      - alert: ReconciliationStale
        expr: time() - reconciliation_last_success_timestamp_seconds > 86400
        for: 10m
        labels: { severity: critical }
        annotations:
          summary: "Reconciliation has not succeeded in 24h"

      - alert: HighTransactionErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{job="backend-services",status=~"5.."}[5m]))
          / sum(rate(http_server_requests_seconds_count{job="backend-services"}[5m])) > 0.02
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Transaction error rate above 2%"

  - name: infrastructure
    rules:
      - alert: ServiceDown
        expr: up{job="backend-services"} == 0
        for: 2m
        labels: { severity: critical }
      - alert: RamHigh
        expr: (1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) > 0.85
        for: 10m
        labels: { severity: warning }
      - alert: DiskHigh
        expr: (1 - node_filesystem_avail_bytes{fstype!="tmpfs"} / node_filesystem_size_bytes{fstype!="tmpfs"}) > 0.85
        for: 10m
        labels: { severity: warning }
      - alert: KafkaConsumerLag
        expr: kafka_consumergroup_lag > 1000
        for: 10m
        labels: { severity: warning }
```

Alertmanager routes `severity: critical` to Telegram immediately and `warning` with a grouped
delay. Configure the bot token and chat id from `.env`, never hardcoded.

## 3. Custom metrics to register

| Metric | Type | Source | Why |
|---|---|---|---|
| `ledger_imbalance` | gauge | `audit-service` | The single most important number: must stay 0 |
| `reconciliation_last_success_timestamp_seconds` | gauge | `audit-service` | Detects a silently broken reconciliation job |
| `fraud_alerts_total{rule,decision}` | counter | `transaction-engine` | Visibility into abuse |
| `idempotent_replays_total` | counter | `transaction-engine` | Retry pressure / duplicate submissions |
| `ledger_post_duration_seconds` | timer | `account-ledger-service` | Posting latency |

Register them with Micrometer; do not compute them at scrape time from the database.

## 4. Grafana panels that must exist

1. Transaction throughput (`rate(http_server_requests_seconds_count[1m])` by service).
2. p95 latency (`histogram_quantile(0.95, ...)` on the transfer endpoint).
3. Error rate (5xx share) with the 2% alert line drawn.
4. **Ledger imbalance** - the `ledger_imbalance` value, alongside the last
   reconciliation timestamp.
5. VPS resources: CPU, RAM, disk, per-container memory (`container_memory_usage_bytes`).
6. Kafka consumer lag per topic.

## 5. Log search (Loki)

- Logs are JSON with `requestId`, `traceId`, `service`, `level`.
- Trace one request across services:

```logql
{service=~"api-gateway|transaction-engine|account-ledger-service"} |= "<requestId>"
```

- Money-path errors only:

```logql
{service="account-ledger-service"} | json | level="ERROR" |= "ledger"
```

## 6. Verify monitoring works (milestone M5 evidence)

```bash
curl -s http://localhost:8081/actuator/prometheus | grep -c '^'      # metrics present
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'
docker stop transaction-engine                                        # expect a Telegram alert in <= 2 min
```

Capture a screenshot of the Grafana dashboard and the Telegram alert for the report.
