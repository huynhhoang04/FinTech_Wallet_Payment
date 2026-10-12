# PHẦN 3 — BA GIAI ĐOẠN PHÂN TÍCH KỸ THUẬT (theo `README.MD` §3)

> `README.MD` §3 yêu cầu sinh viên tìm hiểu **3 giai đoạn**: (1) Kế toán kép, (2) Concurrency & Deadlock,
> (3) Chữ ký số HMAC. Mỗi mục dưới đây gồm: **README yêu cầu gì → hệ thống làm gì (file/dòng code cụ thể) →
> lý do làm như vậy (kể cả khi khác README)**.
>
> Cách chạy kiểm chứng từng mục nằm ở `05-noi-dung-bao-ve-chinh.md`; ở đây tập trung vào **thiết kế + lý do**.

---

## GIAI ĐOẠN 1 — NGUYÊN LÝ KẾ TOÁN KÉP (DOUBLE-ENTRY BOOKKEEPING)

### 1.1. README yêu cầu gì

> *"Một giao dịch luôn có 2 dòng bản ghi trong bảng `ledger_entries`:
> Account A: Amount = −100.000 (Debit), Account B: Amount = +100.000 (Credit).
> **Không được** dùng `UPDATE balance = balance - 100000` trực tiếp mà phải tính số dư bằng tổng
> tất cả ledger_entries (hoặc cập nhật Snapshot kèm Ledger Entry audit)."* — `README.MD` §3, Giai đoạn 1

### 1.2. Hệ thống thực hiện

**Bảng dữ liệu** (`docs/03-jpa-schema.md` §4.2, §4.3):

| Bảng | Vai trò | Ràng buộc |
|---|---|---|
| `ledger_entries` | Sổ cái — **bất biến** (`@Immutable`), chỉ ghi thêm | `amount` có dấu: Debit < 0, Credit > 0; `balance_after`; khoá **unique `(transaction_id, account_id)`** |
| `account_balances` | **Snapshot** số dư để đọc nhanh | `UNIQUE(account_id, currency)`, có `@Version` (optimistic lock) |
| `accounts` | Ví người dùng + 3 tài khoản hệ thống | `SYSTEM_MASTER`, `FEE_INCOME`, `SUSPENSE` (seed sẵn) |

**Đường ghi tiền** — `account-ledger-service/.../service/LedgerPostingService.java`, tất cả trong **một
transaction** (`@Transactional`):

1. `lockActive([sourceId, targetId])` → khoá 2 tài khoản + đọc snapshot số dư hiện tại.
2. `LedgerEntryFactory.transfer(...)` sinh **2 dòng** (nguồn âm, đích dương, cùng trị tuyệt đối) → `saveAll`.
3. `applyDelta(accountId, currency, ±amount)` → cập nhật `account_balances` **trong cùng transaction**.
4. `requireNonNegativeForUserAccount` → ví `USER` không được âm → ném `INSUFFICIENT_BALANCE` (422).

→ **Tổng 2 dòng = 0 theo cấu tạo**, không phải theo kiểm tra sau khi ghi.

**Nạp / rút lấy SUSPENSE làm tài khoản đối ứng**, nên nạp tiền cũng là một bút toán kép hợp lệ:
```
Nạp:   SUSPENSE  −amount   |  Ví user  +amount
Rút:   Ví user   −amount   |  SUSPENSE  +amount
Chuyển: Nguồn     −amount   |  Đích      +amount
```
→ **Không tồn tại đường nào ghi 1 dòng đơn lẻ**; tổng toàn sổ luôn = 0.

### 1.3. Vì sao làm vậy (và khác README ở đâu)

| Điểm | README | Thực tế | Lý do |
|---|---|---|---|
| Số dư đọc từ đâu | "tổng tất cả ledger_entries **hoặc** snapshot kèm audit" | **Cả hai**: snapshot để đọc, `ledger_entries` là chân lý để kiểm chứng | Đọc realtime bằng `SUM()` mỗi request thì đắt; snapshot + job đối chiếu `snapshot == SUM(entries)` cho cả tốc độ lẫn kiểm chứng được |
| Ràng buộc "Σ = 0" | Không nói rõ enforce ở đâu | Enforce **theo cấu tạo** (factory sinh cặp dòng) + **verify** bằng `/internal/ledger/verify-chain` và job đối chiếu | JPA/Hibernate **không diễn đạt được** CHECK tổng hợp nhiều dòng (`docs/03` §0 đã ghi rõ) → phải enforce ở tầng service, và có job kiểm tra lại |
| `UPDATE balance = balance - X` | Cấm | Không dùng; chỉ `applyDelta` trên **snapshot** song song với việc ghi entry | Snapshot là cache của `SUM(entries)`, không phải nguồn chân lý; sửa snapshot mà quên entry sẽ bị job đối chiếu bắt |
| Bút toán cho nạp/rút | README chỉ mô tả chuyển khoản | Dùng **SUSPENSE** làm đối ứng | Giữ bất biến Σ=0 cho **mọi** loại giao dịch, không riêng transfer |

