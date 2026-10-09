# FinTech Wallet — web dev client

React + TypeScript + Vite client for **local** testing of the wallet backend. It calls the
Spring services directly (no api-gateway needed for basic testing) through a per-service Vite
proxy, and signs every request with HMAC-SHA256 so the same code works against the gateway.

## Run

```bash
npm install
npm run dev        # http://localhost:5173
npm run typecheck  # tsc --noEmit
npm run build      # production bundle into dist/
```

Start the backend services first, each with the `local` profile and Eureka off:

```bash
cd backend
java -jar account-ledger-service/target/account-ledger-service-1.0.0-SNAPSHOT.jar  --spring.profiles.active=local --eureka.client.enabled=false
java -jar transaction-engine/target/transaction-engine-1.0.0-SNAPSHOT.jar          --spring.profiles.active=local --eureka.client.enabled=false
java -jar payment-gateway-service/target/payment-gateway-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=local --eureka.client.enabled=false
java -jar notification-service/target/notification-service-1.0.0-SNAPSHOT.jar      --spring.profiles.active=local --eureka.client.enabled=false
java -jar audit-service/target/audit-service-1.0.0-SNAPSHOT.jar                    --spring.profiles.active=local --eureka.client.enabled=false
```

Seed accounts: `0912345678` (admin) and `0987654321`, password `S3cret!23`.

> Do **not** run `mvn install/package` while a service is running — overwriting the jar under a
> live JVM breaks lazy class loading (`NoClassDefFoundError`). Stop, rebuild, restart.

## Configuration (Vite env)

| Var | Default | Purpose |
|---|---|---|
| `VITE_USE_GATEWAY` | `false` | `true` → route `/api` + `/webhooks` through the gateway; HMAC is then enforced |
| `VITE_GATEWAY` | `http://localhost:8080` | gateway base url |
| `VITE_LEDGER` | `http://localhost:8082` | account-ledger-service (auth + internal ledger) |
| `VITE_TXENGINE` | `http://localhost:8081` | transaction-engine |
| `VITE_PAYMENT` | `http://localhost:8083` | payment-gateway-service |
| `VITE_NOTIFICATION` | `http://localhost:8084` | notification-service |
| `VITE_AUDIT` | `http://localhost:8085` | audit-service |
| `VITE_INTERNAL_TOKEN` | `dev-internal-token` | injected by the proxy for `/internal/**` only |

## What you can test

- **Ví** — danh sách ví + số dư, chuyển/nạp/rút tiền (mỗi giao dịch có `X-Idempotency-Key`, có nút **Gửi lại** để xem idempotent replay).
- **Lịch sử** — giao dịch (kèm bút toán kép + cảnh báo gian lận), sao kê, phiên nạp tiền, thông báo.
- **Quản trị** (cần user admin) — verify chuỗi hash audit, báo cáo đối chiếu, chạy đối chiếu, audit log.
- **Dev tools** — trạng thái service, xin/xác thực OTP, demo HMAC, và **nhật ký mọi request** với `stringToSign` + chữ ký.

Ghi chú: ví chỉ được cộng sau khi provider callback và consumer Kafka `payment.succeeded` chạy —
local không có Kafka thì giao dịch nạp đứng ở `PENDING`; chạy kèm docker-compose để thấy số dư tăng.
