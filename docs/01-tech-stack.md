# 01 — CHỐT TECH STACK (FinTech Wallet & Payment Gateway)

> Tài liệu này chốt **công nghệ cụ thể** cho toàn hệ thống. Mọi tài liệu và code sau đó phải tuân theo đây.
> Bản chất: đây là **đồ án DevOps**, không phải đồ án viết app. Vì vậy tiêu chí chọn tech là:
> **hạ tầng vận hành được, CI/CD và monitoring rõ ràng, số lượng service vừa phải, ít code nghiệp vụ nhất có thể.**

---

## 0. RÀNG BUỘC & NGUYÊN TẮC CHỌN

| Loại | Ràng buộc |
|---|---|
| Bắt buộc | Kiến trúc **Microservices** |
| Bắt buộc | **PostgreSQL** |
| Bắt buộc | Backend **Java Spring Boot** (yêu cầu của giảng viên) |
| Bắt buộc | App client **React Native** (folder `app/` đã có sẵn) |
| Bắt buộc | Triển khai trên **VPS thực tế** (Ubuntu, 4 vCPU / 8GB RAM / 50GB SSD) |
| Thời gian | **2 tuần** |
| Kỳ vọng chấm điểm | Chứng minh **Financial Integrity**, **Idempotency**, **gRPC**, **CI/CD**, **Monitoring/Alerting** |

**Nguyên tắc vàng:**
1. **Một ngôn ngữ backend duy nhất (Java)** → không phải nuôi 2 toolchain, Docker build đồng nhất, CI 1 pipeline.
2. **Mọi thứ chạy bằng Docker Compose trên 1 VPS** → không K8s, không cluster, không managed service.
3. **Không self-host thứ tốn RAM** (Elasticsearch, Keycloak, SonarQube, Kafka + ZooKeeper) → xem mục 8 để biết ngân sách RAM.
4. **Chỉ dùng thứ có sẵn image chính chủ + cấu hình bằng ENV** → tái lập được môi trường, không "à em cài tay".

---

## 1. BẢNG TECH STACK TỔNG HỢP

> Version ghi theo major/minor khuyến nghị; **chốt patch mới nhất tại thời điểm khởi tạo** và ghi lại vào `docs/` (pinning để build tái lập được).

| Tầng | Công nghệ | Version | Vai trò |
|---|---|---|---|
| Ngôn ngữ backend | Java (LTS) | 21 | Ngôn ngữ cho toàn bộ service |
| Framework | Spring Boot | 3.5.x | Nền tảng service + autoconfig |
| Build tool | Maven | 3.9.x | Build đa module, dễ viết CI |
| API Gateway | Spring Cloud Gateway | (theo BOM Spring) | HMAC verify, routing, rate limit |
| Internal RPC | gRPC + Protocol Buffers | proto3 | Tx Engine ↔ Ledger Service |
| Message broker | Apache Kafka (**KRaft**) | 4.x | Event bất đồng bộ giữa các service |
| CSDL chính | PostgreSQL | 16.x | Account, Ledger, Payment, Audit |
| Cache / Lock | Redis (+ Redisson) | 7.4 | Idempotency key, distributed lock, rate limit |
| Migration | Flyway | 10.x | Version hoá schema DB |
| Auth | Spring Security + JWT (jjwt) | 0.12.x | Xác thực user trên app |
| Resilience | Resilience4j | 2.x | Circuit breaker, retry, timeout |
| Observability | Micrometer + Actuator | (theo BOM) | Metrics + health cho Prometheus |
| App client | React Native (Expo) + TypeScript | Expo SDK mới nhất | App ví cho người dùng |
| Edge proxy | Nginx | 1.27.x | TLS, HTTP/2, rate limit tầng biên, gRPC proxy |
| Container | Docker + Docker Compose | Compose v2 | Đóng gói & orchestration |
| Image build | Jib (Maven plugin) | mới nhất | Build image Java không cần Dockerfile |
| Registry | GHCR (GitHub Container Registry) | — | Lưu image, free |
| CI/CD | GitHub Actions | — | Build → Test → Scan → Push → Deploy |
| Monitoring | Prometheus + Grafana + Alertmanager | 2.x / 11.x / 0.27 | Metrics, dashboard, cảnh báo |
| Logging | Loki + Promtail | 3.x | Gom log tập trung |
| Load test | k6 | 0.5x | Chứng minh idempotency & concurrency |
| Test | JUnit 5 + Mockito + AssertJ | — | Unit test |
| Test tích hợp | Testcontainers | 1.20.x | Postgres/Redis/Kafka thật trong CI |

