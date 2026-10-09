# 07 — KIỂM TOÁN LỖ HỔNG (SECURITY & FINANCIAL INTEGRITY)

> **Ngày kiểm toán:** 2026-10-03 · **Phạm vi:** toàn bộ backend (`backend/`), đối chiếu với `docs/01` → `docs/06`, `AGENTS.md`.
> **Phương pháp:** đọc mã nguồn tĩnh toàn bộ 7 service + module `common`/`proto`, đối chiếu hợp đồng API/proto và các invariant tiền tệ trong tài liệu.
> **Lưu ý phân loại:** tài liệu này gộp cả **lỗ hổng bảo mật (SEC-*)** lẫn **lỗi toàn vẹn tài chính (FIN-*)**, vì trong hệ thống ví cả hai đều có thể dẫn tới mất tiền. Mức độ theo CVSS-tinh thần: **Critical / High / Medium / Low**.
>
> Hai quy tắc tối thượng của dự án (`AGENTS.md` §3) được dùng làm chuẩn đánh giá:
> **(1) Tiền không được tạo, mất, hoặc chi hai lần.** **(2) Mọi đường ghi tiền phải transactional và idempotent.**

---

## 0. BẢNG TỔNG HỢP

> **Cập nhật 2026-10-05:** cột **Trạng thái** phản ánh kết quả khắc phục. Chi tiết ở §6.

| ID | Mức | Lỗ hổng | Vị trí chính | Trạng thái |
|---|---|---|---|---|
| SEC-01 | 🔴 Critical | `/internal/**` để `permitAll` → ép cộng tiền qua `simulate-callback` | `payment-gateway-service/.../config/SecurityConfig.java` | ✅ Đã vá |
| FIN-01 | 🔴 Critical | `transaction_id` sinh mới mỗi lần gọi → retry sau timeout trừ tiền 2 lần | `transaction-engine/.../service/TransactionService.java` | ✅ Đã vá |
| FIN-02 | 🔴 Critical | Double-post khi cùng `transaction_id` chạy song song (thiếu UNIQUE constraint) | `account-ledger-service/.../service/LedgerPostingService.java` | ✅ Đã vá |
| SEC-02 | 🔴 Critical | Dedupe webhook chạy **trước** khi verify chữ ký → nuốt callback thật | `payment-gateway-service/.../service/WebhookService.java` | ✅ Đã vá |
| FIN-03 | 🔴 Critical | Luồng nạp tiền chưa khép: `payment.succeeded` không ai cộng ví | `transaction-engine` (thiếu consumer) + `TransactionService.deposit` | ✅ Đã vá |
| SEC-03 | 🟠 High | `notification-service` & `audit-service` không có auth; IDOR qua `userId` | `notification-service`, `audit-service` (không có `SecurityConfig`) | ✅ Đã vá |
| SEC-04 | 🟠 High | IDOR đọc dữ liệu: không kiểm ownership tài khoản/giao dịch | `transaction-engine/.../controller/TransactionController.java` | ✅ Đã vá |
| SEC-05 | 🟠 High | Webhook set intent `SUCCEEDED` bất kể `status`, không đối chiếu amount/currency/provider | `payment-gateway-service/.../service/WebhookRecorder.java` | ✅ Đã vá |
| SEC-06 | 🟠 High | Secret dev commit trong repo; api-gateway không có profile `prod` | `*/src/main/resources/application.yml` | ✅ Đã vá |
| FIN-04 | 🟠 High | `risk.alert` khai báo + consume nhưng **không bao giờ được publish** | `transaction-engine/.../service/FraudRuleEngine.java` | ✅ Đã vá |
| FIN-05 | 🟠 High | Circuit breaker cấu hình nhưng không wire annotation/bean → không bao giờ mở | `transaction-engine/.../application.yml` + `grpc/LedgerClient.java` | ✅ Đã vá |
| FIN-06 | 🟠 High | `otpToken` nhận nhưng không kiểm tra | `transaction-engine/.../dto/TransferRequest.java` | ✅ Đã vá |
| SEC-07 | 🟡 Medium | JSON `metadata`/`details` nối chuỗi → chèn ký tự làm hỏng giao dịch | `LedgerEntryFactory`, `FraudRuleEngine`, `ReconciliationService` | ✅ Đã vá |
| FIN-07 | 🟡 Medium | Redis lock key không khớp giữa các thao tác → mutual exclusion giả | `transaction-engine/.../service/TransactionService.java` | ✅ Đã vá |
| FIN-08 | 🟡 Medium | `OutboxPublisher` không cluster-safe (không ShedLock) → publish trùng | `transaction-engine/.../event/OutboxPublisher.java` | ✅ Đã vá (single-instance) |
| SEC-08 | 🟡 Medium | `audit-service` append chuỗi hash có race → chuỗi đứt vĩnh viễn | `audit-service/.../service/AuditChainService.java` | ✅ Đã vá |
| FIN-09 | 🟡 Medium | `AccountBalance.@Version` bị bypass bởi native UPSERT | `account-ledger-service/.../repository/AccountBalanceRepository.java` | ✅ Đã vá (ghi chú) |
| FIN-10 | 🟡 Medium | Rule VELOCITY đếm cả giao dịch `FAILED`/`PENDING` | `transaction-engine/.../service/FraudRuleEngine.java` | ✅ Đã vá |
| SEC-09 | 🟡 Medium | Race trong dedupe webhook → trả 500 thay vì `duplicate:true` | `payment-gateway-service/.../service/WebhookService.java` | ✅ Đã vá |
| FIN-11 | 🟢 Low | `lockByAccountId` giả định 1 row/tài khoản (đa tiền tệ sẽ lỗi) | `account-ledger-service/.../repository/AccountBalanceRepository.java` | ✅ Đã vá |
| FIN-12 | 🟢 Low | Sinh `account_number` ngẫu nhiên không retry khi trùng | `account-ledger-service/.../service/AuthService.java` | ✅ Đã vá |
| SEC-10 | 🟢 Low | Template email render rỗng (`${model}` không được set) | `notification-service/.../templates/email/notification.html` | ✅ Đã vá |
| FIN-13 | 🟢 Low | Không có job hết hạn `PaymentIntent`; status `FAILED`/`EXPIRED` chết | `payment-gateway-service/.../service/PaymentIntentService.java` | ✅ Đã vá |
| SEC-11 | 🟠 High | IDOR trên `GET /api/v1/payments/intents/{id}` (phát hiện thêm ngoài kiểm toán) | `payment-gateway-service/.../controller/PaymentController.java` | ✅ Đã vá |

