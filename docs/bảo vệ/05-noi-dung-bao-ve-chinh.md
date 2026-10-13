# PHẦN 5 — NỘI DUNG BẢO VỆ CHÍNH (chứng minh 4 tiêu chí `README.MD` §5)

> Đây là kịch bản bảo vệ. `README.MD` §5 có **4 tiêu chí**; mục 5.1 → 5.4 dưới đây ứng với đúng 4 tiêu chí đó.
> Mỗi tiêu chí được viết theo cùng một khuôn:
>
> **Cần chứng minh gì → Thao tác (dùng công cụ nào, lệnh/request nào) → Phải để ý → Vì sao làm thao tác đó → Kết quả đạt → Bằng chứng lưu.**
>
> Quy ước: **`[BRUNO]`** = thao tác trong Bruno trên laptop · **`[SSH]`** = chạy trên VPS `/opt/fintech` ·
> **`[CI]`** = mở sẵn trên GitHub Actions. Lệnh `python3 infra/scripts/demo-api.py …` chạy trên **VPS**.

---

## 5.0. CHUẨN BỊ (làm trước, không tính vào thời gian 4 tiêu chí)

### 5.0.1. Bảng "cái gì chạy ở đâu" — nhớ cái này để không loay hoay

| Thành phần | Chạy ở đâu | Vì sao |
|---|---|---|
| Bruno collections (public/internal/admin/gRPC) | **Laptop** | Client thật, có HMAC + JWT như app |
| `infra/scripts/bruno-tunnel.sh` | **Laptop** | Đưa port nội bộ (loopback-only trên VPS) về máy để Bruno gọi được |
| `infra/scripts/demo-api.py` | **VPS** (`/opt/fintech`) | Cần gọi `127.0.0.1:<port nội bộ>` cho các lệnh nội bộ (`ledger-check`, `idem-direct`, `verify-balance`) |
| `docker …`, `infra/scripts/deploy.sh`, `psql` | **VPS** | Thao tác hạ tầng |
| GitHub Actions | Trình duyệt | Chứng minh CI/CD |

### 5.0.2. Khởi động đường vào (2 bước)

```bash
# [LAPTOP] tunnel: 8081-8085 (REST nội bộ) + 9090 (gRPC) -> VPS
cd <repo>
infra/scripts/bruno-tunnel.sh
```
Kết quả phải thấy đủ **6 dòng**:
```
[tunnel] local 8081 -> tx_engine (remote 10081)
[tunnel] local 8082 -> ledger_http (remote 12082)
[tunnel] local 8083 -> payment (remote 10083)
[tunnel] local 8084 -> notification (remote 12084)
[tunnel] local 8085 -> audit (remote 13085)
[tunnel] local 9090 -> account-ledger-service gRPC (remote 172.18.0.x:9090)
```
> ⚠️ **Để ý:** số port remote **đổi sau mỗi lần deploy** (`base + 1000`). Đừng chép tay số; script đọc từ
> `/opt/fintech/infra/nginx/conf.d/upstream.conf`. Lần nào thấy `Address already in use` ⇒ tunnel cũ còn chạy, `Ctrl+C` nó trước.

**Trong Bruno:** mở workspace `fitach wallet app/`, chọn environment theo collection:

| Collection | Environment | Ghi chú |
|---|---|---|
| 01 - Public REST | `vps-public` | Điền `demoPhone`/`demoPassword`/`hmacSecret`/`providerSecret` nếu chưa |
| 02 - Internal REST | `vps-internal` | `internalToken` = `change-me-internal` (giá trị đang chạy trên VPS) |
| 03 - Admin REST | `vps-admin` | `adminToken` = dán JWT của tài khoản admin (login ở 5.1 bước 1) |
| 04 - Ledger gRPC | `vps-grpc` | Đã set `protoPath` để **không dùng reflection**; thêm `proto/ledger.proto` + `proto/common.proto` trong Collection Settings |

### 5.0.3. Dữ liệu demo (điền sẵn vào phiếu, KHÔNG chiếu lên màn hình)