---

## 2. BACKEND — JAVA SPRING BOOT

**Chốt:** Java 21 (LTS) + Spring Boot 3.5.x, build **Maven multi-module monorepo**.

### 2.1. Vì sao Java 21 chứ không phải 25
- Java 21 là LTS đã qua kiểm chứng, toàn bộ hệ sinh thái Spring/Testcontainers/Redisson hỗ trợ tốt nhất → đổi lại **thời gian debug thấp hơn**, đúng tinh thần 2 tuần.
- Dùng **Virtual Threads (Loom)** của Java 21 cho các call I/O (gRPC, DB, HTTP tới ngân hàng) để đạt throughput cao mà không phải viết code reactive phức tạp.
- Ghi chú: Java 25 (LTS) cũng là lựa chọn hợp lệ nếu muốn "mới hơn"; nhưng không mang lại điểm DevOps nào thêm → chỉ chọn nếu chắc chắn về toolchain.

### 2.2. Thư viện Spring dùng trong mọi service
| Thành phần | Thư viện | Dùng để |
|---|---|---|
| Web/REST | `spring-boot-starter-web` | API cho app & webhook |
| Gateway | `spring-cloud-starter-gateway` | Routing, filter HMAC, rate limit |
| gRPC server/client | `net.devh:grpc-spring-boot-starter` | Chạy gRPC trong Spring Boot |
| Kafka | `spring-kafka` | Producer/Consumer, `@KafkaListener` |
| Redis | `spring-boot-starter-data-redis` + **Redisson** | Cache, idempotency, lock phân tán |
| DB | `spring-boot-starter-data-jpa` + `JdbcTemplate` | CRUD + SQL kiểm soát ở luồng tiền |
| Migration | `flyway-core` | Quản lý version schema |
| Auth | `spring-boot-starter-security` + `jjwt` | JWT |
| Resilience | `resilience4j-spring-boot3` | Circuit breaker, retry, bulkhead |
| Metrics | `micrometer-registry-prometheus` + actuator | `/actuator/prometheus` |
| Code gen | **MapStruct** | DTO ↔ Entity, tránh code tay |
| Boilerplate | Lombok | Giảm code lặp |
| Test | `spring-boot-starter-test`, `testcontainers-*` | Unit + integration |

### 2.3. Vì sao Maven (không Gradle)
- Maven multi-module + `pom.xml` parent cho `dependencyManagement` tập trung → CI cấu hình 1 lần, tất cả module dùng chung version.
- Viết GitHub Actions đơn giản hơn (`mvn -B verify`), cache `~/.m2` ổn định.
- Số module chỉ ~6 → ưu thế tốc độ của Gradle không đáng đánh đổi.

### 2.4. Tầng ghi tiền không dùng ORM mù quáng
Với `Ledger Service` (đường đi sống còn của tiền):
- Dùng **JdbcTemplate / native SQL** cho các thao tác ghi `ledger_entries` và khoá tài khoản (`SELECT ... FOR UPDATE`).
- JPA chỉ dùng cho CRUD đơn giản (user, account metadata).
- Lý do: kiểm soát chính xác isolation level, thứ tự lock (chống deadlock), tránh N+1 và tránh Hibernate "tự diễn".

---

## 3. TẦNG DỮ LIỆU

| Store | Version | Vai trò | Ai dùng |
|---|---|---|---|
| **PostgreSQL** | 16.x | SSOT tài chính: `accounts`, `ledger_entries`, `transactions`, `payment_intents`, `audit_logs` | Ledger, Transaction, Payment, Audit |
| **Redis** | 7.4 | Idempotency key (TTL), distributed lock (Redisson), rate limit, cache session | Gateway, Transaction |
| **Kafka (KRaft)** | 4.x | Event bus: `tx.completed`, `payment.webhook`, `risk.alert` | Tx Engine → Fraud / Notification / Audit |