---

## 1. LỖ HỔNG CRITICAL

### SEC-01 — `/internal/**` không xác thực, có thể ép cộng tiền

**Vị trí:** `backend/payment-gateway-service/src/main/java/com/fintech/wallet/payment/config/SecurityConfig.java`

```java
private static final String[] PUBLIC_PATHS = { "/actuator/**", "/internal/**", "/webhooks/**", "/error" };
```

**Tác động:** Endpoint `POST /internal/payments/{paymentIntentId}/simulate-callback` tự giả lập callback ngân hàng, ký bằng provider secret, set intent `SUCCEEDED` và bắn `payment.succeeded` → **cộng tiền vào ví**. Vì `/internal/**` `permitAll`, bất kỳ tiến trình nào trong mạng cluster (hoặc ai port-forward được) đều **tạo tiền không cần xác thực**. Cùng vấn đề ở `transaction-engine` và `account-ledger-service`: toàn bộ `/internal/**` (verify-balance, verify-chain, ledger summary) đều public.

**Khuyến nghị:**
- Bỏ `/internal/**` khỏi `PUBLIC_PATHS` ở **cả ba** service.
- Bảo vệ bằng shared-secret header / mTLS / network policy (chỉ service nội bộ gọi được).
- `simulate-callback` chỉ bật khi profile `local/demo` (ví dụ `@Profile("local")` + `@ConditionalOnProperty`), tuyệt đối không có ở `prod`.

**Tham chiếu:** `docs/04` §0.1 ("endpoint `/internal/**` là nội bộ, không expose ra ngoài"), `AGENTS.md` §3.

---

### FIN-01 — `transaction_id` sinh mới mỗi lần gọi → trừ tiền 2 lần khi retry

**Vị trí:** `backend/transaction-engine/src/main/java/com/fintech/wallet/txengine/service/TransactionService.java`

```java
UUID ledgerTransactionId = UUID.randomUUID();   // <-- sinh MỚI mỗi lần gọi
...
LedgerClient.PostingOutcome outcome = ledger.postTransfer(ledgerTransactionId, ...);
return recordSuccess(transaction, outcome.ledgerTransactionId());
```

