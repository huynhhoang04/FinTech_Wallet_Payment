# 05 — CẤU TRÚC DỰ ÁN (MAVEN MULTI-MODULE)

> Tài liệu này chốt **cách tổ chức toàn bộ repo**: monorepo, backend là **Maven multi-module**, và cấu trúc thư mục/package của từng phần.
> Nguyên tắc: **một repo, nhiều module**; mọi thứ build/test/deploy được bằng **Maven + Docker Compose**, không cấu hình tay.

---

## 1. SƠ ĐỒ REPO CẤP CAO

```
FinTech_Wallet_Payment/
├─ app/                     # React Native (Expo) — client demo
├─ backend/                 # Maven multi-module — toàn bộ service Java
├─ infra/                   # Hạ tầng: docker, nginx, monitoring, scripts
├─ .github/workflows/       # CI/CD (GitHub Actions)
├─ docs/                    # Tài liệu đồ án (01 → 06)
├─ README.MD                # Đề bài gốc
├─ Agent.md / Claude.md     # Ghi chú đề bài (giữ nguyên)
├─ .env.example             # Mẫu biến môi trường (KHÔNG commit .env)
└─ .gitignore
```

**Vì sao monorepo:** 2 tuần + 1 người làm → 1 chỗ build, 1 pipeline, 1 lần `git clone`. Không tách nhiều repo.

---

## 2. QUY ƯỚC ĐẶT TÊN

| Loại | Quy ước | Ví dụ |
|---|---|---|
| Maven `groupId` | cố định | `com.fintech.wallet` |
| Maven `artifactId` | trùng tên thư mục module | `transaction-engine` |
| Maven `version` | khai báo **1 lần** ở parent | `1.0.0-SNAPSHOT` |
| Java package gốc | `com.fintech.wallet.<tên_ngắn>` | `com.fintech.wallet.txengine` |
| Database | `<domain>_db` | `tx_db`, `ledger_db`, `payment_db` |
| Kafka topic | `<domain>.<hành_động>` | `tx.completed`, `payment.succeeded` |
| Docker image | `ghcr.io/<org>/fintech-wallet-<service>` | `.../fintech-wallet-ledger-service` |
| Container name | `<service>` | `transaction-engine` |
| Env prefix | viết HOA, snake_case | `DB_URL`, `KAFKA_BOOTSTRAP` |

**Java package gốc từng service:**

| Service | Package gốc |
|---|---|
| `common` | `com.fintech.wallet.common` |
| `proto` | `com.fintech.wallet.proto` |
| `eureka-server` | `com.fintech.wallet.eureka` |
| `api-gateway` | `com.fintech.wallet.gateway` |
| `transaction-engine` | `com.fintech.wallet.txengine` |
| `account-ledger-service` | `com.fintech.wallet.ledger` |
| `payment-gateway-service` | `com.fintech.wallet.payment` |
| `notification-service` | `com.fintech.wallet.notification` |
| `audit-service` | `com.fintech.wallet.audit` |

---

## 3. BACKEND — MAVEN MULTI-MODULE

```
backend/
├─ mvnw / mvnw.cmd / .mvn/wrapper/     # Maven Wrapper (commit → CI build tái lập được)
├─ pom.xml                             # PARENT POM (packaging = pom)
│
├─ proto/                              # module nền: contract gRPC
│  ├─ pom.xml
│  └─ src/main/proto/
│     ├─ ledger.proto
│     └─ common.proto
│
├─ common/                             # module nền: thư viện dùng chung
│  ├─ pom.xml
│  └─ src/main/java/com/fintech/wallet/common/...
│
├─ eureka-server/                      # ↓ các module ứng dụng (Spring Boot jar)
│  ├─ pom.xml
│  └─ src/main/java/com/fintech/wallet/eureka/EurekaServerApplication.java
│
├─ api-gateway/
├─ transaction-engine/
├─ account-ledger-service/
├─ payment-gateway-service/
├─ notification-service/
└─ audit-service/
```

