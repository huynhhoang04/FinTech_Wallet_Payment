# 04 — API CONTRACT (ĐẦY ĐỦ, CHIA THEO SERVICE)

> Quy ước chung + danh sách endpoint của **từng service**.
> App/merchant **chỉ** gọi qua API Gateway. Các endpoint `/internal/**`, gRPC, webhook là **nội bộ / đối tác**, không expose ra ngoài.

---

## 0. QUY ƯỚC CHUNG

### 0.1. Base URL & versioning
| Kênh | Base URL |
|---|---|
| App / Merchant | `https://api.<domain>/api/v1` |
| Webhook đối tác | `https://api.<domain>/webhooks` |
| Nội bộ (service ↔ service) | `http://<service-name>:<port>/internal` |
| gRPC | `<service-name>:9090` |

Version nằm ở path (`/api/v1`). Breaking change → `/api/v2`.

### 0.2. Header bắt buộc
| Header | Bắt buộc | Ý nghĩa |
|---|---|---|
| `Authorization: Bearer <JWT>` | Với API cần đăng nhập | Token do Auth cấp |
| `X-Idempotency-Key: <UUID>` | **Mọi API động tiền** | Chống thực thi lặp |
| `X-Timestamp: <unix_seconds>` | Mọi request ký HMAC | Chống replay (±300s) |
| `X-Signature: <hex>` | Mọi request ký HMAC | Chữ ký HMAC-SHA256 |
| `X-Request-Id: <UUID>` | Khuyến nghị | Truy vết log xuyên service |
| `Content-Type: application/json` | Có body | |

### 0.3. Đặc tả chữ ký HMAC-SHA256
```
stringToSign =
    METHOD        + "\n" +
    PATH          + "\n" +
    CANONICAL_QUERY + "\n" +
    TIMESTAMP     + "\n" +
    SHA256_HEX(rawBody)

signature = HEX( HMAC_SHA256(key = clientSecret, data = stringToSign) )
```
- `CANONICAL_QUERY`: query params sắp xếp theo key tăng dần, nối `key=value&...`; không có thì để chuỗi rỗng.
- `rawBody`: body thô; GET/DELETE không body → hash của chuỗi rỗng.
- So sánh chữ ký bằng `MessageDigest.isEqual` (chống timing attack).
- Gateway **từ chối** nếu thiếu `X-Signature`/`X-Timestamp`, sai chữ ký, hoặc timestamp lệch quá **300 giây**.

### 0.4. Định dạng lỗi
```json
{
  "timestamp": "2026-09-28T10:15:30Z",
  "status": 422,
  "code": "INSUFFICIENT_BALANCE",
  "message": "Số dư tài khoản không đủ",
  "path": "/api/v1/transactions/transfer",
  "requestId": "2f1c...",
  "traceId": "ab34...",
  "details": [{ "field": "amount", "issue": "vượt hạn mức 1 giao dịch" }]
}
```

| HTTP | `code` | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Sai định dạng / thiếu field |
| 400 | `INVALID_SIGNATURE` | Chữ ký HMAC sai / thiếu timestamp |
| 401 | `UNAUTHENTICATED` / `TOKEN_EXPIRED` | Thiếu/hết hạn JWT |
| 403 | `FORBIDDEN` | Không có quyền trên tài nguyên |
| 404 | `ACCOUNT_NOT_FOUND` / `NOT_FOUND` | Không tồn tại |
| 409 | `DUPLICATE_REQUEST` | Idempotency key đang xử lý |
| 409 | `IDEMPOTENCY_CONFLICT` | Key đã dùng cho request khác payload |
| 422 | `INSUFFICIENT_BALANCE` | Không đủ tiền |
| 422 | `FRAUD_BLOCKED` | Rule engine chặn |
| 423 | `ACCOUNT_LOCKED` | Tài khoản bị khoá |
| 429 | `RATE_LIMITED` | Vượt rate limit |
| 503 | `UPSTREAM_UNAVAILABLE` | Service phụ thuộc chết (circuit breaker mở) |