**Tác động:** Cơ chế chống trùng của ledger dựa hoàn toàn vào `transaction_id` (`docs/04` §4.1: *"UUID do tx-engine sinh, chống trùng khi retry"*). Nhưng engine sinh UUID **mới mỗi lần gọi**. Khi `postTransfer` vượt deadline 3s và bị coi là `UPSTREAM_UNAVAILABLE`, `IdempotencyService.release()` xoá key → client retry → vào nhánh `else`, sinh **UUID khác**. Nếu lần gọi đầu **đã commit** ở ledger nhưng phản hồi bị mất, lần hai **post lại** → **tiền bị trừ hai lần**. Đây là vi phạm trực tiếp quy tắc tối thượng #1.

**Khuyến nghị:**
- Sinh `Transaction` (UUID) **trước** khi gọi ledger và dùng **chính UUID đó** làm `transaction_id` (deterministic theo idempotency key / transaction id).
- Không `release` idempotency key khi lỗi timeout mơ hồ; giữ in-flight cho tới khi đối soát được.

**Tham chiếu:** `docs/04` §4.1; `AGENTS.md` §10 ("không sinh idempotency key thứ hai mỗi lần retry", "không retry `PostTransfer`").

---

### FIN-02 — Double-post khi cùng `transaction_id` chạy song song

**Vị trí:** `backend/account-ledger-service/src/main/java/com/fintech/wallet/ledger/service/LedgerPostingService.java`

```java
Optional<TransferResult> replay = replayTransfer(transactionId, sourceAccountId, targetAccountId);
if (replay.isPresent()) { return replay.get(); }          // check TRƯỚC khi lock
...
LockedAccounts locked = lockActive(List.of(sourceAccountId, targetAccountId));  // lock sau
```

**Tác động:** Dưới `READ_COMMITTED`, hai request cùng `transaction_id` đều thấy "chưa có entry", rồi mới tuần tự hoá trên khoá `FOR UPDATE`. Bên thua **không re-check** `findByTransactionId` sau khi lấy khoá → ghi thêm bộ entry thứ hai và cộng/trừ delta thêm lần nữa. Ở tầng DB **không có chốt chặn**: `ledger_entries` chỉ có index thường `ix_le_tx(transaction_id)`, **không có** `UNIQUE(transaction_id, account_id)`. Test `LedgerIdempotencyIT` chỉ chạy **tuần tự** nên không phát hiện.

**Khuyến nghị:**
- Thêm `UNIQUE(transaction_id, account_id)` trên `ledger_entries` (ràng buộc cứng ở tầng DB — đúng nguyên tắc "chốt chặn ở DB").
- Hoặc/hơn nữa: re-check `replayTransfer` **sau khi** đã lấy khoá.

**Tham chiếu:** `docs/03` §4.2 (invariant `SUM=0` enforce ở service + job đối chiếu); quy tắc tối thượng #2.

---

### SEC-02 — Dedupe webhook chạy trước khi verify chữ ký → nuốt callback thật

**Vị trí:** `backend/payment-gateway-service/src/main/java/com/fintech/wallet/payment/service/WebhookService.java`

```java
if (webhooks.findByProviderAndEventId(provider, event.eventId()).isPresent()
        || !reserve(provider, event.eventId())) {          // <-- reserve TRƯỚC
    return new WebhookResponse(true, event.eventId(), true);
}
boolean signatureValid = HmacUtils.verify(secret(provider), rawBody, signature);  // verify SAU
```

**Tác động:** Kẻ tấn công chỉ cần POST `/webhooks/{provider}` với một `eventId` bất kỳ + chữ ký rác để **chiếm mất slot dedupe** (Redis key TTL 7 ngày + row `signatureValid=false`). Khi ngân hàng thật gửi callback với đúng `eventId` đó, hệ thống coi là `duplicate` → **bỏ qua** → **khách đã trả tiền nhưng ví không được cộng**. Đây là DoS/suppression trên luồng nạp tiền.

**Khuyến nghị:**
- **Verify chữ ký trước**, chỉ `reserve`/ghi log dedupe khi chữ ký hợp lệ.
- Với callback sai chữ ký: trả 4xx và **không** tiêu thụ dedupe key.

**Tham chiếu:** `docs/04` §5.4.

---

### FIN-03 — Luồng nạp tiền chưa khép kín (ví không bao giờ tăng)

