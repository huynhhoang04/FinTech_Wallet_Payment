# 02 — CHIA MICROSERVICE & DEPENDENCY CHI TIẾT

> Tài liệu này chốt **danh sách service**, **nhiệm vụ từng service**, và **toàn bộ dependency** (Spring starter + thư viện ngoài + Maven plugin) của mỗi service.
> Nền tảng: Java 21 LTS + Spring Boot 3.5.x + Spring Cloud 2025.0.x (release train tương thích Boot 3.5).
> Version cụ thể của từng dependency do **parent POM quản lý tập trung** (BOM) — service con **không tự khai version**.

---

## 0. TỔNG QUAN 7 SERVICE

| # | Service | Loại | Port | Store riêng | Giao tiếp |
|---|---|---|---|---|---|
| 1 | `eureka-server` | Infra / Discovery | 8761 | — | HTTP (registry) |
| 2 | `api-gateway` | Edge | 8080 | Redis | REST vào, `lb://` ra Eureka |
| 3 | `transaction-engine` | Core | 8081 | `tx_db` + Redis | REST vào, gRPC ra, Kafka ra |
| 4 | `account-ledger-service` | Core | 8082 + gRPC 9090 | `ledger_db` | gRPC vào, REST (admin) ra |
| 5 | `payment-gateway-service` | Integration | 8083 | `payment_db` + Redis | REST vào, Kafka ra/vào, HTTP ra bank |
| 6 | `notification-service` | Consumer | 8084 | `notification_db` | Kafka vào, SMTP ra |
| 7 | `audit-service` | Consumer / Batch | 8085 | `audit_db` | Kafka vào, Prometheus ra |

**Module dùng chung:** `common` (không phải service) — chứa DTO, error model, HMAC util, hằng số Kafka topic, enum trạng thái.
**Module proto:** `proto` — chứa `*.proto`, build sinh Java stub.

**Fraud Detection:** là **module rule engine nằm trong `transaction-engine`** (xem mục 3.4), không tách service riêng — để tiết kiệm RAM và thời gian. Có thể tách ra service riêng sau nếu còn thời gian.

### 0.1. Port & hạ tầng nền
| Hạ tầng | Port |
|---|---|
| Nginx (edge, TLS) | 443 / 80 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka (KRaft) | 9092 |
| Prometheus | 9090* (đặt 9091 để tránh trùng gRPC) |
| Grafana | 3000 |
| Loki | 3100 |

### 0.2. Kafka topic
| Topic | Producer | Consumer |
|---|---|---|
| `tx.completed` | transaction-engine | notification, audit |
| `payment.webhook.received` | payment-gateway | audit |
| `payment.succeeded` | payment-gateway | transaction-engine, notification, audit |
| `risk.alert` | transaction-engine (fraud) | notification, audit |
| `audit.event` | tất cả | audit |

---

## 1. PLUGIN & DEPENDENCY DÙNG CHUNG (PARENT POM)

Khai báo **một lần** ở `backend/pom.xml`, mọi service kế thừa.

### 1.1. Parent & BOM
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.x</version>
</parent>
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>2025.0.x</version>
      <type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>net.devh</groupId>
      <artifactId>grpc-spring-boot-starter</artifactId>
      <version>(do BOM quản lý)</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### 1.2. Maven plugin dùng chung
| Plugin | Mục đích |
|---|---|
| `maven-compiler-plugin` | Java 21; annotation processor theo **đúng thứ tự**: Lombok → MapStruct → lombok-mapstruct-binding |
| `maven-surefire-plugin` | Chạy unit test (`*Test.java`) |
| `maven-failsafe-plugin` | Chạy integration test (`*IT.java`, dùng Testcontainers) |
| `jacoco-maven-plugin` | Đo coverage, xuất report cho CI |
| `spring-boot-maven-plugin` | `repackage` thành jar chạy được |
| `jib-maven-plugin` | Build image OCI → GHCR, không cần Dockerfile |
| `spotless-maven-plugin` (tuỳ chọn) | Format code, chặn CI nếu lệch format |
| `protobuf-maven-plugin` | **Chỉ ở module `proto`** — sinh Java từ `.proto` |
| — (không migration tool) | Schema do **JPA/Hibernate `ddl-auto=update`** sinh ra (xem doc 03) |