**Nguyên tắc dữ liệu:**
- **Database-per-service**: mỗi service sở hữu schema riêng, **không service nào đọc chéo schema của service khác**. Muốn dữ liệu thì gọi API/gRPC/nghe event.
- PostgreSQL chạy **1 instance** trên VPS nhưng tách **1 database (hoặc 1 schema) cho mỗi service** → vừa tiết kiệm RAM, vừa thể hiện ranh giới service.
- Ledger dùng `REPEATABLE READ` mặc định, các giao dịch tiền tệ nâng lên `SERIALIZABLE` khi cần; khoá tài khoản theo **thứ tự account_id tăng dần** để chống deadlock.
- Kafka **KRaft mode** (không ZooKeeper) → bớt 1 container và ~500MB RAM.

**Không dùng:**
- ❌ **MongoDB**: đề yêu cầu PostgreSQL, thêm NoSQL chỉ tăng gánh nặng vận hành mà không thêm điểm.
- ❌ **Elasticsearch**: ngốn 1–2GB RAM, không cần cho 2 tuần. Audit log để trong PostgreSQL, log hệ thống để trong Loki.
- ❌ **TimescaleDB**: Postgres thường + partition theo tháng là đủ cho khối lượng demo.

---

## 4. GIAO TIẾP GIỮA CÁC SERVICE

| Kiểu | Công nghệ | Dùng cho |
|---|---|---|
| Đồng bộ, nội bộ, hiệu năng cao | **gRPC / proto3** | Tx Engine → Ledger Service (`Debit`/`Credit`/`GetBalance`) |
| Bất đồng bộ, fan-out | **Kafka** | Tx Engine publish `tx.completed` → Notification, Audit, Fraud |
| Đồng bộ, hướng client | **REST/JSON** | App ↔ API Gateway |
| Webhook từ ngoài | **REST + HMAC verify** | Ngân hàng/cổng thanh toán → Payment Service |

**Quy ước bắt buộc:**
- Mọi request tài chính mang header `X-Idempotency-Key: <UUID>`; key lưu Redis TTL 24h kèm kết quả trả về.
- Mọi request từ client qua Gateway phải có chữ ký `HMAC-SHA256(payload, secret)`. Gateway từ chối ngay nếu sai/thiếu.
- Proto file nằm trong module dùng chung (`backend/proto`) và **được biên dịch tự động trong build Maven**, không commit code generated.

---

## 5. APP CLIENT — REACT NATIVE

- **Expo (managed workflow) + TypeScript**: không phải cấu hình native build (Xcode/Gradle), chạy demo nhanh, phù hợp 2 tuần.
- Điều hướng: **Expo Router**. Data/state: **TanStack Query** + **Zustand**.
- Ký request bằng **crypto-js** (`HmacSHA256`) — thuần JS, không cần native module, đủ cho demo chữ ký số.
- Vai trò app trong đồ án: **client demo** cho luồng nạp / chuyển tiền / xem số dư. **Không** đầu tư UI đẹp — trọng tâm là DevOps.

> Lưu ý: folder `app/` hiện đang trống, sẽ khởi tạo Expo project tại đây khi bắt đầu code.

---

## 6. HẠ TẦNG & DEVOPS (PHẦN TRỌNG TÂM)