### 0.5. Định dạng phân trang
Request: `?page=0&size=20&sort=createdAt,desc` (mặc định `size=20`, tối đa `100`).
```json
{ "content": [], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7, "last": false }
```

### 0.6. Tiền tệ
`amount` là **số nguyên** (đơn vị nhỏ nhất — VND = đồng). Không bao giờ gửi số thập phân.

---

## 1. `api-gateway` — EDGE

Không có endpoint nghiệp vụ riêng; nó **định tuyến + kiểm soát**. Bảng route:

| Route ID | Path | Đích | Filter |
|---|---|---|---|
| `auth` | `/api/v1/auth/**` | `lb://account-ledger-service` (module identity) | RateLimit(10/phút/IP), không cần JWT |
| `transactions` | `/api/v1/transactions/**` | `lb://transaction-engine` | HMAC + JWT + RateLimit(20/s) + CircuitBreaker |
| `accounts` | `/api/v1/accounts/**` | `lb://transaction-engine` | HMAC + JWT |
| `payments` | `/api/v1/payments/**` | `lb://payment-gateway-service` | HMAC + JWT + RateLimit |
| `webhooks` | `/webhooks/**` | `lb://payment-gateway-service` | Verify chữ ký đối tác, **không** JWT |
| `notifications` | `/api/v1/notifications/**` | `lb://notification-service` | HMAC + JWT |

**Endpoint vận hành của Gateway:**
| Method | Path | Mô tả |
|---|---|---|
| GET | `/actuator/health` | Health tổng |
| GET | `/actuator/gateway/routes` | Danh sách route đang active |
| GET | `/actuator/prometheus` | Metrics |

---

## 2. AUTH / IDENTITY (nằm trong `account-ledger-service`, module `identity`)

> **Cần chốt:** danh sách service chưa có service auth riêng. Để tiết kiệm RAM, gộp vào `account-ledger-service` (service này đã sở hữu quan hệ user ↔ account).

### 2.1. `POST /api/v1/auth/register`
Đăng ký người dùng + tạo ví mặc định (VND).
```json
// Request
{ "phone": "0912345678", "email": "a@b.com", "password": "S3cret!23", "fullName": "Nguyen Van A" }
// Response 201
{ "userId": "uuid", "walletAccountId": "uuid", "accountNumber": "9704...001", "status": "ACTIVE" }
```

### 2.2. `POST /api/v1/auth/login`
```json
// Request
{ "phone": "0912345678", "password": "S3cret!23" }
// Response 200
{ "accessToken": "eyJ...", "refreshToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 900 }
```
`401 UNAUTHENTICATED` nếu sai thông tin.

### 2.3. `POST /api/v1/auth/refresh`
Body: `{ "refreshToken": "..." }` → trả cặp token mới.

### 2.4. `GET /api/v1/auth/me`
Header: Bearer. Response 200:
```json
{ "userId": "uuid", "phone": "0912345678", "email": "a@b.com", "fullName": "Nguyen Van A", "kycStatus": "VERIFIED" }
```

### 2.5. `POST /api/v1/auth/otp/request` · `POST /api/v1/auth/otp/verify`
Xác thực OTP cho giao dịch lớn (tuỳ chọn, dùng cho demo HMAC/OTP).
```json
// request OTP -> 200
{ "otpRef": "uuid", "expiresIn": 120 }
// verify
{ "otpRef": "uuid", "code": "123456" } -> { "otpToken": "...", "valid": true }
```

---

## 3. `transaction-engine` — CORE

> Mọi endpoint động tiền **bắt buộc** `X-Idempotency-Key`.