| Biến | Giá trị đang dùng | Lấy từ đâu |
|---|---|---|
| `demoPhone` (tài khoản demo) | `0945xxxxxx` | Tài khoản đã đăng ký trên VPS |
| `userId` (identity) | `71308037-…` | Claim `sub` trong JWT sau khi login |
| `accountId` (ví của mình) | `b2d25d75-…` (số TK `9704347264234`) | `[BRUNO] 19 - List My Accounts` |
| `targetAccountNumber` (ví đích) | `9704524581836` | Ví demo thứ hai (chủ khác) |
| `ownerUserId` (cho gRPC) | `71308037-…` | = `userId` |
| Admin | phone trên nằm trong `ADMIN_PHONES` | Đăng nhập chính tài khoản đó để có `role=ROLE_ADMIN` |

### 5.0.4. Nguyên tắc an toàn khi trình bày (nói ra 1 câu trước khi bắt đầu)

- Số tiền **nguyên VND, rất nhỏ** (1–10.000); dùng ví demo, không phải tài khoản thật.
- **Không** chiếu/nói: `HMAC_SECRET`, `INTERNAL_TOKEN`, provider secret, password, JWT, số điện thoại/email thật.
- **Không** reset/xoá DB production để "làm sạch" demo; cần dữ liệu mới thì tạo giao dịch mới.
- **Không** retry bằng một idempotency key mới nếu chưa xác định trạng thái giao dịch trước đó.

### 5.0.5. Thứ tự chạy (khoảng 15–20 phút)

| # | Tiêu chí | Công cụ chính | Thời lượng |
|---|---|---|---|
| 1 | Toàn vẹn tài chính / kế toán kép | Bruno 11/15/20 + `[SSH] ledger-check` | ~5' |
| 2 | Idempotency & Concurrency | `[SSH] idem-direct … 100` + Bruno `conflict` | ~4' |
| 3 | Hiệu năng gRPC | Bruno collection gRPC + `[SSH]` xem log/entry | ~4' |
| 4 | Kịch bản demo (webhook / 2 ví / từ chối HMAC) | Bruno 12/25 + `[SSH] security-test` | ~6' |

---

## 5.1. TIÊU CHÍ 1 — TÍNH TOÀN VẸN TÀI CHÍNH (KẾ TOÁN KÉP)

**Cần chứng minh:** mỗi giao dịch sinh **≥2 bút toán đối ứng**, tổng của giao dịch **= 0**, số dư suy ra từ
ledger (không `UPDATE balance` trực tiếp), và **toàn sổ luôn cân**.

### Thao tác

| # | Thao tác | Công cụ | Lệnh / request | Phải để ý | Vì sao làm thao tác này |
|---|---|---|---|---|---|
| 1 | Đăng nhập lấy JWT | `[BRUNO]` | `02 - Auth - Login` | Phải chọn env `vps-public` và có `hmacSecret`; login vẫn cần HMAC | Chứng minh JWT (lớp auth thứ 2) hoạt động song song HMAC |
| 2 | Ghi số dư **trước** của 2 ví | `[BRUNO]` | `20 - Get Account Balance` (ví mình) và cùng request cho ví đích (đổi `accountId`) | Chụp lại 2 số này — mọi kết luận sau dựa vào nó | Không có số dư trước thì không chứng minh được "đúng số tiền đã đổi" |
| 3 | Chuyển tiền nhỏ | `[BRUNO]` | `11 - Transfer`: `amount` nhỏ (dưới ngưỡng OTP `10.000.000`), điền `idempotencyKey` **mới** | `idempotencyKey` phải là UUID **mới** cho giao dịch mới | Đây là thao tác duy nhất thực sự động tiền; mọi bút toán sinh từ đây |
| 4 | Lấy `transactionId` | — | trong response bước 3 | Nếu response có `transactionId` ⇒ đã ghi ledger | Giao dịch chỉ "thành công" khi ledger đã ghi |
| 5 | Xem bút toán của giao dịch | `[BRUNO]` | `15 - Get Ledger Entries` | Phải thấy **đúng 2 dòng**: 1 âm (nguồn), 1 dương (đích), **cùng trị tuyệt đối** | Đây là bằng chứng trực tiếp của "ghi sổ kép" |
| 6 | Kiểm tra số dư suy ra từ ledger | `[SSH]` | `python3 infra/scripts/demo-api.py verify-balance <accountId>` (chạy cho cả 2 ví) | Kết quả phải `consistent: true` | Chứng minh `snapshot == SUM(ledger_entries)` — không phải "tin" con số trong bảng snapshot |
| 7 | Kiểm tra **toàn sổ** | `[SSH]` | `python3 infra/scripts/demo-api.py ledger-check` | Phải in **`LEDGER INVARIANTS: PASS`** với `valid=true, unbalancedTransactions=0, totalAmount=0` | Đây là bất biến mạnh nhất: tổng **cả sổ** = 0 |
| 8 | Số dư **sau** + đối chiếu | `[BRUNO]` | `20 - Get Account Balance` (cả 2 ví) | Nguồn giảm đúng `amount`, đích tăng đúng `amount` | Khép kín vòng chứng minh |
| 9 | (Admin) Đối chiếu theo ngày | `[BRUNO]` / `[SSH]` | `37 - Reconciliation - Run` hoặc `python3 infra/scripts/demo-api.py reconcile <YYYY-MM-DD>` | Cần `adminToken`; `date` để `YYYY-MM-DD` | Chứng minh có **job đối chiếu định kỳ**, không chỉ test thủ công |
| 10 | (Admin) Chuỗi hash audit | `[BRUNO]` / `[SSH]` | `34 - Audit - Verify Hash Chain` hoặc `python3 infra/scripts/demo-api.py audit-chain` | Phải `valid: true` | Chứng minh nhật ký audit **bất biến, phát hiện được sửa đổi** |