### 1.3. Thư viện dùng chung (khai báo ở `common`)
- `lombok`
- `mapstruct` + `mapstruct-processor`
- `spring-boot-starter-validation`
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (verify JWT / sinh token)
- `slf4j` (qua Spring Boot), `logstash-logback-encoder` (log JSON cho Loki)
- `micrometer-tracing-bridge-otel` (tuỳ chọn, cho tracing)

---

## 2. `eureka-server` — SERVICE DISCOVERY

### 2.1. Nhiệm vụ
- **Làm:** Đăng ký/khám phá service. Mọi service khác đăng ký với tên logic (vd `LEDGER-SERVICE`); API Gateway gọi bằng `lb://ledger-service` thay vì hardcode IP/port.
- **Không làm:** Không chứa logic nghiệp vụ, không DB, không nhận traffic người dùng.

### 2.2. Spring dependency
| Dependency | Vì sao |
|---|---|
| `spring-cloud-starter-netflix-eureka-server` | Chạy registry |
| `spring-boot-starter-actuator` | `/health`, `/actuator` cho monitoring |
| `spring-boot-starter-security` | Basic auth bảo vệ Eureka dashboard |
| `micrometer-registry-prometheus` | Metrics |

### 2.3. Thư viện ngoài
- `lombok`

### 2.4. Maven plugin
`maven-compiler-plugin`, `spring-boot-maven-plugin`, `jib-maven-plugin`, `jacoco-maven-plugin`, `maven-surefire-plugin`

### 2.5. Cấu hình chính
```yaml
server.port: 8761
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    enable-self-preservation: true      # tránh evict oan khi mạng chập chờn
```
Annotation: `@EnableEurekaServer`. Không dùng `spring-cloud-starter-netflix-eureka-client` ở service này.

---

## 3. `api-gateway` — EDGE / SECURITY PROXY

### 3.1. Nhiệm vụ
- **Làm:**
  - Điểm vào duy nhất cho app và merchant (sau Nginx).
  - **Verify HMAC-SHA256** trên mọi request tài chính (custom `GlobalFilter`, so sánh `MessageDigest.isEqual` để chống timing attack).
  - **Verify JWT** cho user đã đăng nhập; chặn request thiếu/sai chữ ký → `401`.
  - **Rate limiting** theo IP/user bằng Redis (`RequestRateLimiter`).
  - Routing tới service qua Eureka (`lb://`), thêm **circuit breaker** chống dây chuyền sập.
  - CORS, gắn `X-Request-Id` / `traceId` để tra log xuyên service.
- **Không làm:** Không truy cập DB, không xử lý nghiệp vụ, không giữ state.

### 3.2. Spring dependency
| Dependency | Vì sao |
|---|---|
| `spring-cloud-starter-gateway` | Gateway (WebFlux, reactive) |
| `spring-cloud-starter-netflix-eureka-client` | Tra cứu instance qua `lb://` |
| `spring-cloud-starter-loadbalancer` | Cân bằng tải client-side (đi kèm eureka-client) |
| `spring-cloud-starter-circuitbreaker-reactor-resilience4j` | Circuit breaker cho route |
| `spring-boot-starter-data-redis-reactive` | **Bắt buộc** cho `RequestRateLimiter` |
| `spring-boot-starter-security` | Security filter chain |
| `spring-boot-starter-oauth2-resource-server` | Decode/verify JWT theo chuẩn (thay cho tự viết) |
| `spring-boot-starter-actuator` | Health + gateway routes |
| `micrometer-registry-prometheus` | Metrics |

### 3.3. Thư viện ngoài
`lombok`, `commons-codec` (HMAC helper, dùng từ module `common`)

### 3.4. Maven plugin
`maven-compiler-plugin`, `spring-boot-maven-plugin`, `jib-maven-plugin`, `jacoco-maven-plugin`, `surefire`, `failsafe`