**Vị trí:**
- `transaction-engine/.../service/TransactionService.java` — `deposit` chỉ tạo row `PENDING`, **không** gọi ledger.
- `account-ledger-service/.../grpc/LedgerGrpcService.java` — RPC `Deposit` (SUSPENSE → ví) đã implement nhưng **không có caller** (không service nào gọi).
- `payment-gateway-service` publish `payment.succeeded`, nhưng `transaction-engine` **không có consumer** cho topic này (`docs/02` §4.5 yêu cầu phải nghe để cộng ví).

**Tác động:** Chạy hết luồng nạp (tạo intent → webhook → `payment.succeeded`), **không có bước nào cộng số dư**. Toàn bộ `Deposit` RPC + `GetStatement` + luồng DEPOSIT là **dead code**. Đây là mốc gãy **M3 / ngày 7** trong `docs/06` ("Nạp tiền end-to-end → ví tăng đúng số tiền").

**Khuyến nghị:**
- Thêm consumer `payment.succeeded` trong `transaction-engine` (qua outbox/idempotent) → gọi `ledger.deposit(...)` với `transaction_id` deterministic.
- Bổ sung `LedgerClient.deposit(...)` (hiện client chỉ có 5/7 RPC).

**Tham chiếu:** `docs/02` §4.5, §11; `docs/06` M3.

---

## 2. LỖ HỔNG HIGH

### SEC-03 — `notification-service` và `audit-service` không có xác thực; IDOR

**Vị trí:** cả hai service **không có** `SecurityConfig` (notification thậm chí không khai `spring-boot-starter-security`).

**Tác động:**
- `GET /api/v1/notifications?userId=...` tin **query param** thay vì JWT → bất kỳ ai liệt kê được thông báo của mọi người dùng (**IDOR**).
- `GET /api/v1/audit/logs`, `/verify-chain`, `POST /api/v1/reconciliation/run` mở công khai → ai chạm được port 8085 có thể chạy job đối chiếu / đọc audit.
- `/internal/notifications/dead-letters`, `/internal/notifications/test` mở hoàn toàn.

**Khuyến nghị:** thêm Spring Security + JWT resource-server cho cả hai; lấy danh tính từ `CurrentUser.userId()` (như payment-gateway đang làm); chặn `/internal/**` và yêu cầu `ROLE_ADMIN` cho audit/reconciliation.

---

### SEC-04 — IDOR trên các endpoint đọc của transaction-engine

**Vị trí:** `transaction-engine/.../controller/TransactionController.java` và `AccountController.java`

**Tác động:** `GET /transactions/{id}`, `/transactions/{id}/ledger-entries`, `/transactions/{id}/fraud-alerts`, `GET /transactions?accountId=`, `GET /accounts/{id}/balance` nhận id từ path/query và trả dữ liệu **không kiểm tra JWT subject có sở hữu tài khoản**. Một user đã đăng nhập đọc được số dư, giao dịch, ledger entries, fraud alerts của người khác. `CurrentUser` có sẵn nhưng không dùng.

**Khuyến nghị:** kiểm tra quyền sở hữu (owner_user_id của account) trước khi trả dữ liệu; trả `404`/`403` phù hợp.

---

### SEC-05 — Webhook đánh dấu `SUCCEEDED` bất kể trạng thái, thiếu đối chiếu

**Vị trí:** `payment-gateway-service/.../service/WebhookRecorder.java`

```java
if (signatureValid && event.paymentIntentId() != null) {
    intents.findById(event.paymentIntentId()).ifPresentOrElse(intent -> {
        intent.setStatus(PaymentIntent.PaymentStatus.SUCCEEDED);   // bất kể event.status()
        ...
```

**Tác động:** Webhook đã ký nhưng `status=FAILED`/`PENDING` vẫn set intent thành `SUCCEEDED`. Đồng thời **không** so `amount`, `currency`, `provider` của webhook với `PaymentIntent` gốc → callback tham chiếu sai intent/sai số tiền vẫn được chấp nhận.

**Khuyến nghị:** chỉ chuyển `SUCCEEDED` khi `event.status()==SUCCEEDED`; đối chiếu amount/currency/provider khớp intent trước khi cập nhật.

---

### SEC-06 — Secret dev commit trong repo; gateway thiếu profile `prod`