**Chứng minh (chi tiết ở Phần 5, tiêu chí 1):** `ledger_entries` của 1 transaction có đúng 1 âm + 1 dương,
`sum = 0`; `/internal/accounts/{id}/verify-balance` trả `consistent=true` cho cả 2 ví;
`/internal/ledger/verify-chain` trả `valid=true, unbalancedTransactions=0, totalAmount=0`.

---

## GIAI ĐOẠN 2 — CHỐNG TRANH CHẤP & DEADLOCK (CONCURRENCY)

### 2.1. README yêu cầu gì

> *"User A chuyển tiền cho User B **đồng thời** User B chuyển tiền cho User A.
> Tìm hiểu cách sắp xếp thứ tự Lock tài khoản theo ID (Lock Ordering) để tránh Deadlock."* — `README.MD` §3, Giai đoạn 2

### 2.2. Hệ thống thực hiện — khoá 2 tầng, cùng một quy tắc thứ tự

**Tầng 1 — `transaction-engine` (Redisson, khoá phân tán):**
`TransactionService` lấy **một lock cho mỗi account, theo thứ tự `accountId` tăng dần**, và nhả theo thứ tự ngược lại:
```java
for (UUID accountId : accountIds.stream().distinct().sorted().toList()) { ... }
```
*(code: `transaction-engine/.../service/TransactionService.java`, comment "Locks are taken per account, ascending")*

**Tầng 2 — `account-ledger-service` (khoá hàng DB):**
`LedgerPostingService.lockActive(...)` cũng **sort tăng dần rồi mới khoá**, và khoá **cả 2 tài khoản cùng lúc
trước khi đọc số dư**:
```java
List<UUID> ordered = accountIds.stream().distinct().sorted().toList();   // ← thứ tự cố định
... accountRepository.lockById(accountId)          // SELECT * FROM accounts WHERE id = :id FOR UPDATE
... accountBalanceRepository.lockByAccountIdAndCurrency(...)  // ... FOR UPDATE
```

→ Vì **mọi** writer của cùng cặp tài khoản đều đi theo **cùng một thứ tự**, không thể xảy ra vòng chờ
(A giữ X chờ Y, B giữ Y chờ X) ⇒ **deadlock-free theo thiết kế**, không phải theo may mắn.

**Chống ghi trùng khi 2 request đua nhau** (đây là phần README không nói tới nhưng bắt buộc phải có):
`postTransfer` kiểm tra replay **2 lần** — trước khi khoá (đường nhanh) và **sau khi đã có khoá**:
```java
Optional<TransferResult> replay = replayTransfer(...);            // lần 1 (chưa khoá)
LockedAccounts locked = lockActive(...);
Optional<TransferResult> committedWhileWaiting = replayTransfer(...);  // lần 2 (đã khoá) ← chốt
```
Lý do có lần 2: dưới `READ_COMMITTED`, request thua đã đọc "chưa có entry nào" **trước khi bị chặn**, nên nếu
không kiểm lại nó sẽ ghi thêm một cặp entry trùng — cặp đó bị chặn ở tầng cuối bởi **unique `(transaction_id, account_id)`**.

### 2.3. Điểm KHÁC README quan trọng nhất: isolation level

README §2.1 mục 2 yêu cầu `REPEATABLE READ` / `SERIALIZABLE`. **Hệ thống dùng `READ_COMMITTED`**, và đây là
quyết định có **số đo thực tế**, ghi ngay trong code (`LedgerPostingService`, javadoc):

> *"Isolation is READ_COMMITTED, not REPEATABLE_READ. Under REPEATABLE_READ PostgreSQL raises SQLState 40001
> ("could not serialize access due to concurrent update") when a transaction locks a row changed by another
> transaction after its snapshot was taken, aborting the posting; **measured with 25 concurrent A→B / B→A pairs,
> 43 of 50 postings failed that way.** The ascending row locks already serialise every writer of the involved
> accounts and READ_COMMITTED re-reads the latest committed balance after the lock is granted, so correctness
> does not depend on the snapshot isolation level."*

**Diễn giải để trình bày:**
1. README khuyên `REPEATABLE READ` để an toàn, nhưng với luồng tiền có **khoá hàng tường minh** thì mức isolation
   đó **phản tác dụng**: snapshot cũ + `FOR UPDATE` trên hàng đã bị transaction khác sửa ⇒ Postgres trả `40001`
   và **huỷ luôn lệnh ghi**.