### 3.5. Cấu hình chính
```yaml
server.port: 8080
spring:
  cloud:
    gateway:
      routes:
        - id: transaction-engine
          uri: lb://transaction-engine
          predicates: [ Path=/api/v1/transactions/** ]
          filters:
            - name: RequestRateLimiter
              args: { redis-rate-limiter.replenishRate: 20, redis-rate-limiter.burstCapacity: 40 }
            - name: CircuitBreaker
              args: { name: txCB }
        - id: payment-gateway
          uri: lb://payment-gateway-service
          predicates: [ Path=/api/v1/payments/**, Path=/webhooks/** ]
```
- Filter tuỳ biến: `HmacAuthFilter` (order cao, chạy trước routing), `RequestIdFilter`.

---

## 4. `transaction-engine` — CORE ENGINE

### 4.1. Nhiệm vụ
- **Làm:**
  - Nhận lệnh **chuyển tiền / nạp / rút** từ Gateway (REST).
  - **Idempotency**: check/lưu `X-Idempotency-Key` trong Redis (TTL 24h), nếu trùng trả lại kết quả cũ, **không trừ tiền lần 2**.
  - **Distributed lock** bằng Redisson theo cặp tài khoản, **sắp xếp theo `accountId` tăng dần** để chống deadlock (A→B và B→A đồng thời).
  - Gọi **gRPC đồng bộ** sang `account-ledger-service` để hạch toán.
  - **Kiểm tra gian lận (rule engine nhúng)** trước khi cho giao dịch đi: velocity (N giao dịch/phút), hạn mức, IP/thiết bị lạ → có thể chặn hoặc chỉ bắn `risk.alert`.
  - Lưu `transactions` (state machine: `PENDING → PROCESSING → SUCCESS/FAILED`).
  - Publish `tx.completed` / `risk.alert` lên Kafka.
- **Không làm:** Không tự tính số dư, không ghi `ledger_entries` (đó là việc của Ledger), không gọi ngân hàng.

### 4.2. Spring dependency
| Dependency | Vì sao |
|---|---|
| `spring-boot-starter-web` | REST API nhận lệnh |
| `net.devh:grpc-client-spring-boot-starter` | gRPC client → gọi Ledger |
| `spring-kafka` | Publish event |
| `spring-boot-starter-data-redis` | Idempotency key |
| `redisson-spring-boot-starter` | Distributed lock (watchdog tự gia hạn) |
| `spring-boot-starter-data-jpa` | Bảng `transactions` |
| `spring-boot-starter-jdbc` | Query SQL cho luồng tiền quan trọng |
| `postgresql` (driver) | Kết nối DB |
| `spring-cloud-starter-netflix-eureka-client` | Đăng ký discovery |
| `spring-cloud-starter-openfeign` | Gọi REST service khác (nếu cần) |
| `resilience4j-spring-boot3` | Circuit breaker/retry/timeout khi gọi Ledger |
| `spring-boot-starter-validation` | Validate request |
| `spring-boot-starter-actuator`, `micrometer-registry-prometheus` | Metrics |
| `spring-boot-starter-security` | Bảo vệ endpoint nội bộ |

### 4.3. Thư viện ngoài
`lombok`, `mapstruct`, `testcontainers` (`postgresql`, `kafka`, `junit-jupiter`)

### 4.4. Maven plugin
`maven-compiler-plugin`, `spring-boot-maven-plugin`, `jib-maven-plugin`, `jacoco-maven-plugin`, `surefire`, `failsafe`

### 4.5. Giao tiếp
- **Vào:** REST `/api/v1/transactions/transfer`, `/deposit`, `/withdraw` (từ Gateway); Kafka `payment.succeeded` (từ payment-gateway).
- **Ra:** gRPC → `ledger-service`; Kafka → `tx.completed`.

---

## 5. `account-ledger-service` — SỔ CÁI KẾ TOÁN KÉP