### Kết quả phải đạt
```
15 - Get Ledger Entries:  2 entry, 1 âm + 1 dương, |source| = |target|
verify-balance:           consistent = true (cả 2 ví)
ledger-check:             valid=true, unbalancedTransactions=0, totalAmount=0 → LEDGER INVARIANTS: PASS
37 - reconciliation:      status = BALANCED, difference = 0
34 - audit verify-chain:  valid = true
```

### Bằng chứng lưu vào phiếu (5.5)
Response của `11` (transactionId), response của `15` (2 bút toán), 2 ảnh số dư trước/sau,
output `ledger-check`, response `37`, response `34`.

### Bonus nếu bị hỏi khó — "chứng minh sửa audit thì bị phát hiện"
Chỉ chạy **trên DB demo**, và **khôi phục lại** ngay sau khi chiếu:
```bash
# [SSH] 1. lưu giá trị gốc
docker exec postgres psql -U wallet -d audit_db -tAc \
  "select payload from audit_logs order by recorded_at asc limit 1" > /tmp/audit_original.txt
# 2. sửa 1 bản ghi (giả lập tấn công)
docker exec postgres psql -U wallet -d audit_db -c \
  "update audit_logs set payload = payload || ' ' where id = (select id from audit_logs order by recorded_at asc limit 1)"
# 3. verify-chain => valid:false, checkedEntries tăng dần tới bản ghi bị sửa
python3 infra/scripts/demo-api.py audit-chain
# 4. KHÔI PHỤC về giá trị gốc rồi verify lại => valid:true
docker exec postgres psql -U wallet -d audit_db -c \
  "update audit_logs set payload = '$(cat /tmp/audit_original.txt)' where id = (select id from audit_logs order by recorded_at asc limit 1)"
python3 infra/scripts/demo-api.py audit-chain
```
> ⚠️ Chỉ demo khi thời gian cho phép và **nhớ khôi phục** — bản ghi audit là append-only, không được để lệch
> trước khi nộp. `audit_logs.payload` phải là `text` (không phải `jsonb`) thì hash mới khớp — xem Phần 4 sự cố 8.

---

## 5.2. TIÊU CHÍ 2 — IDEMPOTENCY & CONCURRENCY (100 REQUEST)

**Cần chứng minh:** gửi **100 request giống hệt nhau cùng lúc** với **cùng một** `X-Idempotency-Key` →
hệ thống **chỉ trừ tiền 1 lần**; và chuyển tiền 2 chiều đồng thời **không deadlock**.

### Thao tác