2. Đo được: 25 cặp A→B / B→A đồng thời ⇒ **43/50 posting thất bại** vì lý do này (không phải deadlock).
3. Cách sửa **không phải** nâng isolation lên `SERIALIZABLE` (sẽ fail nhiều hơn), mà là:
   - giữ **thứ tự khoá tăng dần** (chống deadlock), **và**
   - dùng `READ_COMMITTED` để **đọc lại số dư mới nhất sau khi đã có khoá** ⇒ đúng dữ liệu, không bị 40001.
4. An toàn không còn phụ thuộc snapshot nữa, mà phụ thuộc **thứ tự khoá + đọc lại sau khoá + unique key ở DB**.

### 2.4. Idempotency — chống "trừ tiền 2 lần" (README §2.2 yêu cầu, mô tả ở đây)

`IdempotencyService` (Redis, TTL **24h**, key `idem:<X-Idempotency-Key>`, value `{payloadHash, response}`):

| Trạng thái key | Hệ thống làm gì |
|---|---|
| Chưa có | `SETNX` thành công → **request này sở hữu key**, xử lý tiếp |
| Có, **cùng** payload, đã có response | Trả lại nguyên response cũ + header `Idempotent-Replay: true` (không trừ tiền) |
| Có, **khác** payload | `409 IDEMPOTENCY_CONFLICT` |
| Có, **chưa** có response | Request đầu **đang chạy** → `409 DUPLICATE_REQUEST` |
| Xử lý lỗi tạm thời (upstream chết) | `release(key)` → xoá key để client retry được ngay, không phải chờ hết TTL |

**Hai lớp bảo vệ** (README chỉ nói Redis): Redis là lớp 1, **`transactions.idempotency_key UNIQUE`** là lớp 2 ở DB —
nếu Redis mất dữ liệu, DB vẫn chặn giao dịch trùng.

### 2.5. Test tự động đã có (chạy trong CI)

| Test | Chứng minh |
|---|---|
| `IdempotencyBurstIT` (tx-engine) | **100 request đồng thời cùng key ⇒ đúng 1 transaction, 1 posting**; replay trả cùng transactionId |
| `LedgerParallelSameTransactionIT` | Cùng transactionId chạy song song ⇒ chỉ 1 lần posting (nhờ check-lần-2 + unique key) |
| `LedgerConcurrencyIT` | A↔B đồng thời + tải nhiều ⇒ **không deadlock**, tổng tài sản không đổi |
| `LedgerIdempotencyIT` | Cùng transactionId nhưng payload/tài khoản khác ⇒ bị từ chối |

---

## GIAI ĐOẠN 3 — CHỐNG GIẢ MẠO DỮ LIỆU BẰNG CHỮ KÝ HMAC

### 3.1. README yêu cầu gì

> *"Sinh viên thực hành tính `HMAC-SHA256(payload, secret_key)` từ phía Client và kiểm tra lại chữ ký tại API Gateway."*
> — `README.MD` §3, Giai đoạn 3

### 3.2. Hệ thống thực hiện

**Chuỗi ký (canonical request)** — `docs/04-api-contract.md` §0.3:

```
stringToSign =
    METHOD            + "\n" +
    PATH              + "\n" +
    CANONICAL_QUERY   + "\n" +      # query sắp xếp theo key tăng dần, nối key=value&...
    TIMESTAMP         + "\n" +      # unix giây
    SHA256_HEX(rawBody)

signature = HEX(HMAC_SHA256(secret, stringToSign))
```
Client gửi `X-Signature` + `X-Timestamp`; `rawBody` = **body thô** (GET/DELETE không body → hash chuỗi rỗng).

**Kiểm tra tại Gateway** — `api-gateway/.../filter/HmacAuthFilter.java`:
- Là `GlobalFilter` với `getOrder() = Ordered.HIGHEST_PRECEDENCE + 10` ⇒ chạy **trước** routing/rate-limit.
- Body được cache (`cacheRequestBodyAndRequest`) để đọc raw body mà **không làm mất body** khi forward tiếp.
- 5 nhánh từ chối (đều `400 INVALID_SIGNATURE`):

| Điều kiện | Thông báo |
|---|---|
| Thiếu `X-Signature` hoặc `X-Timestamp` | `Missing X-Signature or X-Timestamp` |
| `X-Timestamp` không parse được | `Invalid X-Timestamp` |
| Lệch quá `app.hmac.max-skew-seconds` (mặc định **300s**) | `Timestamp outside allowed window` |
| Chữ ký sai | `Signature mismatch` |
| (Miễn HMAC) `/actuator/**`, `/webhooks/**` | — |