### 5.1. Nhiệm vụ
- **Làm:**
  - Sở hữu `accounts` và `ledger_entries` (nguồn chân lý duy nhất về tiền).
  - **gRPC server**: `PostTransfer`, `Deposit`, `GetBalance`, `GetStatement`.
  - **Bất biến kế toán kép**: một giao dịch tạo ≥ 2 dòng, tổng Debit + Credit **= 0**; ràng buộc ở tầng DB (constraint + trigger) và tầng code.
  - Số dư = tổng `ledger_entries` (hoặc snapshot có kiểm chứng), **không** `UPDATE balance = balance - X` trực tiếp.
  - Khoá tài khoản bằng `SELECT ... FOR UPDATE` theo **thứ tự id tăng dần**; dùng isolation `REPEATABLE READ` (nâng lên `SERIALIZABLE` khi cần).
  - Endpoint admin (REST, chỉ nội bộ): đối chiếu, health số dư.
- **Không làm:** Không nhận request người dùng trực tiếp, không biết về payment/hóa đơn, không publish thông báo.

### 5.2. Spring dependency
| Dependency | Vì sao |
|---|---|
| `net.devh:grpc-server-spring-boot-starter` | gRPC server |
| `grpc-protobuf`, `grpc-stub` | Runtime proto (do plugin sinh) |
| `spring-boot-starter-data-jpa` | CRUD account |
| `spring-boot-starter-jdbc` | **JdbcTemplate cho ghi ledger** (kiểm soát SQL/lock) |
| `postgresql` (driver) | Kết nối DB |
| `spring-cloud-starter-netflix-eureka-client` | Đăng ký discovery cho gRPC |
| `spring-boot-starter-web` | Endpoint admin nội bộ |
| `spring-boot-starter-validation` | Validate |
| `spring-boot-starter-actuator`, `micrometer-registry-prometheus` | Metrics |
| `spring-boot-starter-security` | Chỉ cho phép nội bộ gọi admin API |

### 5.3. Thư viện ngoài
`lombok`, `mapstruct`, `testcontainers` (`postgresql`, `junit-jupiter`)

### 5.4. Maven plugin
`maven-compiler-plugin`, **`protobuf-maven-plugin`** (sinh stub từ `proto`), `spring-boot-maven-plugin`, `jib-maven-plugin`, `jacoco-maven-plugin`, `surefire`, `failsafe`

### 5.5. Cấu hình chính
```yaml
server.port: 8082
grpc.server.port: 9090
```

---

## 6. `payment-gateway-service` — TÍCH HỢP CỔNG THANH TOÁN

### 6.1. Nhiệm vụ
- **Làm:**
  - Tạo **payment intent** (nạp tiền vào ví), trả URL/QR cho app (VNPay/Stripe/VietQR sandbox).
  - Nhận **webhook callback** từ ngân hàng: **verify chữ ký** của đối tác, **chống trùng webhook** (Redis + trạng thái idempotent).
  - Publish `payment.webhook.received` và `payment.succeeded` lên Kafka → transaction-engine cộng tiền.
  - Gọi API ngân hàng ra ngoài với **retry + circuit breaker**.
- **Không làm:** Không cộng tiền trực tiếp vào số dư (chỉ bắn event), không tự ghi ledger.

### 6.2. Spring dependency
| Dependency | Vì sao |
|---|---|
| `spring-boot-starter-web` | Nhận webhook + API tạo intent |
| `spring-kafka` | Publish/consume event |
| `spring-boot-starter-data-jpa` | Bảng `payment_intents`, `webhook_logs` |
| `postgresql` (driver) | Kết nối DB |
| `spring-boot-starter-data-redis` | Chống xử lý webhook trùng |
| `spring-cloud-starter-netflix-eureka-client` | Discovery |
| `spring-cloud-starter-openfeign` | Gọi HTTP API ngân hàng/cổng |
| `resilience4j-spring-boot3` | `@Retry`, `@CircuitBreaker` khi gọi bank |
| `spring-boot-starter-validation` | Validate webhook payload |
| `spring-boot-starter-actuator`, `micrometer-registry-prometheus` | Metrics |
| `spring-boot-starter-security` | Bảo vệ endpoint tạo intent |

### 6.3. Thư viện ngoài
`lombok`, `mapstruct`, `testcontainers` (`postgresql`, `kafka`, `junit-jupiter`), SDK cổng thanh toán (nếu dùng SDK chính chủ; bằng không thì `RestClient`/Feign)