### 3.1. Thứ tự build (Maven reactor tự lo)
```
proto  →  common  →  {eureka-server, api-gateway, transaction-engine,
                      account-ledger-service, payment-gateway-service,
                      notification-service, audit-service}
```
`proto` và `common` là **thư viện** (`jar` thường), 7 service còn lại là **Spring Boot app** (`jar` repackage).

### 3.2. Parent POM (`backend/pom.xml`)
```xml
<project>
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.x</version>
    <relativePath/>
  </parent>

  <groupId>com.fintech.wallet</groupId>
  <artifactId>fintech-wallet-backend</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <properties>
    <java.version>21</java.version>
    <spring-cloud.version>2025.0.x</spring-cloud.version>
    <grpc-starter.version>x.y.z</grpc-starter.version>
    <mapstruct.version>x.y.z</mapstruct.version>
    <jib.version>x.y.z</jib.version>
    <testcontainers.version>x.y.z</testcontainers.version>
  </properties>

  <modules>
    <module>proto</module>
    <module>common</module>
    <module>eureka-server</module>
    <module>api-gateway</module>
    <module>transaction-engine</module>
    <module>account-ledger-service</module>
    <module>payment-gateway-service</module>
    <module>notification-service</module>
    <module>audit-service</module>
  </modules>

  <dependencyManagement>
    <!-- spring-cloud-dependencies (import scope) + grpc-spring-boot-starter
         + mapstruct + testcontainers BOM -->
  </dependencyManagement>

  <build>
    <pluginManagement>
      <!-- maven-compiler-plugin (annotation processor: lombok → mapstruct → binding)
           spring-boot-maven-plugin, jib-maven-plugin, jacoco,
           surefire (unit *Test), failsafe (integration *IT),
           protobuf-maven-plugin, spotless -->
    </pluginManagement>
  </build>
</project>
```

**Quy tắc dependency (bắt buộc):**
- Service con **không khai `<version>`** cho thư viện đã có trong BOM.
- Service con **không tự thêm plugin** đã có trong `pluginManagement`, chỉ cần `<plugin>` rỗng để bật.
- `common` **không** kéo `spring-boot-starter-web`/`-data-jpa` (giữ nhẹ, tránh ràng buộc chéo).
- Không service nào phụ thuộc service khác ở tầng Maven (chỉ gọi qua gRPC/REST/Kafka).

### 3.3. Module `proto`
```
proto/
├─ pom.xml
└─ src/main/proto/
   ├─ common.proto      # Money, ErrorCode, Empty
   └─ ledger.proto      # service LedgerService { ... }
```
- Sinh code vào `target/generated-sources/protobuf/` (build tự động, **không commit**).
- Plugin: `protobuf-maven-plugin` + `os-maven-plugin`.
- Ai cần gRPC thì thêm dependency vào `proto`.

### 3.4. Module `common`
```
com/fintech/wallet/common/
├─ constant/
│  ├─ KafkaTopics.java        # TX_COMPLETED, PAYMENT_SUCCEEDED, RISK_ALERT...
│  ├─ HeaderNames.java        # X-Idempotency-Key, X-Signature, X-Timestamp...
│  └─ Roles.java
├─ dto/
│  ├─ ApiResponse.java
│  └─ PageResponse.java
├─ event/                     # payload event dùng chung producer/consumer
│  ├─ TransactionCompletedEvent.java
│  └─ PaymentSucceededEvent.java
├─ error/
│  ├─ ErrorCode.java          # enum mã lỗi (xem doc 04 mục 0.4)
│  ├─ BusinessException.java
│  ├─ ApiError.java
│  └─ GlobalExceptionHandler.java   # @RestControllerAdvice
├─ hmac/
│  ├─ CanonicalRequest.java   # dựng stringToSign
│  └─ HmacUtils.java          # ký & verify (MessageDigest.isEqual)
├─ security/
│  └─ JwtUtils.java           # sinh/verify JWT (jjwt)
└─ util/
   ├─ MoneyUtils.java
   └─ IdUtils.java            # UUID, Idempotency-Key
```