| # | Thao tác | Công cụ | Lệnh | Phải để ý | Vì sao |
|---|---|---|---|---|---|
| 1 | Ghi số dư ví nguồn | `[BRUNO]` | `20 - Get Account Balance` | Ví nguồn phải **đủ tiền** cho 1 lần chuyển (không phải ×100) | Chỉ 1 lần được trừ, nên số dư trước chỉ cần > `amount` |
| 2 | Bắn 100 request đồng thời, **cùng key** | `[SSH]` | `python3 infra/scripts/demo-api.py idem-direct <sourceAccountId> <targetAccountNumber> <amount> 100` | Chạy `idem-direct` (**direct vào TxEngine**) chứ không phải `idem` qua domain | Đi thẳng TxEngine để **bỏ rate limit của Gateway** — nếu qua domain, 429 sẽ làm sai lệch kết luận |
| 3 | Đọc kết quả burst | — | dòng `=> so transactionId khac nhau duoc tao ra:` | Phải là **1** | Đây là kết luận chính của tiêu chí |
| 4 | Replay sau khi burst xong | — | script tự chạy replay cuối | Phải `HTTP 200` + header `Idempotent-Replay: true` + **cùng** transactionId | Chứng minh "trả lại kết quả cũ, không thực thi lại" |
| 5 | Kiểm tra ledger chỉ ghi 1 lần | `[SSH]` | `python3 infra/scripts/demo-api.py entries <transactionId>` | Đúng **1 cặp** bút toán (không phải 100 cặp) | Chốt ở tầng sổ cái, không chỉ ở tầng API |
| 6 | Số dư sau | `[BRUNO]` | `20 - Get Account Balance` | Chỉ giảm **đúng 1 lần** `amount` | Bằng chứng tiền không bị trừ nhiều lần |
| 7 | Cùng key nhưng **payload khác** | `[BRUNO]` / `[SSH]` | `11 - Transfer` (đổi `amount`, giữ nguyên `idempotencyKey`) hoặc `python3 infra/scripts/demo-api.py conflict <src> <target> <amount> <key>` | Phải trả **`409 IDEMPOTENCY_CONFLICT`** | Chứng minh key gắn với **payload**, không chỉ với "thời điểm" |
| 8 | Concurrency 2 chiều | `[CI]` | mở run `backend-ci` → tìm `LedgerConcurrencyIT`, `LedgerParallelSameTransactionIT`, `IdempotencyBurstIT` | Test phải xanh | Chứng minh **deadlock-free** bằng test tự động (25 cặp A↔B), không chỉ bằng lời nói |

### Kết quả phải đạt — đọc đúng cái này
```
=> so transactionId khac nhau duoc tao ra: 1  (PHAI la 1)
   request khong ket noi: 0; bi rate-limit: 0; response bat ngo: 0
IDEMPOTENCY BURST: PASS
Replay: HTTP 200, Idempotent-Replay: true, cùng transactionId
entries(transactionId): 1 cặp bút toán, sum = 0
```

**Phải nói rõ 2 điều này khi chiếu kết quả (rất hay bị hỏi):**
1. **`409 DUPLICATE_REQUEST` là kết quả HỢP LỆ**, không phải lỗi: request đến khi key còn **in-flight** bị từ chối
   theo thiết kế (tránh 2 luồng cùng ghi). Script đã tính các mã này vào "expected".
2. **Không yêu cầu 100/100 trả 2xx.** Yêu cầu là: **đúng 1** transaction được tạo, **0** request unreachable,
   **0** request bị 429 (ở chế độ direct), và replay trả cùng transactionId.

### Bằng chứng lưu
Output đầy đủ của `idem-direct` (100 dòng phân bố + 3 dòng kết luận + replay), `entries`,
số dư trước/sau, và **link run CI** có 3 test concurrency.

---

## 5.3. TIÊU CHÍ 3 — HIỆU NĂNG & ĐÚNG ĐẮN CỦA gRPC TRÊN VPS

**Cần chứng minh:** `transaction-engine` gọi `account-ledger-service` **qua gRPC** và chạy **mượt trên VPS**,
với **deadline 3 giây**; đồng thời 8 RPC của `ledger.proto` gọi được.

> **Nói rõ ngay từ đầu để tránh bị bắt lỗi:** gRPC ở đây là **giao tiếp nội bộ trong mạng Docker**
> (`transaction-engine → account-ledger-service:9090`). Hệ thống **không** expose gRPC ra Internet;
> vhost `grpc.conf` trong nginx là **vhost chết** (nợ đã ghi `docs/09` §13.8).
> Nếu bị hỏi latency: đó là **latency end-to-end của API**, không phải benchmark gRPC độc lập.

### 5.3.A. Chứng minh tại runtime: đường tiền đi qua gRPC (mạnh nhất, không cần công cụ gRPC)

