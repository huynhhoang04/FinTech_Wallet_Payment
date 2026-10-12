# PHIẾU TÓM TẮT DỰ ÁN — dán vào đầu cuộc hỏi (Hermes/LLM) hoặc in làm cheat sheet

> Dùng khi bảo vệ: mở Hermes, dán/nạp file này trước, rồi hỏi. Mọi số liệu dưới đây là **số đo thật**
> của hệ thống đang chạy, không phải ước lượng.

## 1. Một đoạn để định vị dự án

FinTech Wallet & Payment Gateway — **7 service** Java 21 + Spring Boot 3.5, Maven multi-module, chạy
**Docker Compose trên 1 VPS** (4 vCPU/8 GB), edge là **Cloudflare Tunnel → Nginx**, event bus **Kafka KRaft**,
giao tiếp nội bộ đường tiền bằng **gRPC**, tất cả DB là **PostgreSQL**. Deadline 15 ngày (28/09→12/10/2026),
nhóm chốt **1 toolchain Java** để không phải nuôi 2 hệ build.

| # | Service | Port | Vai trò |
|---|---|---|---|
| 1 | `eureka-server` | 8761 | Service discovery (**thêm so với README** — blue/green đổi port mỗi lần deploy) |
| 2 | `api-gateway` | 8080 | HMAC + JWT + rate limit + routing `lb://` |
| 3 | `transaction-engine` | 8081 | transfer/deposit/withdraw, idempotency Redis, Redisson lock, **fraud rule engine nhúng**, outbox |
| 4 | `account-ledger-service` | 8082 + gRPC **9090** | `users`/`accounts`/**`ledger_entries`** kế toán kép, cấp JWT |
| 5 | `payment-gateway-service` | 8083 | payment intent + webhook có chữ ký provider |
| 6 | `notification-service` | 8084 | consumer Kafka → email (Mailpit) |
| 7 | `audit-service` | 8085 | audit log **chuỗi hash** + đối chiếu định kỳ + metric cảnh báo |

**Khác README §2:** thêm Eureka; Fraud **không tách service** (RAM + deadline); Notification/Audit **tách đôi**
(SMTP chậm không được chặn audit; khi lệch sổ phải cảnh báo được kể cả mail chết); không dùng MongoDB/Elasticsearch
(ràng buộc PostgreSQL + RAM).

## 2. Các con số PHẢI nhớ

| Số | Nghĩa |
|---|---|
| **43/50** | Posting **thất bại** khi dùng `REPEATABLE READ` (25 cặp A↔B đồng thời, lỗi `SQLState 40001`) → nên chọn `READ_COMMITTED` |
| **100** | Số request đồng thời cùng 1 idempotency key ⇒ **đúng 1 transaction, 1 cặp bút toán**; `409 DUPLICATE_REQUEST` là **hợp lệ** |
| **exitCode=137** | Bị kernel OOM-kill (notification/audit ở `mem_limit=320m` + `MaxRAMPercentage=70`) → đã nâng lên `512m` |
| **300 s** | Cửa sổ `X-Timestamp` cho HMAC (chống replay) |
| **24 h** | TTL idempotency key trong Redis (`idem:<key>`); hỗ trợ 4 trạng thái: mới / replay / conflict / in-flight |
| **3 s** | Deadline gRPC khi TxEngine gọi Ledger; **không retry** posting |
| **base+1000** | Mỗi lần blue/green deploy, service dời cổng thêm 1000 (nên phải đọc `upstream.conf`, không hardcode) |
| **~6,9/8 GB** | Ngân sách RAM đã dùng; không còn chỗ cho service thứ 8 |
| **15 ngày** | Thời gian dự án (28/09 → 12/10/2026) |

## 3. Câu hỏi hay gặp → trả lời ngắn

| Câu hỏi | Trả lời 1–3 câu |
|---|---|
| Không được `UPDATE balance = balance - X`? | Không. Mỗi giao dịch ghi **≥2 dòng** `ledger_entries` (nguồn âm, đích dương) và cập nhật snapshot `account_balances` **trong cùng transaction**; số dư = `SUM(entries)`; job đối chiếu kiểm lại |
| Chống trừ tiền 2 lần thế nào? | 3 lớp: Redis idempotency key (24h) → `transactions.idempotency_key UNIQUE` → posting idempotent theo `transactionId` + unique `(transaction_id, account_id)` ở ledger |
| Chống deadlock thế nào? | Khoá tài khoản **theo thứ tự `accountId` tăng dần** ở **cả 2 tầng** (Redisson ở TxEngine, `SELECT … FOR UPDATE` ở Ledger); có test `LedgerConcurrencyIT` |
| Sao không dùng `REPEATABLE READ` như đề? | Vì nó **phản tác dụng** khi có khoá hàng tường minh: Postgres trả `40001` và huỷ lệnh ghi — đo được **43/50 posting fail**. `READ_COMMITTED` + đọc lại sau khi đã có khoá là đúng và không bị lỗi đó |
| HMAC chống được gì? | Client HMAC ở Gateway (verify **trước** routing/rate-limit) chống sửa dữ liệu trên đường truyền; thêm `X-Timestamp ±300s` chống **replay**; so sánh bằng `MessageDigest.isEqual` |
| Webhook có HMAC của Gateway không? | Không — `/webhooks/**` được miễn HMAC Gateway, nhưng **bắt buộc** verify chữ ký **của provider** trên **raw body**, và phải verify **trước** khi ghi dedupe |
| Gửi lại webhook có cộng tiền 2 lần không? | Không. `UNIQUE(provider, event_id)` chặn ở tầng vào; và khi cộng ví, ledger id **suy ra từ `paymentIntentId`** ⇒ gửi lại chỉ là **replay** ở ledger |
| gRPC có public không? | **Không.** gRPC chỉ nội bộ trong mạng Docker (`transaction-engine → account-ledger-service:9090`); vhost `grpc.conf` là **vhost chết** (đã ghi nợ `docs/09` §13.8). Muốn test từ laptop phải SSH tunnel tới **IP container** |
| Latency gRPC bao nhiêu? | Số đo được là **latency end-to-end của API transfer**, **không** phải benchmark gRPC độc lập — đừng nhận vơ |
| Tên metric cảnh báo lệch sổ? | **`ledger_imbalance`** (Micrometer **bỏ hậu tố `_total`** khi export, dù code khai báo `ledger_imbalance_total`) + `reconciliation_last_success_timestamp_seconds` |
| Deploy thế nào khi không SSH được? | GitHub runner không SSH vào VPS được (inbound chặn theo địa lý) → đi **Cloudflare Tunnel → `deploy-hook` (HTTPS + Bearer token + whitelist service/sha)** → `deploy.sh` blue/green |
| Blue/green cụ thể? | Green chạy `port+1000` → health gate `/actuator/health` → `sed upstream.conf` + `nginx -s reload` (+ sync target Prometheus) → container cũ giữ làm cửa sổ rollback |
| Tại sao có Eureka? | Vì port đổi mỗi lần deploy; hardcode `host:port` **đã gây lỗi thật** (audit-service ghi reconciliation `FAILED`) |
| Nạp tiền qua đâu? | `POST /api/v1/transactions/deposit` **không** cộng ví: nó mở deposit PENDING + xin checkout session; ví chỉ tăng khi có **webhook ký** → Kafka → consumer gọi gRPC `deposit` |

## 4. Không được nói sai (dễ mất điểm)

- **Không** nói gRPC đang expose ra Internet, hay demo `grpcurl` từ ngoài.
- **Không** nói "100/100 request trả 2xx" — yêu cầu là **đúng 1 transaction** được tạo và **0 request** bị 429/unreachable.
- **Không** nói health/smoke xanh là đã chứng minh luồng tiền (đã từng health xanh mà eureka sai cổng ⇒ 503).
- **Không** chiếu secret/token/PII lên màn hình.
- **Không** sửa DB production để "làm sạch" demo.

## 5. Bản đồ tài liệu (hỏi sâu thì mở file này)

| Cần gì | File |
|---|---|
| Kiến trúc, service, lý do khác README | `docs/bảo vệ/02-phan-tich-kien-truc-microservices.md` |
| Kế toán kép · concurrency/deadlock · HMAC (chi tiết + code) | `docs/bảo vệ/03-ba-giai-doan-ky-thuat.md` |
| Deploy VPS, CI/CD, blue/green, monitoring, 10 sự cố thật | `docs/bảo vệ/04-huong-dan-deploy-vps.md` |
| Kịch bản bảo vệ 4 tiêu chí, thao tác từng bước | `docs/bảo vệ/05-noi-dung-bao-ve-chinh.md` |
| Hợp đồng API, mã lỗi, spec HMAC | `docs/04-api-contract.md` |
| Schema entity | `docs/03-jpa-schema.md` |
| Nhật ký deploy thật + nợ kỹ thuật | `docs/09-cicd-pipeline.md` (§13) |
| Runbook demo + bảng tiêu chí tự động | `docs/10-defense-demo-runbook.md` |

## 6. Cách chạy Hermes agent (đã cấu hình sẵn)

Chuẩn bị đã làm một lần: `hermes skills trust` trong repo ⇒ **4 skill repo-local** (backend/devops/frontend/security)
tự load cho session chạy trong repo; đã tạo project `fintech-wallet-payment` trỏ vào repo.

```bash
# A. Hỏi nhanh 1 câu (một lượt, in ra terminal) — đã kiểm chứng chạy đúng
cd /mnt/Data/rikkei/FinTech_Wallet_Payment
hermes --cli -z 'Đọc file "/mnt/Data/rikkei/FinTech_Wallet_Payment/docs/bảo vệ/00-tom-tat-du-an.md" rồi trả lời: <câu hỏi>'

# B. Hỏi nhiều câu (chat tương tác) — dán câu mở đầu ở dưới rồi hỏi tiếp
cd /mnt/Data/rikkei/FinTech_Wallet_Payment
hermes chat
```

> ⚠️ **Đã kiểm chứng:** Hermes chạy tool ở `$HOME`, **không** tự thấy repo, nên **luôn đưa đường dẫn tuyệt đối**
> (hoặc dán câu mở đầu dưới đây). Nếu chỉ hỏi chung chung, nó sẽ đi đọc code của chính nó và trả lời sai dự án.
> Model đang dùng: `nvidia/nemotron-3-ultra-550b-a55b:free` qua OpenRouter (có sẵn API key); Ollama +
> `dolphin-mistral:7b` là phương án local nếu mất mạng.

**Câu mở đầu (dán khi vào `hermes chat`):**
```
Bạn là trợ lý bảo vệ cho dự án FinTech Wallet & Payment Gateway.
Đọc file này trước: /mnt/Data/rikkei/FinTech_Wallet_Payment/docs/bảo vệ/00-tom-tat-du-an.md
Nếu cần chi tiết, đọc thêm 02/03/04/05 trong cùng thư mục (đường dẫn tuyệt đối).
Trả lời ngắn, đúng số liệu, nói rõ khi nào là "hạn chế đã biết".
```