### 6.4. Maven plugin
`maven-compiler-plugin`, `spring-boot-maven-plugin`, `jib-maven-plugin`, `jacoco-maven-plugin`, `surefire`, `failsafe`

---

## 7. `notification-service` — THÔNG BÁO & AUDIT NHẸ

### 7.1. Nhiệm vụ
- **Làm:**
  - Consumer Kafka: nghe `tx.completed`, `payment.succeeded`, `risk.alert`.
  - Gửi **email biến động số dư** (SMTP, template Thymeleaf), push notification (mock hoặc Firebase).
  - Lưu `notification_logs` để tra cứu/kiểm toán việc đã gửi.
  - Retry khi gửi fail (Spring Kafka `ErrorHandler` + dead-letter topic).
- **Không làm:** Không đọc/ghi số dư, không đụng `ledger`.

### 7.2. Spring dependency
| Dependency | Vì sao |
|---|---|
| `spring-kafka` | Consumer event |
| `spring-boot-starter-mail` | Gửi email SMTP |
| `spring-boot-starter-thymeleaf` | Template nội dung email |
| `spring-boot-starter-data-jpa` | `notification_logs` |
| `postgresql` (driver) | Kết nối DB |
| `spring-cloud-starter-netflix-eureka-client` | Discovery + health |
| `spring-boot-starter-web` | Health/actuator endpoint |
| `spring-boot-starter-actuator`, `micrometer-registry-prometheus` | Metrics |
| `spring-boot-starter-validation` | Validate payload event |

### 7.3. Thư viện ngoài
`lombok`, `mapstruct`, `testcontainers` (`postgresql`, `kafka`, `junit-jupiter`), `firebase-admin` (tuỳ chọn cho push)

### 7.4. Maven plugin
`maven-compiler-plugin`, `spring-boot-maven-plugin`, `jib-maven-plugin`, `jacoco-maven-plugin`, `surefire`, `failsafe`

---

## 8. `audit-service` — AUDIT LOG BẤT BIẾN & ĐỐI CHIẾU

### 8.1. Nhiệm vụ
- **Làm:**
  - Consumer Kafka: nghe **mọi** event → ghi **immutable audit log** (append-only; mỗi bản ghi có `hash` liên kết bản ghi trước → chuỗi hash phát hiện sửa đổi).
  - **Job đối chiếu định kỳ**: kiểm tra tổng Debit + Credit = 0 trên toàn sổ; nếu lệch → **bắn alert** (log ERROR + metric + webhook Telegram).
  - Expose **custom metric** cho Prometheus: `ledger_imbalance`, `reconciliation_last_success_timestamp`.
  - Lưu báo cáo đối soát theo ngày.
- **Không làm:** Không can thiệp giao dịch, không sửa dữ liệu (chỉ ghi thêm).

### 8.2. Spring dependency
| Dependency | Vì sao |
|---|---|
| `spring-kafka` | Consumer toàn bộ event |
| `spring-boot-starter-batch` | Job đối chiếu định kỳ (chunk, có report) |
| `spring-boot-starter-data-jpa` | `audit_logs`, `reconciliation_reports` |
| `spring-boot-starter-jdbc` | Query tổng hợp Debit/Credit |
| `postgresql` (driver) | Kết nối DB |
| `spring-cloud-starter-netflix-eureka-client` | Discovery |
| `spring-boot-starter-web` | Health/actuator + endpoint xem report |
| `spring-boot-starter-actuator`, `micrometer-registry-prometheus` | **Custom metric cảnh báo lệch sổ** |
| `spring-boot-starter-validation` | Validate |

### 8.3. Thư viện ngoài
`lombok`, `mapstruct`, `testcontainers` (`postgresql`, `kafka`, `junit-jupiter`)

### 8.4. Maven plugin
`maven-compiler-plugin`, `spring-boot-maven-plugin`, `jib-maven-plugin`, `jacoco-maven-plugin`, `surefire`, `failsafe`