| # | Thao tác | Công cụ | Lệnh / request | Phải để ý | Vì sao |
|---|---|---|---|---|---|
| 1 | Chuyển tiền nhỏ qua API | `[BRUNO]` | `11 - Transfer` | Ghi lại thời gian phản hồi | Request này **bắt buộc** phải đi qua gRPC mới ghi được ledger |
| 2 | Lấy transactionId + ledger entries | `[BRUNO]` | `15 - Get Ledger Entries` | Có bút toán ⇒ gRPC đã chạy thành công | Nếu gRPC hỏng, không thể có `ledger_entries` |
| 3 | Đo latency end-to-end | `[SSH]` | `curl -s -o /dev/null -w 'total=%{time_total}s\n' …` (hoặc đọc `time` của Bruno) | Ghi rõ đây là **end-to-end**, không phải gRPC thuần | Trung thực về phương pháp đo — đúng yêu cầu phản biện |
| 4 | Xem log TxEngine gọi ledger | `[SSH]` | `docker logs --tail 50 transaction-engine \| grep -i -E "grpc\|ledger"` | Thấy dấu vết gọi ledger | Bằng chứng phía client |
| 5 | Xem metric gRPC **phía server** | `[SSH]`/`[LAPTOP]` | `curl -s http://127.0.0.1:8082/actuator/prometheus \| grep grpc_server_call_duration_seconds_count` | Thấy counter theo từng `grpc_method` = `ledger.v1.LedgerService/<Rpc>` | Đây là bằng chứng **server-side**: RPC đã thực sự chạy trên ledger, không phải chỉ "client nói đã gọi" |

**5.3.A.1. Bằng chứng gRPC mạnh nhất: counter + latency thật của server** (đã đo, dùng để chiếu)

```bash
# tren VPS (hoac laptop qua tunnel): counter theo tung RPC, va latency trung binh moi RPC
curl -s http://127.0.0.1:8082/actuator/prometheus | grep 'grpc_server_call_duration_seconds_count{.*LedgerService'
curl -s http://127.0.0.1:8082/actuator/prometheus | grep 'grpc_server_call_duration_seconds_sum{.*LedgerService'
# hoac hoi Prometheus (qua tunnel 9091) - cung so lieu, da duoc scrape:
#   grpc_server_call_duration_seconds_count{grpc_method=~"ledger.*"}
#   sum by (grpc_method) (rate(grpc_server_call_duration_seconds_sum{grpc_method=~"ledger.*"}[5m]))
#     / sum by (grpc_method) (rate(grpc_server_call_duration_seconds_count{grpc_method=~"ledger.*"}[5m]))
```

Kết quả đo thật (10 series, đủ 8 RPC + 2 lần `INVALID_ARGUMENT` từ bước test âm):

| RPC | Latency trung bình (server-side) |
|---|---|
| `Withdraw` | ~78 ms *(lần gọi đầu sau khởi động, có JIT)* |
| `Deposit` | ~38 ms |
| `PostTransfer` | ~31 ms |
| `GetStatement` | ~29 ms |
| `GetBalance` | ~20 ms |
| `GetAccountByNumber` | ~15 ms |
| `GetAccountsByOwner` | ~10 ms |
| `GetLedgerEntries` | ~9.5 ms |

> ✅ **Nhờ metric này, tiêu chí 3 có số liệu gRPC THẬT (server-side)**, không cần vin vào "latency end-to-end".
> Nói cho đúng phương pháp khi bị hỏi: đây là **trung bình cộng trên histogram của server**, gồm cả lần gọi đầu
> (JIT ấm máy) — muốn số sạch thì chạy vài vòng rồi lấy; **không** phải benchmark tải (không có k6/wrk cho gRPC).
> `grpc_status` cũng nằm trong label (`OK`, `INVALID_ARGUMENT`…) nên chứng minh được cả **error mapping** của server.


### 5.3.B. Gọi 8 RPC bằng Bruno (đã bật sẵn proto local, **không** dùng reflection)

Trong collection **04 - Ledger gRPC**, env **`vps-grpc`**, `protoPath = proto/ledger.proto` đã đặt sẵn.