### 3.1. `POST /api/v1/transactions/transfer`
Chuyển tiền giữa 2 ví.
```json
// Request
{
  "sourceAccountId": "uuid",
  "targetAccountNumber": "9704...002",
  "amount": 100000,
  "currency": "VND",
  "description": "Tra tien an",
  "otpToken": "optional"
}
// Response 201
{
  "transactionId": "uuid",
  "status": "SUCCESS",
  "amount": 100000,
  "fee": 0,
  "sourceAccountId": "uuid",
  "targetAccountId": "uuid",
  "ledgerTransactionId": "uuid",
  "createdAt": "2026-09-28T10:15:30Z"
}
```
| Status | Khi nào |
|---|---|
| 201 | Thành công (hoặc trả lại kết quả cũ nếu idempotent replay) |
| 409 | `DUPLICATE_REQUEST` — key đang xử lý |
| 409 | `IDEMPOTENCY_CONFLICT` — key đã dùng payload khác |
| 422 | `INSUFFICIENT_BALANCE` |
| 422 | `FRAUD_BLOCKED` |
| 423 | `ACCOUNT_LOCKED` |
| 503 | Ledger không phản hồi (circuit breaker mở) |

> **Idempotency replay:** cùng `X-Idempotency-Key` + payload giống → trả **nguyên response cũ**, HTTP `200` (hoặc `201`), `Idempotent-Replay: true` ở header.

### 3.2. `POST /api/v1/transactions/deposit`
Nạp tiền — **không** cộng ví trực tiếp; tạo luồng nạp (thực tế đi qua payment-gateway rồi webhook về).
```json
// Request
{ "accountId": "uuid", "amount": 500000, "currency": "VND", "provider": "VIETQR" }
// Response 201
{ "transactionId": "uuid", "status": "PENDING", "paymentIntentId": "uuid", "checkoutUrl": "https://..." }
```

### 3.3. `POST /api/v1/transactions/withdraw`
```json
{ "accountId": "uuid", "amount": 200000, "currency": "VND", "bankCode": "VCB", "bankAccount": "0123456789" }
// 201 -> { "transactionId": "uuid", "status": "PENDING", "estimatedSettleAt": "..." }
```

### 3.4. `GET /api/v1/transactions/{transactionId}`
```json
{
  "transactionId": "uuid", "type": "TRANSFER", "status": "SUCCESS",
  "sourceAccountId": "uuid", "targetAccountId": "uuid",
  "amount": 100000, "fee": 0, "currency": "VND",
  "description": "Tra tien an", "failureReason": null,
  "createdAt": "...", "updatedAt": "..."
}
```

### 3.5. `GET /api/v1/transactions`
Danh sách giao dịch (phân trang).
Query: `accountId`, `type`, `status`, `from`, `to`, `page`, `size`.

### 3.6. `GET /api/v1/transactions/{transactionId}/ledger-entries`
Trả về các dòng sổ cái của giao dịch (Nợ/Có) — **chứng minh kế toán kép** ngay trên API.
```json
{
  "transactionId": "uuid",
  "entries": [
    { "accountId": "uuid-A", "direction": "DEBIT",  "amount": -100000, "balanceAfter": 900000 },
    { "accountId": "uuid-B", "direction": "CREDIT", "amount":  100000, "balanceAfter": 300000 }
  ],
  "sum": 0
}
```

### 3.7. `GET /api/v1/accounts`
Danh sách ví của user đang đăng nhập.
```json
{ "content": [ { "accountId": "uuid", "accountNumber": "9704...001", "currency": "VND", "balance": 1000000, "status": "ACTIVE" } ] }
```

### 3.8. `GET /api/v1/accounts/{accountId}/balance`
```json
{ "accountId": "uuid", "currency": "VND", "available": 1000000, "pending": 0, "asOf": "..." }
```

### 3.9. `GET /api/v1/accounts/{accountId}/statement`
Sao kê: `?from=&to=&page=&size=` → trả danh sách dòng ledger + số dư đầu/cuối kỳ.

### 3.10. `GET /api/v1/transactions/{transactionId}/fraud-alerts`
Các cảnh báo gian lận gắn với giao dịch.