**Vị trí:** nhiều `application.yml` (default value cho `${JWT_SECRET:...}`, `${HMAC_SECRET:dev-only-hmac-secret}`, `dev-{vnpay,stripe,vietqr}-secret`, `eureka-local`).

**Tác động:** Giá trị mặc định nằm trong repo. Nếu môi trường `prod` không set biến, hệ thống chạy bằng secret công khai. `api-gateway` **không có** `application-prod.yml` để buộc override (trong khi payment/notification/audit có).

**Khuyến nghị:** bỏ mọi default secret khỏi YAML (để `${JWT_SECRET}` fail-fast nếu thiếu); thêm `application-prod.yml` cho gateway/eureka/tx-engine/ledger; quét `gitleaks` trong CI.

---

### FIN-04 — `risk.alert` không bao giờ được publish

**Vị trí:** `transaction-engine/.../service/FraudRuleEngine.java` (chỉ ghi `FraudAlert`, không ghi outbox/publish)

**Tác động:** `KafkaTopics.RISK_ALERT` được khai báo và được `notification-service`/`audit-service` consume, nhưng **không producer nào** phát. Người dùng **không bao giờ** nhận cảnh báo khi giao dịch bị chặn gian lận → sai `docs/06` M4 ngày 9 ("ghi `fraud_alerts`, bắn `risk.alert`").

**Khuyến nghị:** khi fraud rule ra `decision=BLOCK/REVIEW`, ghi outbox event `risk.alert` trong cùng transaction với `FraudAlert`.

---

### FIN-05 — Circuit breaker cấu hình nhưng không hoạt động

**Vị trí:** `transaction-engine/.../resources/application.yml` (khai `resilience4j.circuitbreaker.instances.ledger`) + `grpc/LedgerClient.java` (không có `@CircuitBreaker`/`@Retry`/bean nào).

**Tác động:** CB **không bao giờ mở**; chỉ có deadline 3s bảo vệ. DoD `docs/06` M2 ngày 4 ("Ledger chết → 503, circuit breaker mở") không đạt; comment trong `LedgerClient` gây hiểu nhầm là có CB.

**Khuyến nghị:** wire `@CircuitBreaker(name="ledger")` + `@Retry` (chỉ cho read-only) hoặc tạo bean `CircuitBreaker` dùng trực tiếp trong client.

---

### FIN-06 — `otpToken` nhận nhưng không kiểm tra

**Vị trí:** `transaction-engine/.../dto/TransferRequest.java` (`otpToken`) — `TransactionService.transfer` không đọc field này.

**Tác động:** OTP cho giao dịch lớn được mô tả trong `docs/04` §2.5 nhưng **không rule nào enforce**; kẻ tấn công có JWT hợp lệ chuyển tiền dưới hạn mức **không cần OTP**. Field `otpToken` là dead input gây ngộ nhận đã có bảo vệ 2 lớp.

**Khuyến nghị:** hoặc enforce verify OTP theo ngưỡng tiền (gọi ledger OtpService), hoặc xoá field để tránh hiểu nhầm.

---

## 3. LỖ HỔNG MEDIUM

### SEC-07 / FIN — JSON nối chuỗi làm hỏng giao dịch (injection nhẹ)

**Vị trí:** `LedgerEntryFactory.entry(...)` (`"{\"ref\":\"" + reference + "\"}"`), `FraudRuleEngine` (`details`), `ReconciliationService` (`details`).