> **Đường dự phòng không cần Bruno** (đã chạy thật qua tunnel, nên dùng khi trình bày cho chắc):
> ```bash
> python3 infra/scripts/ledger-grpc-demo.py             # 5 RPC chỉ đọc
> python3 infra/scripts/ledger-grpc-demo.py --posting   # thêm PostTransfer/Withdraw/Deposit
> ```
> Script tự encode/decode protobuf (không cần `protoc`, không cần stub), chạy qua tunnel
> (`127.0.0.1:9090`) hoặc trên VPS (`--host 172.18.0.x:9090` của container ledger) và in dữ liệu đã decode.
> `--posting` **có đổi tiền thật** (mặc định 1 VND, tự sinh `transactionId` mới nên chạy lại là no-op).
>
> Cảnh báo khi dùng Bruno: nếu Message editor hiện nội dung **méo** (mất vài ký tự đầu dòng), đó là
> **bản nháp đang cache trong tab**, không phải nội dung file — đóng tab rồi mở lại (hoặc quit hẳn Bruno)
> để nó đọc lại `.bru` từ đĩa trước khi Send.

**Thứ tự khuyến nghị — chạy 5 RPC chỉ-đọc trước (không đổi dữ liệu):**

| Thứ tự | Request | Body cần để ý | Kết quả mong đợi |
|---|---|---|---|
| 1 | `GetBalance` | `accountId` = ví mình | `currency=VND`, `available` = số dư hiện tại |
| 2 | `GetAccountByNumber` | `targetAccountNumber` | Trả `accountId`, `accountNumber`, `ACTIVE` |
| 3 | `GetAccountsByOwner` | `ownerUserId` | Danh sách ví của mình |
| 4 | `GetStatement` | `accountId`, `page=0`, `size=20` | Danh sách entry + `closingBalance` |
| 5 | `GetLedgerEntries` | `ledgerTransactionId` = **ledger transaction id** (lấy từ `15` hoặc từ lần PostTransfer trước) | 1 cặp DEBIT + CREDIT, tổng = 0 |

**3 RPC động tiền — chạy sau, mỗi lần đổi `transactionId`:**

| # | Request | Phải để ý |
|---|---|---|
| 6 | `PostTransfer` | `transactionId` = **UUID mới**; `sourceAccountId` / `targetAccountId` phải tồn tại; `amount.amount` nhỏ |
| 7 | `Withdraw` | `accountId` = ví mình; phải **đủ số dư** |
| 8 | `Deposit` | ⚠️ RPC này từng lỗi parse ở bước demo trước (chưa xác nhận lại). Nếu chạy: nếu lỗi thì ghi nhận là **đã biết**, dùng `Withdraw`/`PostTransfer` để chứng minh đường ghi |

**Bắt buộc nhớ 3 điều khi chạy gRPC:**
1. Tunnel phải có dòng `local 9090 -> account-ledger-service gRPC`; nếu không, request báo lỗi kết nối.
   `127.0.0.1:9090` trên host là cổng **chết** (deploy.sh không publish port gRPC) — tunnel phải trỏ vào **IP container**.
2. Body phải là JSON hợp lệ: `"amount": {"amount": "{{amountVnd}}", "currency": "VND"}` — placeholder **có quote**
   vì `Money.amount` là `int64` (JSON chuẩn của int64 là chuỗi).
3. Posting **idempotent theo `transactionId`**: muốn tạo giao dịch mới phải đổi UUID; retry thì **giữ nguyên**.

### Kết quả phải đạt
- 5 RPC chỉ-đọc trả dữ liệu thật (ví dụ đã đo được: `GetBalance → VND 44821`).
- `GetLedgerEntries` trả **2 entry đối ứng**.
- Chuyển tiền qua API tạo ra `ledger_entries` ⇒ gRPC nội bộ hoạt động trên VPS.

### Bằng chứng lưu
Ảnh response 5 RPC chỉ-đọc, 1 ảnh `PostTransfer` thành công (hoặc `Withdraw`), output bước **5.3.A #3–#5**.

---

## 5.4. TIÊU CHÍ 4 — KỊCH BẢN DEMO (webhook nạp tiền · chuyển 2 ví · từ chối request xấu)

`README.MD` §5.4 chia thành 3 kịch bản: **(4a)** nạp tiền qua webhook → cộng ví · **(4b)** chuyển tiền 2 ví →
sổ cái biến động · **(4c)** request thiếu HMAC / sai idempotency key → bị từ chối an toàn.