### 3.5. Template 1 service (ví dụ `transaction-engine`)
```
transaction-engine/
├─ pom.xml
├─ src/main/java/com/fintech/wallet/txengine/
│  ├─ TransactionEngineApplication.java      # @SpringBootApplication @EnableJpaAuditing ...
│  ├─ config/
│  │  ├─ RedisConfig.java
│  │  ├─ RedissonConfig.java
│  │  ├─ KafkaProducerConfig.java
│  │  ├─ OpenFeignConfig.java
│  │  ├─ SecurityConfig.java
│  │  └─ JacksonConfig.java
│  ├─ controller/
│  │  ├─ TransactionController.java
│  │  ├─ AccountController.java
│  │  └─ InternalTransactionController.java
│  ├─ grpc/
│  │  ├─ LedgerClient.java                   # gọi account-ledger-service
│  │  └─ GrpcDeadlineInterceptor.java
│  ├─ service/
│  │  ├─ TransferService.java
│  │  ├─ DepositService.java
│  │  ├─ WithdrawService.java
│  │  ├─ IdempotencyService.java             # Redis
│  │  └─ fraud/FraudRuleEngine.java          # velocity, limit, geo
│  ├─ domain/                                # @Entity
│  │  ├─ Transaction.java
│  │  ├─ FraudAlert.java
│  │  └─ OutboxEvent.java
│  ├─ repository/
│  │  ├─ TransactionRepository.java
│  │  ├─ FraudAlertRepository.java
│  │  └─ OutboxEventRepository.java
│  ├─ event/
│  │  ├─ OutboxPublisher.java                # scheduler đẩy Kafka
│  │  └─ PaymentSucceededConsumer.java       # nghe payment.succeeded
│  ├─ dto/
│  │  ├─ request/TransferRequest.java
│  │  └─ response/TransferResponse.java
│  ├─ mapper/
│  │  └─ TransactionMapper.java              # MapStruct
│  ├─ exception/
│  │  └─ InsufficientBalanceException.java
│  └─ util/
├─ src/main/resources/
│  ├─ application.yml
│  ├─ application-local.yml
│  ├─ application-docker.yml
│  └─ logback-spring.xml                     # log JSON cho Loki
└─ src/test/
   ├─ java/com/fintech/wallet/txengine/
   │  ├─ service/TransferServiceTest.java     # unit (surefire)
   │  ├─ service/IdempotencyIT.java           # integration (failsafe, Testcontainers)
   │  └─ support/TestcontainersConfig.java
   └─ resources/application-test.yml
```

**Luật phân lớp (không phá):**
```
controller  →  service  →  repository  →  domain
     ↓            ↓
    dto         event / grpc
```
- `controller`: chỉ nhận request, validate, gọi service — **không** chứa logic tiền.
- `service`: toàn bộ nghiệp vụ + transaction boundary (`@Transactional`).
- `repository`: chỉ truy cập DB.
- `domain`: entity JPA, không rò rỉ ra ngoài `controller` (dùng `mapper`).
- `config`, `exception`, `util`: hạ tầng, không gọi ngược lên `service`.

### 3.6. Quy ước test
| Loại | Hậu tố | Plugin | Ghi chú |
|---|---|---|---|
| Unit | `*Test.java` | `surefire` | Không cần Spring context, chạy trong CI mọi commit |
| Integration | `*IT.java` | `failsafe` | Testcontainers (Postgres/Kafka/Redis), chạy ở giai đoạn verify |
| Test support | `support/` | — | Cấu hình Testcontainers dùng chung |

### 3.7. Profile cấu hình
| Profile | Khi nào | Nguồn cấu hình |
|---|---|---|
| `local` | Dev trên máy | `docker compose` port localhost |
| `docker` | Chạy trong Compose | host = tên service (`postgres`, `kafka`, `redis`) |
| `prod` | Trên VPS | `.env` bơm qua biến môi trường, **không** commit |

Bí mật **không** nằm trong `application.yml`; dùng `${DB_PASSWORD}` trỏ tới biến môi trường.

---

## 4. APP — REACT NATIVE (EXPO)