### 3.11. Endpoint nội bộ
| Method | Path | Mô tả |
|---|---|---|
| POST | `/internal/transactions/credit` | Cộng ví do webhook nạp tiền thành công (gọi bởi luồng Kafka, không expose) |
| GET | `/internal/transactions/{id}` | Tra cứu nội bộ |

---

## 4. `account-ledger-service` — gRPC + ADMIN REST

### 4.1. gRPC contract — `ledger.proto`
```proto
syntax = "proto3";
package ledger.v1;
option java_multiple_files = true;
option java_package = "com.fintech.ledger.grpc";

service LedgerService {
  rpc PostTransfer (PostTransferRequest) returns (PostTransferResponse);
  rpc Deposit      (DepositRequest)     returns (LedgerResult);
  rpc Withdraw     (WithdrawRequest)    returns (LedgerResult);
  rpc GetBalance   (GetBalanceRequest)  returns (GetBalanceResponse);
  rpc GetStatement (GetStatementRequest) returns (GetStatementResponse);
}

message Money { int64 amount = 1; string currency = 2; }

message PostTransferRequest {
  string transaction_id   = 1;   // UUID do tx-engine sinh, chống trùng khi retry
  string source_account_id = 2;
  string target_account_id = 3;
  Money  amount = 4;
  string description = 5;
}

message PostTransferResponse {
  string ledger_transaction_id = 1;
  string status = 2;              // POSTED | REJECTED
  int64  source_balance_after = 3;
  int64  target_balance_after = 4;
  string error_code = 5;          // INSUFFICIENT_FUNDS | ACCOUNT_LOCKED | ...
}

message DepositRequest  { string transaction_id = 1; string account_id = 2; Money amount = 3; string channel = 4; }
message WithdrawRequest { string transaction_id = 1; string account_id = 2; Money amount = 3; string channel = 4; }
message LedgerResult    { string ledger_transaction_id = 1; string status = 2; int64 balance_after = 3; string error_code = 4; }

message GetBalanceRequest  { string account_id = 1; }
message GetBalanceResponse { string account_id = 1; string currency = 2; int64 available = 3; int64 pending = 4; }

message GetStatementRequest  { string account_id = 1; int64 from_epoch_ms = 2; int64 to_epoch_ms = 3; int32 page = 4; int32 size = 5; }
message LedgerEntryDto { string entry_id = 1; int64 amount_signed = 2; string direction = 3; int64 balance_after = 4; int64 at_epoch_ms = 5; }
message GetStatementResponse { repeated LedgerEntryDto entries = 1; int64 opening_balance = 2; int64 closing_balance = 3; }
```
**Quy ước gRPC:** deadline bắt buộc (mặc định 3s), retry chỉ với `GetBalance`/`GetStatement` (read-only), **không** retry `PostTransfer` (đã có `transaction_id` chống trùng).

### 4.2. REST nội bộ (admin)
| Method | Path | Mô tả |
|---|---|---|
| GET | `/internal/accounts/{id}/balance` | Số dư hiện tại |
| GET | `/internal/accounts/{id}/verify-balance` | So `snapshot` vs `SUM(ledger_entries)` → `{ "consistent": true }` |
| GET | `/internal/ledger/summary?date=` | Tổng Debit/Credit theo ngày |
| GET | `/internal/ledger/verify-chain` | Kiểm tra toàn vẹn sổ cái |
| GET | `/actuator/health`, `/actuator/prometheus` | Vận hành |

---

## 5. `payment-gateway-service` — TÍCH HỢP ĐỐI TÁC

### 5.1. `POST /api/v1/payments/intents`
Tạo phiên nạp tiền.
```json
// Request  (X-Idempotency-Key bắt buộc)
{ "accountId": "uuid", "amount": 500000, "currency": "VND", "provider": "VIETQR", "returnUrl": "app://wallet/result" }
// Response 201
{
  "paymentIntentId": "uuid",
  "provider": "VIETQR",
  "status": "PENDING",
  "checkoutUrl": "https://sandbox.vietqr.../pay/abc",
  "qrContent": "000201010212...",
  "expiresAt": "2026-09-28T10:30:00Z"
}
```