- So sánh chữ ký bằng `HmacUtils.verify(...)` → dùng `MessageDigest.isEqual` (**so sánh hằng thời gian**, chống timing attack).

**Phân biệt 2 loại HMAC trong hệ thống** (hay bị hỏi):

| Loại | Ai ký | Ai kiểm | Ghi chú |
|---|---|---|---|
| **Client HMAC** | App/Bruno/demo-api.py (biết `HMAC_SECRET`) | `api-gateway` | Mọi request qua Gateway, trừ 2 prefix miễn |
| **Provider HMAC** | Cổng thanh toán (VietQR/VNPay/Stripe secret) | `payment-gateway-service`, hàm `WebhookService.receive` | Webhook **được miễn HMAC của Gateway** (`/webhooks` nằm trong danh sách loại trừ) vì đối tác không biết secret của mình |

**Thứ tự bắt buộc trong webhook** (`docs/07` SEC-02, đã sửa): **verify chữ ký TRƯỚC, rồi mới ghi dedupe**.
Nếu ghi dedupe trước, kẻ giả mạo có thể gửi callback giả cùng `eventId` để **chiếm chỗ** của callback thật
⇒ khách trả tiền mà ví không được cộng.

### 3.3. Vì sao làm vậy (và khác README ở đâu)

| Điểm | README | Thực tế | Lý do |
|---|---|---|---|
| HMAC ở đâu | "tại API Gateway" | Giữ nguyên, nhưng là **`GlobalFilter` order cao nhất** | Phải chạy trước cả rate-limit và routing: request sai chữ ký không được tiêu tốn một slot rate-limit |
| Chống replay | Không nói | Thêm **`X-Timestamp` ±300s** | HMAC chỉ chứng minh "đúng secret", không chống gửi lại nguyên request cũ; cửa sổ 300s chặn replay |
| So sánh chữ ký | Không nói | `MessageDigest.isEqual` | So sánh `==`/`equals` bị lộ thời gian ⇒ dò được chữ ký |
| Webhook | "verify chữ ký đối tác" | Tách khỏi HMAC Gateway, ký trên **raw body** | Phải hash **đúng bytes gốc**; re-serialize JSON sẽ làm lệch chữ ký |
| Idempotency | Chỉ nói Redis | Redis + **UNIQUE ở DB** | Hai lớp, đã trình bày ở mục 2.4 |
| Rate limit | "theo IP / User ID" | Redis `RequestRateLimiter` theo route: auth 1/s (burst 10), transactions 20/s (burst 40), payments 5/s (burst 10) | Chặn brute-force login mạnh hơn chặn chuyển tiền; chuyển tiền cần nhanh cho burst idempotency |

### 3.4. Cách chứng minh (chi tiết ở Phần 5, tiêu chí 4c)

- Thiếu chữ ký ⇒ `400 INVALID_SIGNATURE`; sai chữ ký ⇒ `400 INVALID_SIGNATURE`.
- Timestamp lệch > 300s ⇒ bị từ chối (`Timestamp outside allowed window`).
- Sửa 1 byte body sau khi ký ⇒ `Signature mismatch` (chữ ký tính trên `SHA256(rawBody)`).
- Webhook sai `X-Provider-Signature` ⇒ `401 INVALID_SIGNATURE` và **không** chiếm dedupe.
- `HmacUtilsTest` + `HmacAuthFilterTest` chạy trong `./mvnw verify` (CI).

---

## TÓM TẮT 3 GIAI ĐOẠN — BẢNG TRẢ LỜI NHANH

| Giai đoạn | README | Hệ thống | Điểm khác biệt then chốt |
|---|---|---|---|
| 1. Kế toán kép | 2 dòng ±amount, không `UPDATE balance` | `ledger_entries` (−/+) + snapshot `account_balances` cập nhật **cùng transaction**; nạp/rút dùng SUSPENSE làm đối ứng | Enforce Σ=0 **theo cấu tạo**, không theo UPDATE; có job verify lại |
| 2. Concurrency | Lock ordering theo ID, tránh deadlock | Sort `accountId` tăng dần ở **cả 2 tầng** (Redisson + `FOR UPDATE`); check replay 2 lần; unique key DB | **Dùng `READ_COMMITTED` thay vì `REPEATABLE READ`**: đo được 43/50 posting fail vì `40001` |
| 3. HMAC | HMAC-SHA256 client → verify ở Gateway | `GlobalFilter` order cao nhất, `X-Timestamp` ±300s, `MessageDigest.isEqual`; webhook ký riêng trên raw body | Thêm chống replay + so sánh hằng thời gian + verify-trước-dedupe |