```
app/
├─ app.json / app.config.ts
├─ package.json / tsconfig.json
├─ .env.example                 # EXPO_PUBLIC_API_BASE_URL
└─ src/
   ├─ app/                      # Expo Router: (auth)/, (tabs)/
   │  ├─ (auth)/login.tsx
   │  ├─ (tabs)/index.tsx       # số dư, danh sách ví
   │  ├─ (tabs)/transfer.tsx
   │  ├─ (tabs)/history.tsx
   │  └─ _layout.tsx
   ├─ api/
   │  ├─ client.ts              # fetch wrapper + gắn header
   │  ├─ hmac.ts                # ký HMAC-SHA256 (crypto-js)
   │  └─ endpoints.ts           # transfer, deposit, balance...
   ├─ components/
   ├─ hooks/                    # useBalance, useTransfer (TanStack Query)
   ├─ store/                    # Zustand: auth, wallet
   └─ utils/
```
- Folder `app/` hiện **trống** → khởi tạo bằng `npx create-expo-app`.
- Client **phải** ký HMAC (doc 04 mục 0.3) và gửi `X-Idempotency-Key` cho API động tiền.

---

## 5. INFRA

```
infra/
├─ docker/
│  ├─ docker-compose.yml            # nền: postgres, redis, kafka, prometheus, grafana, loki, alertmanager
│  ├─ docker-compose.app.yml        # 7 service backend + nginx
│  ├─ docker-compose.prod.yml       # override cho VPS (blue/green slot, resource limit)
│  └─ .env.example
├─ nginx/
│  ├─ nginx.conf
│  └─ conf.d/
│     ├─ api.conf                   # REST API, TLS, HTTP/2, rate limit biên
│     └─ grpc.conf                  # grpc_pass → account-ledger-service:9090
├─ monitoring/
│  ├─ prometheus/
│  │  ├─ prometheus.yml
│  │  └─ alert-rules.yml            # ledger_imbalance, error_rate, service down
│  ├─ alertmanager/alertmanager.yml # route → Telegram/Email
│  ├─ grafana/
│  │  ├─ provisioning/              # datasource + dashboard provider
│  │  └─ dashboards/*.json          # dashboard tài chính + hạ tầng
│  ├─ loki/loki-config.yml
│  └─ promtail/promtail-config.yml
└─ scripts/
   ├─ deploy.sh                     # deploy blue/green
   ├─ rollback.sh
   ├─ backup-db.sh                  # pg_dump định kỳ
   └─ seed.sh                       # seed tài khoản hệ thống (doc 03 mục 9)
```
- Java image build bằng **Jib** → **không có Dockerfile** trong `backend/`.
- Postgres tách **1 database cho mỗi service** (init script tạo `tx_db`, `ledger_db`, ...).

---

## 6. CI/CD

```
.github/workflows/
├─ backend-ci.yml      # push/PR: mvn -B verify (unit + integration) → jacoco → trivy
├─ backend-cd.yml      # main: jib build → GHCR → deploy VPS (blue/green) → smoke test
└─ app-ci.yml          # (tuỳ chọn) lint + typecheck Expo
```

---

## 7. CÂY THƯ MỤC ĐẦY ĐỦ (THAM CHIẾU NHANH)

```
FinTech_Wallet_Payment/
├─ app/ ........................................ React Native (Expo)
│  └─ src/{app,api,components,hooks,store,utils}
├─ backend/ .................................... Maven multi-module
│  ├─ pom.xml (parent)
│  ├─ proto/ ................................... ledger.proto, common.proto
│  ├─ common/ .................................. dto, error, event, hmac, security, constant
│  ├─ eureka-server/
│  ├─ api-gateway/
│  ├─ transaction-engine/
│  ├─ account-ledger-service/
│  ├─ payment-gateway-service/
│  ├─ notification-service/
│  └─ audit-service/
├─ infra/
│  ├─ docker/ .................................. compose + .env.example
│  ├─ nginx/ ................................... api.conf, grpc.conf
│  ├─ monitoring/ .............................. prometheus, alertmanager, grafana, loki, promtail
│  └─ scripts/ ................................. deploy, rollback, backup, seed
├─ .github/workflows/ .......................... backend-ci, backend-cd, app-ci
├─ docs/ ....................................... 01..06
└─ README.MD
```