**Tác động:** `reference` của transfer = `description` (free text). `description` chứa `"` hoặc `\` → JSON không hợp lệ → insert `jsonb` fail → **rollback toàn bộ giao dịch ledger**, transfer bị từ chối với lỗi DB khó hiểu.

**Khuyến nghị:** dùng Jackson `ObjectMapper.writeValueAsString(Map.of(...))` thay vì nối chuỗi.

---

### FIN-07 — Redis lock key không khớp giữa các thao tác

**Vị trí:** `transaction-engine/.../service/TransactionService.java` (`withAccountLocks`)

**Tác động:** Transfer A→B dùng key `tx:lock:<A>,<B>`; withdraw từ A dùng `tx:lock:<A>`. Hai key khác nhau → **không loại trừ nhau** ở tầng engine. An toàn thực tế hiện dựa vào row-lock của ledger; comment "A→B và B→A serialize" tạo cảm giác an toàn giả (chỉ đúng khi cả hai đều là transfer trên cùng cặp).

**Khuyến nghị:** khoá theo **từng account** (multi-lock) hoặc theo toàn bộ tập account đã sort, để mọi thao tác chạm cùng account đều dùng chung một khoá.

---

### FIN-08 — `OutboxPublisher` không cluster-safe

**Vị trí:** `transaction-engine/.../event/OutboxPublisher.java` (`@Scheduled` trần, không ShedLock).

**Tác động:** Chạy >1 instance → cả hai `findTop50ByStatus(NEW)` và cùng `kafka.send` → **publish trùng**. Consumer idempotent nên chịu được, nhưng ồn và `OutboxEvent.Status.FAILED` không bao giờ được set (poison event retry vô hạn).

**Khuyến nghị:** ShedLock/`@SchedulerLock`, hoặc claim row bằng `UPDATE ... WHERE status=NEW RETURNING`; có ngưỡng retry → `FAILED`.

---

### SEC-08 — Race append chuỗi hash audit

**Vị trí:** `audit-service/.../service/AuditChainService.java`

**Tác động:** `append` đọc `findFirstByOrderByRecordedAtDesc()` (prev hash) rồi insert **không khoá/không optimistic guard**. Hai consumer song song đọc cùng `prevHash` → hai row cùng `prevHash` → **chuỗi đứt vĩnh viễn** và `@Immutable` chặn sửa. Thứ tự còn phụ thuộc `recordedAt` (có thể trùng timestamp).

**Khuyến nghị:** serialize append (khoá advisory / single partition / sequence number), hoặc dùng cột `sequence` tăng dần và unique.

---

### FIN-09 — `AccountBalance.@Version` bị bypass

**Vị trí:** `account-ledger-service/.../repository/AccountBalanceRepository.java` — `applyDelta` là native UPSERT `version = version + 1` trong SQL.

**Tác động:** Optimistic lock của entity không bao giờ được dùng cho thay đổi số dư (defense-in-depth mất). Hiện an toàn nhờ `FOR UPDATE`, nhưng nếu bỏ row-lock sẽ mất lớp bảo vệ.

**Khuyến nghị:** giữ row-lock làm lớp chính (đang đúng) và ghi chú rõ `@Version` không bảo vệ đường UPSERT.

---

### FIN-10 — VELOCITY đếm cả giao dịch `FAILED`/`PENDING`

**Vị trí:** `transaction-engine/.../service/FraudRuleEngine.java`

**Tác động:** `countBySourceAccountIdAndCreatedAtAfter(...)` không lọc `status` → giao dịch đã bị chặn gian lận vẫn **làm tăng bộ đếm**, khiến người dùng bị chặn oan các giao dịch sau.

**Khuyến nghị:** chỉ đếm `status IN (SUCCESS, PROCESSING)`.

---

### SEC-09 — Race dedupe webhook trả 500

**Vị trí:** `payment-gateway-service/.../service/WebhookService.java`

**Tác động:** `findByProviderAndEventId` rồi `save` có thể đụng `UNIQUE(provider,event_id)` khi chạy song song → ném lỗi unique → 500 thay vì nhánh `duplicate:true` (200). Đối tác sẽ retry vô ích.

**Khuyến nghị:** bắt `DataIntegrityViolationException` → trả `duplicate:true`.

---

## 4. LỖ HỔNG LOW / CẢI THIỆN

- **FIN-11** — `AccountBalanceRepository.lockByAccountId` chọn row theo `account_id` và wrap `Optional`; nếu tài khoản có nhiều tiền tệ sẽ ném `IncorrectResultSizeDataAccessException`. Nên khoá theo `(account_id, currency)`.
- **FIN-12** — `AuthService.nextAccountNumber()` = `"9704" + 9 số ngẫu nhiên` **không retry khi trùng** → registration có thể 500. Thêm vòng retry khi `DataIntegrityViolationException`.
- **SEC-10** — `notification-service/.../templates/email/notification.html` lặp `${model}` nhưng `buildMessage` set biến trực tiếp (không có key `model`) → bảng chi tiết email **render rỗng**; `${subject}` cũng không có trong context. Sửa template/context.
- **FIN-13** — `PaymentIntent.expiresAt` được set (15 phút) nhưng **không có job** chuyển sang `EXPIRED`; trạng thái `FAILED`/`EXPIRED` không bao giờ được dùng.
- **Khác (từ kiểm toán tổng thể, không phải lỗ hổng nhưng cần khắc phục):**
  - `infra/docker/*`, `infra/nginx/*`, `infra/monitoring/*`, `infra/scripts/{deploy,rollback,backup-db,seed}.sh` — **thiếu toàn bộ** (M0/M5/M6 chưa làm).
  - `.github/workflows/` — **rỗng**, chưa có CI/CD (M2 ngày 5 / M6 chưa làm).
  - `app/` là **Vite/React web**, không phải React Native (Expo) như `docs/01` §5 / `docs/05` §4.
  - `transaction-engine`, `account-ledger-service`, `api-gateway`, `eureka-server` thiếu profile `application-{local,docker,prod}.yml`.
  - `payment-gateway-service` và `notification-service` **không có test** (Testcontainers khai báo nhưng không dùng).
  - Endpoint thiếu so `docs/04`: `GET /api/v1/accounts` (list ví), `/accounts/{id}/statement` (REST), `/internal/transactions/credit`, `/internal/transactions/{id}`; response `deposit` thiếu `paymentIntentId`/`checkoutUrl`.

---

## 5. THỨ TỰ ƯU TIÊN KHẮC PHỤC

1. **Chặn mất tiền:** FIN-01 (deterministic `transaction_id`), FIN-02 (UNIQUE `(transaction_id, account_id)`), SEC-02 (verify chữ ký trước dedupe).
2. **Khép luồng tiền:** FIN-03 (consumer `payment.succeeded` → `ledger.deposit`).
3. **Bịt lỗ xác thực:** SEC-01 (đóng `/internal/**`), SEC-03 (auth cho notification/audit), SEC-04 (ownership check), SEC-06 (bỏ secret khỏi repo).
4. **Đúng hợp đồng:** SEC-05, FIN-04, FIN-05, FIN-06.
5. **DevOps (điểm chấm):** `infra/docker`, `infra/nginx`, `infra/monitoring`, `.github/workflows`, app Expo.

> **Ghi chú:** Các mục FIN-01/FIN-02/FIN-03/SEC-01/SEC-02 ảnh hưởng trực tiếp tới hai quy tắc tối thượng trong `AGENTS.md` §3 và tới mốc gãy M3 (`docs/06`) — cần xử lý trước khi tiếp tục mở rộng scope.

---

## 6. TRẠNG THÁI KHẮC PHỤC (cập nhật 2026-10-05)

> Toàn bộ mục trong §0 đã được vá và **kiểm chứng bằng cả đọc mã lẫn chạy thật** trên môi trường local
> (`Postgres` + `Redis`, các service `local` profile). Dưới đây là cách vá từng nhóm.

### 6.1. Nhóm Critical (chặn mất tiền)

| ID | Cách khắc phục | Kiểm chứng |
|---|---|---|
| SEC-01 | `InternalTokenFilter` (trong `common`) chạy trước security chain của **cả 5 service**, đòi header `X-Internal-Token` so sánh bằng `MessageDigest.isEqual`; token **không có default** (thiếu → service fail-fast). `simulate-callback` bị tắt ngoài profile dev (`app.payment.demo-endpoints=false`). | `/internal/**` không token → `401`; có token → `200`. `simulate-callback` không token → `401`. |
| FIN-01 | `ledgerTransactionId = UUID.nameUUIDFromBytes("ledger-tx:" + idempotencyKey)` — tất định theo key, retry sau timeout dùng lại đúng id đó; `IdempotencyService.release()` chỉ an toàn vì id dẫn xuất từ key. | Chạy lại cùng key → `200` + `Idempotent-Replay: true`, số dư chỉ trừ 1 lần. |
| FIN-02 | Thêm `UNIQUE(transaction_id, account_id)` (`uq_le_tx_account`) ở tầng DB **và** re-check `replayTransfer` sau khi đã lấy row-lock. | Constraint có thật trong `ledger_entries`; test song song `LedgerParallelSameTransactionIT`. |
| SEC-02 | **Verify chữ ký trước**, chỉ ghi log/dedupe khi chữ ký hợp lệ; callback sai chữ ký trả `400` và **không** tiêu thụ dedupe slot. | Webhook sai chữ ký → `400`, Redis `EXISTS webhook:...=0`. |
| FIN-03 | Thêm `PaymentSucceededConsumer` (Kafka) → `LedgerClient.deposit(...)` với `transaction_id` dẫn xuất từ `paymentIntentId`; `deposit` trả `paymentIntentId` + `checkoutUrl`. | Tạo deposit → `201` kèm `paymentIntentId`/`checkoutUrl`. |

### 6.2. Nhóm High

- **SEC-03** — `notification-service` và `audit-service` có `SecurityConfig` (JWT resource-server); danh tính lấy từ `CurrentUser` (bỏ `userId` query param); audit/reconciliation yêu cầu `ROLE_ADMIN`; `role` claim map qua `RoleClaimAuthenticationConverter`.
- **SEC-04** — `AccountOwnership` kiểm tra chủ sở hữu (trả `404`) cho mọi endpoint đọc của tx-engine; áp dụng cho balance, statement, transaction, ledger-entries, fraud-alerts, list.
- **SEC-05** — `WebhookRecorder` chỉ chuyển `SUCCEEDED` khi `event.status==SUCCEEDED` **và** `provider`/`amount`/`currency` khớp intent.
- **SEC-06** — Bỏ mọi default secret ở `application.yml` gốc (`${JWT_SECRET}`, `${INTERNAL_TOKEN}`, …); thêm `application-{local,docker,prod}.yml` cho gateway/eureka/tx-engine/ledger.
- **FIN-04** — `TransactionRecorder.recordFraud` ghi `FraudAlert` + outbox `risk.alert` trong cùng transaction, đồng thời set giao dịch `FAILED`.
- **FIN-05** — `LedgerClient` dùng `CircuitBreakerRegistry`/`RetryRegistry`; posting không retry, read retry; `BusinessException` được ignore.
- **FIN-06** — `requireOtpIfLarge` enforce ngưỡng `app.otp.threshold`; OTP token có claim `otp=true` và `sub` khớp user.

### 6.3. Nhóm Medium & Low

- **SEC-07** — thay nối chuỗi bằng Jackson `ObjectMapper.writeValueAsString(Map.of(...))` ở `LedgerEntryFactory`, `FraudRuleEngine`, `ReconciliationService`.
- **FIN-07** — lock theo **từng account**, sort tăng dần, giải phóng ngược thứ tự.
- **FIN-08** — `OutboxPublisher` giới hạn `MAX_ATTEMPTS` → chuyển `FAILED` (ShedLock để lại khi scale nhiều instance).
- **SEC-08** — `AuditChainService.append` khoá advisory (`lockChain`) trước khi đọc prev-hash + insert.
- **FIN-09** — giữ row-lock làm lớp chính; ghi chú rõ `@Version` không bảo vệ đường UPSERT.
- **FIN-10** — `countSettledBySourceAccountIdAfter` chỉ đếm `status IN (SUCCESS, PROCESSING)`.
- **SEC-09** — bắt `DataIntegrityViolationException` → trả `duplicate:true`.
- **FIN-11** — `lockByAccountIdAndCurrency(accountId, currency)` (multi-currency an toàn).
- **FIN-12** — `nextAccountNumber()` retry 5 lần khi trùng.
- **SEC-10** — template email nhận `subject` + danh sách `entries` (không còn biến `model` rỗng).
- **FIN-13** — `@Scheduled expireOverdueIntents()` chuyển intent PENDING quá hạn sang `EXPIRED`.

### 6.4. Phát hiện thêm & việc kèm theo

- **SEC-11** (mới, ngoài kiểm toán gốc) — `GET /api/v1/payments/intents/{id}` thiếu kiểm ownership. Đã thêm `PaymentIntentService.getOwned(...)` (trả `404` nếu không sở hữu) + unit test.
- **Cấu hình test** — bổ sung `app.internal.token` và `app.security.jwt-secret` cho `application-test.yml` của tx-engine/ledger để context IT khởi động được khi có Docker.
- **Lưu ý vận hành** — không chạy `mvn install/package` khi các service đang chạy: ghi đè file jar mà JVM đang mở sẽ gây `NoClassDefFoundError`/treo (Spring Boot fat-jar nạp class lazy).

### 6.5. Kiểm chứng tổng hợp

- `./mvnw test` → **BUILD SUCCESS**, toàn bộ unit test xanh (mọi module).
- Smoke test sống (local): security gate `401` đúng chỗ; transfer nợ/có cân bằng; idempotent replay; IDOR `404`; webhook sai chữ ký `400` + không tiêu thụ dedupe; fraud velocity `422` + outbox `risk.alert`; `verify-chain` hợp lệ.