### 4a. Nạp tiền qua webhook (đường bất đồng bộ: Webhook → Kafka → TxEngine → gRPC → Ledger)

| # | Thao tác | Công cụ | Lệnh / request | Phải để ý | Vì sao |
|---|---|---|---|---|---|
| 1 | Ghi số dư ví **trước** | `[BRUNO]` | `20 - Get Account Balance` | — | Để so sánh "tăng đúng 1 lần" |
| 2 | Tạo payment intent (nạp tiền) | `[BRUNO]` | `12 - Deposit` (`accountId`, `amount`, `provider=VIETQR`) | `idempotencyKey` **mới**; lưu `paymentIntentId` từ response | Nạp tiền ở hệ thống này **không** cộng ví trực tiếp: nó tạo "lời xin nạp", tiền chỉ vào khi có callback đối tác |
| 3 | Gửi webhook **có chữ ký** | `[BRUNO]` | `25 - Provider Webhook - Signed` (cần `paymentIntentId`, `webhookEventId`, `amount`, `providerSecret`) | **Đặt `webhookEventId` = một UUID cố định** trong env trước khi gửi | Có event ID cố định mới chứng minh được "gửi lại không cộng 2 lần" ở bước 5 |
| 4 | Chờ consumer xử lý rồi kiểm số dư | `[BRUNO]` / `[SSH]` | `20 - Get Account Balance` | Xử lý qua Kafka nên cần vài giây; không spam request | Tiền vào qua đường bất đồng bộ, không phải ngay ở response webhook |
| 5 | **Gửi lại y hệt** webhook (cùng `eventId`) | `[BRUNO]` | `25` lần 2, **không đổi** `webhookEventId` | Response phải là **duplicate**, số dư **không** tăng thêm | Chống xử lý lặp — 1 event chỉ cộng tiền 1 lần |
| 6 | Đối chiếu sổ | `[SSH]` | `python3 infra/scripts/demo-api.py ledger-check` | Vẫn `PASS` | Nạp tiền cũng phải giữ Σ=0 (đối ứng qua SUSPENSE) |

> **Vì sao gửi lại webhook KHÔNG cộng tiền lần 2 — có 2 lớp, nên trả lời được cả khi bị hỏi sâu:**
> 1. **Lớp vào:** `payment_intents` + `webhook_logs` chống trùng theo `UNIQUE(provider, event_id)` (+ Redis).
> 2. **Lớp ghi tiền:** `PaymentSucceededConsumer` **suy ra ledger transaction id từ chính `paymentIntentId`**
>    (`ledger-deposit:<intentId>`) và bỏ qua nếu transaction đã `SUCCESS` ⇒ gửi lại event là **replay ở ledger**,
>    không phải một khoản credit mới. Thêm nữa, `deposit` trên đường tiền vẫn là **gRPC có deadline 3s**
>    (`LedgerClient.deposit`) — đúng tiêu chí 3.
>
> ⚠️ **Cách A (khuyến nghị — chạy được ngay):** dùng `[BRUNO] 25` như trên, vì `providerSecret` trong env
> (`change-me-vietqr`) **khớp** `VIETQR_SECRET` đang chạy trên VPS.
> ⚠️ **Cách B (`demo-api.py webhook`):** script này **từ chối ký nếu secret còn là placeholder**
> (`change-me-…`). Muốn dùng phải **rotate `VIETQR_SECRET`** trên VPS rồi redeploy `payment-gateway-service`
> và cập nhật `providerSecret` trong Bruno. Chưa rotate thì **dùng Cách A**.

### 4b. Chuyển tiền giữa 2 ví → sổ cái biến động

| # | Thao tác | Công cụ | Lệnh / request | Phải để ý |
|---|---|---|---|---|
| 1 | Số dư 2 ví trước | `[BRUNO]` | `20` cho 2 `accountId` | Chụp lại |
| 2 | Chuyển tiền nhỏ | `[BRUNO]` | `11 - Transfer` | `idempotencyKey` mới |
| 3 | Bút toán | `[BRUNO]` | `15 - Get Ledger Entries` | 1 âm + 1 dương, cùng |giá trị| |
| 4 | Số dư 2 ví sau | `[BRUNO]` | `20` cho 2 `accountId` | Nguồn −amount, đích +amount |
| 5 | Toàn sổ vẫn cân | `[SSH]` | `ledger-check` | `LEDGER INVARIANTS: PASS` |