### 8.5. Ghi chú
Đối chiếu có thể dùng `@Scheduled` + `JdbcTemplate` nếu Spring Batch quá nặng cho 2 tuần. Batch là lựa chọn "đẹp" hơn cho phần DevOps (job có trạng thái, có report) — ưu tiên Batch nhưng hạ xuống `@Scheduled` nếu hết thời gian.

---

## 9. MODULE DÙNG CHUNG

### 9.1. `common`
- **Nhiệm vụ:** Chứa thứ nhiều service xài chung để không lặp code.
- Nội dung: DTO/event model, error model + `@ControllerAdvice` chung, `HmacUtils`, hằng số Kafka topic, enum trạng thái giao dịch, `RequestIdFilter`.
- Dependency: `spring-boot-starter-validation`, `jjwt`, `lombok`, `mapstruct`, `logstash-logback-encoder` (không kéo web/jpa để giữ module nhẹ).
- Plugin: `maven-compiler-plugin`, `jacoco-maven-plugin`.

### 9.2. `proto`
- **Nhiệm vụ:** Nguồn chân lý cho contract gRPC.
- Nội dung: `ledger.proto` (`PostTransfer`, `Deposit`, `GetBalance`, `GetStatement`), `common.proto` (Money, Error).
- Plugin: **`protobuf-maven-plugin`** + `os-maven-plugin`, sinh `grpc-java` + `grpc-stub`. Code generated **không commit**.

---

## 10. MA TRẬN PHỤ THUỘC TÓM TẮT

| Dependency | eureka | gateway | tx-engine | ledger | payment | notif | audit |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| eureka-server | ● | | | | | | |
| eureka-client | | ● | ● | ● | ● | ● | ● |
| gateway (WebFlux) | | ● | | | | | |
| starter-web (MVC) | | | ● | ● | ● | ● | ● |
| gRPC server | | | | ● | | | |
| gRPC client | | | ● | | | | |
| spring-kafka | | | ● | | ● | ● | ● |
| Redis | | ●(reactive) | ● | | ● | | |
| Redisson | | | ● | | | | |
| JPA | | | ● | ● | ● | ● | ● |
| JdbcTemplate | | | ● | ● | | | ● |
| JPA schema (`ddl-auto`) | | | ● | ● | ● | ● | ● |
| PostgreSQL driver | | | ● | ● | ● | ● | ● |
| OpenFeign | | | ● | | ● | | |
| Resilience4j | | ●(reactor) | ● | | ● | | |
| Spring Batch | | | | | | | ● |
| Mail + Thymeleaf | | | | | | ● | |
| Actuator + Micrometer | ● | ● | ● | ● | ● | ● | ● |
| Security | ● | ● | ● | ● | ● | | |

---

## 11. LUỒNG DEMO CHÍNH (ĐỂ KIỂM CHỨNG CHIA SERVICE)

**Luồng chuyển tiền (đồng bộ + bất đồng bộ):**
```
App → Nginx → API Gateway (verify HMAC + JWT + rate limit)
    → transaction-engine (check Idempotency Redis → Redisson lock → fraud rule)
    → gRPC → account-ledger-service (ghi ledger_entries: Debit + Credit, tổng = 0)
    → transaction-engine publish Kafka tx.completed
        → notification-service (email biến động số dư)
        → audit-service (ghi audit + đối chiếu)
```

**Luồng nạp tiền qua ngân hàng:**
```
App → Gateway → payment-gateway-service (tạo payment intent + URL)
Ngân hàng → webhook → payment-gateway-service (verify chữ ký + chống trùng)
    → Kafka payment.succeeded → transaction-engine → gRPC ledger (Credit ví)
```

---

## 12. CÒN LẠI CẦN CHỐT

1. Cổng thanh toán thật dùng để tích hợp sandbox (VNPay / Stripe / VietQR).
2. Fraud Detection có tách service riêng không (mặc định: **không**, nằm trong tx-engine).
3. Có dùng Spring Cloud Config Server không (mặc định: **không**, dùng `.env` + `application-*.yml`).

**Tài liệu tiếp theo:** `03-database-design.md` → `04-api-contract.md` → `05-cicd-deployment.md` → `06-monitoring.md`.