### 6.1. Triển khai
- **Docker Compose trên 1 VPS** (Ubuntu LTS). Tất cả service + hạ tầng chạy bằng `docker compose up -d`.
- Tách file compose: `docker-compose.yml` (base) + `docker-compose.prod.yml` (override cho VPS) + `.env` (không commit).
- **Nginx** làm edge: TLS (Let's Encrypt/Certbot), HTTP/2, `grpc_pass` cho gRPC, rate limit tầng biên.
- **Jib** build image Java (layered, reproducible) → đẩy lên **GHCR**.

### 6.2. CI/CD — GitHub Actions
Pipeline theo giai đoạn, fail-fast:

```
push/PR → [build & unit test] → [integration test - Testcontainers] → [scan image - Trivy]
        → [build & push image - Jib → GHCR] → [deploy VPS qua SSH] → [smoke test] → [notify Telegram]
```

- **Blue/Green đơn giản hoá**: 2 slot (blue/green) trong Compose, Nginx trỏ traffic sang slot mới sau khi health-check pass; rollback = trỏ lại slot cũ.
- Deploy dùng SSH key + GitHub Secrets; không hardcode bí mật.
- Chặn merge nếu test đỏ (required status check).

### 6.3. Monitoring & Alerting
- **Prometheus** scrape `/actuator/prometheus` của mọi service + `node-exporter` (VPS) + `cadvisor` (container).
- **Grafana** dashboard: throughput giao dịch, p95 latency, tỉ lệ lỗi, số dư đối chiếu, tài nguyên VPS.
- **Alertmanager → Telegram/Email** cho các alert tài chính & vận hành:
  - Tỉ lệ giao dịch thất bại > 2%.
  - **Tổng Debit ≠ tổng Credit** trong sổ cái (alert quan trọng nhất).
  - Service down / health-check fail, RAM/disk > 85%.
- **Loki + Promtail** gom log toàn hệ thống, tra cứu theo `traceId`/`requestId`.

### 6.4. (Tuỳ chọn) Tracing
- **OpenTelemetry + Tempo/Jaeger** để trace xuyên service (REST → gRPC → Kafka). Đây là **điểm cộng**, chỉ làm nếu còn thời gian, không đưa vào đường găng.

---

## 7. TESTING & CHỨNG MINH TIÊU CHÍ

| Tiêu chí đề bài | Cách chứng minh | Công cụ |
|---|---|---|
| Toàn vẹn kế toán kép | Test tổng Debit + Credit = 0 sau mọi giao dịch; job đối chiếu định kỳ | JUnit + SQL check |
| Idempotency | Bắn 100 request trùng `X-Idempotency-Key`, chỉ 1 lần trừ tiền | **k6** |
| Concurrency/Deadlock | Test chuyển tiền A→B và B→A đồng thời, không deadlock | Testcontainers + k6 |
| gRPC | Chứng minh Tx Engine gọi Ledger qua gRPC | Test tích hợp + log |
| HMAC | Gửi request sai/thiếu chữ ký → Gateway trả 401 | Postman/k6 |
| CI/CD & Monitoring | Video demo pipeline + dashboard + alert Telegram | GitHub Actions + Grafana |

**Chốt:** `k6` là công cụ chính để tạo bằng chứng cho tiêu chí 2 & 3 (nhẹ, script JS, có report).

---

## 8. NGÂN SÁCH RAM VPS 8GB (KIỂM TRA TÍNH KHẢ THI)

Các JVM đặt `-XX:MaxRAMPercentage=70` + giới hạn `mem_limit` trong Compose.

| Thành phần | RAM ước tính |
|---|---|
| OS + Docker daemon | ~800 MB |
| Nginx | ~50 MB |
| API Gateway (JVM) | ~400 MB |
| Transaction Engine (JVM) | ~500 MB |
| Ledger Service (JVM) | ~500 MB |
| Payment Gateway (JVM) | ~450 MB |
| Notification + Audit consumer (JVM) | ~450 MB |
| PostgreSQL | ~500 MB |
| Redis | ~150 MB |
| Kafka KRaft (1 node) | ~1.200 MB |
| Prometheus | ~300 MB |
| Grafana | ~200 MB |
| Loki + Promtail | ~300 MB |
| Alertmanager | ~50 MB |
| **Tổng** | **~6,3 GB** |

→ **Vừa 8GB nhưng sát ngưỡng.** Kết luận: **không thêm** Elasticsearch/Keycloak/SonarQube self-host (xem mục 9). Nếu thiếu, hạ xuống: bỏ Loki (dùng `docker logs`) hoặc chạy Grafana/Prometheus ở máy cá nhân trỏ về VPS.

---

## 9. NHỮNG THỨ **KHÔNG** DÙNG (KIỂM SOÁT PHẠM VI)

| Bị loại | Lý do |
|---|---|
| Kubernetes | Quá tải cho 2 tuần / 1 VPS 8GB; Compose đã đủ chứng minh năng lực DevOps |
| ZooKeeper | Kafka KRaft mode đã bỏ được |
| MongoDB | Đề bắt buộc PostgreSQL |
| Elasticsearch | Tốn RAM, không cần thiết |
| Keycloak | Thêm 1 service nặng; dùng Spring Security + JWT là đủ |
| SonarQube self-host | Tốn RAM; nếu cần thì dùng **SonarCloud** (SaaS, free repo public) |
| Service mesh (Istio/Linkerd) | Không cần ở quy mô này |
| Đa ngôn ngữ (Python/Go) | Phá vỡ nguyên tắc "1 toolchain", tăng thời gian build/CI |

---

## 10. CẤU TRÚC REPO ĐỀ XUẤT

```
FinTech_Wallet_Payment/
├─ app/                      # React Native (Expo) - client demo
├─ backend/                  # Maven multi-module monorepo
│  ├─ pom.xml                # parent: dependencyManagement + plugin chung
│  ├─ proto/                 # *.proto dùng chung, build sinh code
│  ├─ api-gateway/           # Spring Cloud Gateway: HMAC, routing, rate limit
│  ├─ transaction-engine/    # nhận lệnh, idempotency, lock, publish Kafka
│  ├─ ledger-service/        # gRPC server, kế toán kép, PostgreSQL
│  ├─ payment-gateway/       # tích hợp ngân hàng, webhook callback
│  ├─ notification-service/  # consumer Kafka: thông báo biến động số dư
│  └─ audit-service/         # consumer Kafka: audit log + đối chiếu
├─ infra/
│  ├─ docker/                # compose base + override prod, .env.example
│  ├─ nginx/                 # nginx.conf, certs
│  ├─ monitoring/            # prometheus.yml, alert rules, grafana dashboards
│  └─ scripts/               # deploy.sh, backup.sh, seed data
├─ .github/workflows/        # ci.yml, cd.yml
├─ docs/                     # tài liệu đồ án (folder này)
└─ README.MD                 # đề bài gốc
```

---

## 11. MAPPING: TECH → TIÊU CHÍ CHẤM ĐIỂM

| Tiêu chí đề bài | Tech đảm nhiệm |
|---|---|
| Microservices | 5–6 service Spring Boot tách biệt, DB-per-service |
| PostgreSQL + ACID | PostgreSQL 16, isolation level, `SELECT FOR UPDATE`, Flyway |
| Kế toán kép | Ledger Service (JVM, gRPC), bảng `ledger_entries` bất biến |
| Idempotency | Redis + `X-Idempotency-Key`, Redisson lock |
| gRPC hiệu năng | proto3 + grpc-spring-boot-starter, Nginx `grpc_pass` |
| HMAC chống giả mạo | Spring Cloud Gateway filter |
| Fraud Detection | Consumer Kafka + rule engine (Java) |
| CI/CD nghiêm ngặt | GitHub Actions + Testcontainers + Trivy + Blue/Green |
| Monitoring tài chính | Prometheus + Grafana + Alertmanager (alert lệch sổ cái) |

---

## 12. VIỆC CẦN CHỐT TIẾP & TÀI LIỆU TIẾP THEO

**Còn phải chốt (chưa cần ngay):**
1. Số lượng service chính xác (4 / 5 / 6) — sẽ chốt trong tài liệu kiến trúc.
2. Tên miền & nhà cung cấp VPS.
3. Cổng gRPC, port mapping, quy ước đặt tên.

**Tài liệu tiếp theo trong `docs/`:**
- `02-architecture.md` — sơ đồ service, luồng dữ liệu, ranh giới service, danh sách service chốt.
- `03-database-design.md` — schema PostgreSQL, bảng ledger, ràng buộc toàn vẹn.
- `04-api-contract.md` — REST API, proto gRPC, header HMAC/Idempotency.
- `05-cicd-deployment.md` — pipeline, Blue/Green, hạ tầng VPS.
- `06-monitoring.md` — metrics, dashboard, alert rules.