### 5.2. `GET /api/v1/payments/intents/{paymentIntentId}`
Trạng thái phiên nạp: `CREATED | PENDING | SUCCEEDED | FAILED | EXPIRED`.

### 5.3. `GET /api/v1/payments`
Lịch sử nạp tiền của user (phân trang): `?status=&provider=&from=&to=`.

### 5.4. `POST /webhooks/{provider}` — đối tác gọi vào
| | |
|---|---|
| Auth | **Chữ ký của đối tác** (`X-Provider-Signature`), không dùng JWT |
| Chống trùng | `UNIQUE(provider, event_id)` + Redis |
| Xử lý | 200 nhanh → bắn Kafka `payment.succeeded`; lỗi nghiệp vụ vẫn trả 200 để đối tác không retry vô hạn, ghi log |

```json
// Response 200
{ "received": true, "eventId": "evt_123", "duplicate": false }
```

### 5.5. Endpoint nội bộ
| Method | Path | Mô tả |
|---|---|---|
| POST | `/internal/payments/{id}/simulate-callback` | **Chỉ dùng demo** — giả lập ngân hàng callback để không cần cổng thật |
| GET | `/actuator/health`, `/actuator/prometheus` | Vận hành |

---

## 6. `notification-service`

### 6.1. `GET /api/v1/notifications`
Thông báo của user hiện tại (phân trang).
```json
{ "content": [ { "id": "uuid", "channel": "EMAIL", "templateCode": "BALANCE_CHANGED",
  "subject": "Bien dong so du", "status": "SENT", "sentAt": "...", "relatedTransactionId": "uuid" } ] }
```

### 6.2. `GET /api/v1/notifications/{id}`
Chi tiết 1 thông báo.

### 6.3. Endpoint nội bộ
| Method | Path | Mô tả |
|---|---|---|
| POST | `/internal/notifications/test` | Gửi thử 1 email/push để demo |
| GET | `/internal/notifications/dead-letters` | Xem event lỗi |
| GET | `/actuator/health`, `/actuator/prometheus` | Vận hành |

---

## 7. `audit-service`

### 7.1. `GET /api/v1/audit/logs`
Tra cứu audit log (chỉ admin): `?eventType=&aggregateId=&from=&to=&page=&size=`.

### 7.2. `GET /api/v1/audit/logs/{id}`
Chi tiết 1 bản ghi + `prevHash`/`hash` để kiểm chứng.

### 7.3. `GET /api/v1/audit/verify-chain`
Kiểm tra toàn vẹn chuỗi hash.
```json
{ "valid": true, "checkedEntries": 10234, "firstBrokenAt": null }
```

### 7.4. `GET /api/v1/reconciliation/reports`
Danh sách báo cáo đối chiếu theo ngày.

### 7.5. `GET /api/v1/reconciliation/reports/{reportDate}`
```json
{ "reportDate": "2026-09-27", "entryCount": 5120, "totalDebit": 912000000,
  "totalCredit": 912000000, "difference": 0, "status": "BALANCED" }
```

### 7.6. `POST /api/v1/reconciliation/run` (admin)
Chạy đối chiếu thủ công cho 1 ngày → dùng cho demo.

### 7.7. Endpoint nội bộ / vận hành
| Method | Path | Mô tả |
|---|---|---|
| GET | `/actuator/health` | Health |
| GET | `/actuator/prometheus` | **Metrics** `ledger_imbalance_total`, `reconciliation_last_success_timestamp_seconds` |

---

## 8. BẢNG TỔNG HỢP ENDPOINT