---

## 8. LỆNH MAVEN THƯỜNG DÙNG

```bash
cd backend

# Build toàn bộ + chạy unit & integration test
./mvnw clean verify

# Chỉ build 1 service (kèm module nó phụ thuộc: -am)
./mvnw -pl transaction-engine -am clean package

# Chạy 1 service ở local (cần infra đang chạy)
./mvnw -pl transaction-engine -am spring-boot:run -Dspring-boot.run.profiles=local

# Bỏ qua test khi build nhanh
./mvnw -pl api-gateway -am package -DskipTests

# Build & push image lên GHCR
./mvnw -pl account-ledger-service -am jib:build \
  -Djib.to.image=ghcr.io/<org>/fintech-wallet-ledger-service:1.0.0

# Build image vào Docker daemon local (không push)
./mvnw -pl account-ledger-service -am jib:dockerBuild

# Chỉ chạy integration test
./mvnw -pl transaction-engine verify -Dit.test=IdempotencyIT
```

| Lệnh | Ý nghĩa |
|---|---|
| `-pl <module>` | Chỉ định module cần build |
| `-am` | Build luôn các module mà nó phụ thuộc (`proto`, `common`) |
| `verify` | Chạy đủ: compile → unit test → integration test → package |
| `-DskipTests` | Bỏ test (chỉ dùng khi build nhanh, **không** dùng trong CI) |

---

## 9. QUY TẮC THÊM MỘT SERVICE MỚI (CHECKLIST)

1. Tạo thư mục `backend/<tên-service>/` + `pom.xml` (kế thừa parent, `<artifactId>` trùng tên).
2. Thêm `<module>` vào `modules` của parent POM → giữ **thứ tự** sau `common`.
3. Tạo package `com.fintech.wallet.<tên_ngắn>` + class `*Application` với `@EnableDiscoveryClient`.
4. Thêm `application.yml` + profile `local`/`docker`.
5. Đăng ký route ở `api-gateway` (nếu client gọi được) + thêm service vào `docker-compose.app.yml`.
6. Thêm scrape target ở `infra/monitoring/prometheus/prometheus.yml`.
7. Thêm health check vào `deploy.sh` / smoke test.
8. Cập nhật tài liệu `02-service-breakdown.md`.

---

## 10. BẢN ĐỒ PORT & CẤU HÌNH

| Service | HTTP | gRPC | DB | Biến môi trường chính |
|---|---|---|---|---|
| `eureka-server` | 8761 | — | — | — |
| `api-gateway` | 8080 | — | Redis | `REDIS_HOST`, `EUREKA_URI`, `HMAC_SECRET_*` |
| `transaction-engine` | 8081 | — | `tx_db` | `DB_URL`, `REDIS_HOST`, `KAFKA_BOOTSTRAP`, `LEDGER_GRPC_HOST` |
| `account-ledger-service` | 8082 | **9090** | `ledger_db` | `DB_URL`, `GRPC_PORT` |
| `payment-gateway-service` | 8083 | — | `payment_db` | `DB_URL`, `KAFKA_BOOTSTRAP`, `PROVIDER_*` |
| `notification-service` | 8084 | — | `notification_db` | `DB_URL`, `KAFKA_BOOTSTRAP`, `SMTP_*` |
| `audit-service` | 8085 | — | `audit_db` | `DB_URL`, `KAFKA_BOOTSTRAP` |

---

## 11. CÒN LẠI CẦN CHỐT

1. Package manager cho `app/` (mặc định: **npm**).
2. Tổ chức GitHub org/registry cho GHCR.
3. Có tách `docker-compose.app.yml` khỏi base không (mặc định: **tách**).

**Tài liệu tiếp theo:** `06-cicd-deployment.md` → `07-monitoring.md`.