### 4c. Từ chối request thiếu chữ ký / sai idempotency key

| # | Thao tác | Công cụ | Lệnh / request | Kết quả mong đợi | Vì sao |
|---|---|---|---|---|---|
| 1 | Bộ test âm (thiếu HMAC + sai HMAC + thiếu idempotency key) | `[SSH]` | `python3 infra/scripts/demo-api.py security-test` | `400 INVALID_SIGNATURE` (2 lần) và `400 VALIDATION_ERROR` cho request thiếu key | Chạy **tự động**, không phải sửa tay trên Bruno ⇒ không thể "vô tình" gửi được |
| 2 | Tự tay gửi thiếu chữ ký (nếu muốn chiếu trực tiếp) | `[BRUNO]` | Clone request `02 - Auth - Login`, **xoá** header `X-Signature` | `400 INVALID_SIGNATURE` | Cho thấy gateway chặn ở tầng filter, trước cả xác thực JWT |
| 3 | Cùng key, khác payload | `[BRUNO]` | `11` gửi 2 lần: lần 2 đổi `amount` nhưng **giữ** `idempotencyKey` | Lần 2: `409 IDEMPOTENCY_CONFLICT`, và **không** sinh transaction mới | Chứng minh key gắn payload |
| 4 | Chữ ký đúng nhưng **timestamp cũ** | `[BRUNO]` | Sửa `X-Timestamp` về quá khứ > 300s (trong Script) | `400 INVALID_SIGNATURE` + lý do `Timestamp outside allowed window` | Chứng minh có chống **replay** |

---

## 5.5. PHIẾU GHI KẾT QUẢ CHO BÁO CÁO (điền khi chạy thật)

| Trường | Giá trị ghi nhận |
|---|---|
| Ngày giờ / timezone | |
| Commit SHA đang deploy | `git rev-parse HEAD` (local) và `docker inspect -f '{{.Config.Image}}' <svc>` (VPS) |
| Run CI (verify) — URL + ID | |
| Run CD (deploy) — URL + ID + service | |
| **TC1** transactionId / 2 bút toán / số dư trước-sau / `ledger-check` / `37` BALANCED / `34` valid | |
| **TC2** phân bố 100 response / số transactionId khác nhau / replay header / entries / số dư trước-sau | |
| **TC3** 5 RPC read-only (ảnh) / RPC posting đã chạy / cách đo latency + giá trị | |
| **TC4a** paymentIntentId / eventId / số dư trước-sau / kết quả gửi lại | |
| **TC4b** transactionId / 2 bút toán / số dư trước-sau | |
| **TC4c** 2 kết quả HMAC âm / `409 IDEMPOTENCY_CONFLICT` / timestamp cũ | |
| Bước không chạy được + lý do (ghi thật, không giấu) | |

### Checklist 5 phút trước khi bắt đầu trình bày

```bash
# [LAPTOP] tunnel đủ 6 dòng (có dòng 9090)? Bruno chọn đúng env?
# [SSH] hạ tầng & service còn sống
ssh deploy@160.187.228.112 'cd /opt/fintech && docker ps --format "{{.Names}}\t{{.Status}}" | head -20'
curl -fsS https://www.fintech-wallet4105.bid/actuator/health | grep '"status":"UP"'
# [SSH] sổ cái cân + audit chain hợp lệ + chưa có báo cáo FAILED
python3 infra/scripts/demo-api.py ledger-check
python3 infra/scripts/demo-api.py audit-chain      # cần đã login tài khoản admin
# [SSH] không còn service nào đang restart
for s in notification-service audit-service transaction-engine; do \
  docker inspect -f '{{.Name}} restarts={{.RestartCount}}' $s; done
```

**Nếu bước nào đỏ, xử lý theo đúng thứ tự này, không đoán:**
1. `docker ps` → có container nào `Restarting`/`Exited` không.
2. `docker logs --tail 50 <service>` → tìm `Exception`/`ERROR` thật.
3. Cổng có khớp không: `grep upstream infra/nginx/conf.d/upstream.conf`.
4. RAM có chạm trần không: `docker stats --no-stream`.
5. Chỉ khi 1–4 đều sạch mới nghi tới logic nghiệp vụ.