| Service | Method | Path | Auth | Idempotency |
|---|---|---|---|---|
| gateway | * | (route) | HMAC + JWT | — |
| ledger (identity) | POST | `/api/v1/auth/register` | — | — |
| ledger (identity) | POST | `/api/v1/auth/login` | — | — |
| ledger (identity) | POST | `/api/v1/auth/refresh` | Refresh token | — |
| ledger (identity) | GET | `/api/v1/auth/me` | JWT | — |
| ledger (identity) | POST | `/api/v1/auth/otp/request` \| `/verify` | JWT | — |
| tx-engine | POST | `/api/v1/transactions/transfer` | JWT | **Bắt buộc** |
| tx-engine | POST | `/api/v1/transactions/deposit` | JWT | **Bắt buộc** |
| tx-engine | POST | `/api/v1/transactions/withdraw` | JWT | **Bắt buộc** |
| tx-engine | GET | `/api/v1/transactions/{id}` | JWT | — |
| tx-engine | GET | `/api/v1/transactions` | JWT | — |
| tx-engine | GET | `/api/v1/transactions/{id}/ledger-entries` | JWT | — |
| tx-engine | GET | `/api/v1/transactions/{id}/fraud-alerts` | JWT | — |
| tx-engine | GET | `/api/v1/accounts` | JWT | — |
| tx-engine | GET | `/api/v1/accounts/{id}/balance` | JWT | — |
| tx-engine | GET | `/api/v1/accounts/{id}/statement` | JWT | — |
| ledger | * | gRPC `LedgerService` | mTLS/nội bộ | `transaction_id` |
| payment | POST | `/api/v1/payments/intents` | JWT | **Bắt buộc** |
| payment | GET | `/api/v1/payments/intents/{id}` | JWT | — |
| payment | GET | `/api/v1/payments` | JWT | — |
| payment | POST | `/webhooks/{provider}` | Provider signature | `event_id` |
| notification | GET | `/api/v1/notifications` | JWT | — |
| notification | GET | `/api/v1/notifications/{id}` | JWT | — |
| audit | GET | `/api/v1/audit/logs` | Admin | — |
| audit | GET | `/api/v1/audit/logs/{id}` | Admin | — |
| audit | GET | `/api/v1/audit/verify-chain` | Admin | — |
| audit | GET | `/api/v1/reconciliation/reports` | Admin | — |
| audit | GET | `/api/v1/reconciliation/reports/{date}` | Admin | — |
| audit | POST | `/api/v1/reconciliation/run` | Admin | — |

---

## 9. VÍ DỤ REQUEST ĐÃ KÝ (DEMO TIÊU CHÍ HMAC)

```bash
# 1) Tạo stringToSign
BODY='{"sourceAccountId":"a1","targetAccountNumber":"9704002","amount":100000,"currency":"VND"}'
TS=$(date +%s)
BODY_HASH=$(printf '%s' "$BODY" | sha256sum | cut -d' ' -f1)
STS=$(printf '%s\n%s\n%s\n%s\n%s' "POST" "/api/v1/transactions/transfer" "" "$TS" "$BODY_HASH")
SIG=$(printf '%s' "$STS" | openssl dgst -sha256 -hmac "$CLIENT_SECRET" | awk '{print $2}')

# 2) Gọi API
curl -X POST https://api.<domain>/api/v1/transactions/transfer \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: $TS" \
  -H "X-Signature: $SIG" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -d "$BODY"
```
> Demo fail-case: bỏ `X-Signature` hoặc sửa 1 ký tự trong body → Gateway trả `400 INVALID_SIGNATURE`.

---

## 10. CÒN LẠI CẦN CHỐT

1. Auth nằm trong `account-ledger-service` hay tách service riêng (mặc định: **gộp**).
2. Cổng thanh toán sandbox cụ thể (VNPay / Stripe / VietQR) — ảnh hưởng spec `/webhooks/{provider}`.
3. Chuẩn chữ ký webhook của từng đối tác (mỗi bên một kiểu).

**Tài liệu tiếp theo:** `05-cicd-deployment.md` → `06-monitoring.md`.
