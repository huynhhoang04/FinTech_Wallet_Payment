# 03 — JPA SCHEMA (ENTITY THEO SERVICE)

> **Cách tiếp cận:** dùng **JPA/Hibernate làm nguồn chân lý cho schema** (`spring.jpa.hibernate.ddl-auto=update`) → không viết file migration SQL riêng.
> Hệ quả: mọi thứ **JPA diễn đạt được** (bảng, cột, kiểu, unique, index, FK nội bộ, enum) mới có trong DB. Các ràng buộc JPA không diễn đạt được (vd: `SUM(amount) = 0` theo transaction) **phải** enforce ở tầng service + job đối chiếu.
>
> ⚠️ Ghi chú kỹ thuật (không bàn sâu): `ddl-auto=update` **không xoá cột** và không đổi kiểu an toàn → khi demo là ổn, nhưng nếu muốn an toàn dữ liệu thật thì đổi sang `validate` + Flyway. Đồ án giữ `update` theo yêu cầu.

---

## 0. QUY ƯỚC CHUNG (ÁP DỤNG MỌI SERVICE)

| Quy ước | Giá trị |
|---|---|
| Khóa chính | `UUID`, `@GeneratedValue(strategy = GenerationType.UUID)` |
| Tiền tệ | `BIGINT` — đơn vị nhỏ nhất (VND = đồng). **Không** dùng `double/float` |
| Thời gian | `Instant`, audit tự động qua `@CreatedDate` / `@LastModifiedDate` |
| Enum | `@Enumerated(EnumType.STRING)` |
| Optimistic lock | `@Version long version` trên entity mutable |
| Đặt tên bảng/cột | snake_case (Spring PhysicalNamingStrategy tự chuyển) |
| JSON | `@JdbcTypeCode(SqlTypes.JSON)` → cột `jsonb` |
| Quan hệ liên service | **Không FK chéo DB**, chỉ lưu `UUID` tham chiếu |
| Nguyên tắc | Mỗi service **sở hữu DB riêng**, không service nào map entity của service khác |

**Cấu hình chung (`application.yml`):**
```yaml
spring:
  jpa:
    hibernate.ddl-auto: update
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
```

**Base entity dùng chung:**
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate  @Column(nullable = false, updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(nullable = false) private Instant updatedAt;
}
```
Bật `@EnableJpaAuditing` ở `@SpringBootApplication` của mỗi service có DB.

---

## 1. `eureka-server`

**Không có JPA. Không có DB.** Không chứa entity nào.

---

## 2. `api-gateway`

**Không có JPA. Không có DB.** Toàn bộ state (rate limit counter, blacklist token) nằm ở **Redis**, không persist schema.

---

## 3. `transaction-engine` — database `tx_db`

### 3.1. `Transaction` (entity trung tâm)
```java
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "ix_tx_account_src", columnList = "source_account_id"),
    @Index(name = "ix_tx_account_tgt", columnList = "target_account_id"),
    @Index(name = "ix_tx_status_created", columnList = "status, created_at"),
    @Index(name = "ix_tx_idem", columnList = "idempotency_key", unique = true)
})
public class Transaction extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private TransactionType type;          // TRANSFER | DEPOSIT | WITHDRAW

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private TransactionStatus status;      // PENDING | PROCESSING | SUCCESS | FAILED | REVERSED

    @Column(name = "source_account_id")      private UUID sourceAccountId;
    @Column(name = "target_account_id")      private UUID targetAccountId;

    @Column(nullable = false) private Long amount;     // đơn vị nhỏ nhất
    @Column(nullable = false) private Long fee;
    @Column(nullable = false, length = 3) private String currency;

    @Column(length = 255) private String description;
    @Column(name = "failure_reason", length = 255) private String failureReason;
    @Column(name = "ledger_transaction_id") private UUID ledgerTransactionId;

    @Version private long version;
}
```
| Cột | Null | Ghi chú |
|---|---|---|
| `idempotency_key` | ✗ | **UNIQUE** — chốt chống trừ tiền 2 lần ở tầng DB |
| `status` | ✗ | State machine, chỉ tiến theo luồng hợp lệ |
| `amount`, `fee` | ✗ | `> 0` (validate ở service) |
| `ledger_transaction_id` | ✓ | ID giao dịch do Ledger trả về, dùng để truy vết |

### 3.2. `FraudAlert` (rule engine nhúng trong service này)
```java
@Entity
@Table(name = "fraud_alerts", indexes = {
    @Index(name = "ix_fraud_tx", columnList = "transaction_id"),
    @Index(name = "ix_fraud_created", columnList = "created_at")
})
public class FraudAlert extends BaseEntity {
    @Column(name = "transaction_id") private UUID transactionId;
    @Column(name = "rule_code", nullable = false, length = 32) private String ruleCode; // VELOCITY, AMOUNT_LIMIT, NEW_DEVICE, GEO_ANOMALY
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Severity severity; // LOW | MEDIUM | HIGH
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Decision decision; // ALLOW | REVIEW | BLOCK
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String details;
}
```

### 3.3. `OutboxEvent` (Transactional Outbox → publish Kafka an toàn)
```java
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "ix_outbox_status", columnList = "status, created_at")
})
public class OutboxEvent extends BaseEntity {
    @Column(name = "aggregate_type", nullable = false, length = 32) private String aggregateType; // TRANSACTION
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 48) private String eventType;         // tx.completed
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private OutboxStatus status; // NEW | PUBLISHED | FAILED
    @Column(name = "published_at") private Instant publishedAt;
}
```
> **Vì sao có Outbox:** ghi `Transaction` và event trong **cùng 1 transaction DB**, sau đó scheduler đẩy sang Kafka → không bao giờ mất event khi Kafka chết giữa chừng. Đây là điểm cộng "zero financial error".

> **Idempotency key lưu ở Redis** (TTL 24h), không có bảng riêng. Bảng `transactions.idempotency_key UNIQUE` là chốt chặn thứ 2 ở tầng DB.

---

## 4. `account-ledger-service` — database `ledger_db`

### 4.1. `Account`
```java
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "ix_acc_owner", columnList = "owner_user_id"),
    @Index(name = "ix_acc_type_status", columnList = "type, status")
})
public class Account extends BaseEntity {

    @Column(name = "owner_user_id") private UUID ownerUserId;   // null = tài khoản hệ thống

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private AccountType type;                  // USER | SYSTEM | FEE | SUSPENSE

    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private AccountStatus status;              // ACTIVE | LOCKED | CLOSED

    @Column(name = "account_number", nullable = false, unique = true, length = 24)
    private String accountNumber;

    @Version private long version;
}
```
> Bắt buộc seed sẵn các tài khoản hệ thống: `SYSTEM_MASTER`, `FEE_INCOME`, `SUSPENSE` (dùng làm đối ứng cho luồng nạp/rút và phí).

### 4.2. `LedgerEntry` (bảng bất biến — trái tim của kế toán kép)
```java
@Entity
@Immutable                                  // Hibernate: KHÔNG cho UPDATE
@Table(name = "ledger_entries", indexes = {
    @Index(name = "ix_le_tx", columnList = "transaction_id"),
    @Index(name = "ix_le_account_created", columnList = "account_id, created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class LedgerEntry {

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false) private UUID transactionId;
    @Column(name = "account_id", nullable = false, updatable = false)     private UUID accountId;

    /** Số có dấu: DEBIT < 0, CREDIT > 0. Bất biến: SUM(amount) theo transaction_id = 0 */
    @Column(nullable = false, updatable = false) private Long amount;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8, updatable = false)
    private EntryDirection direction;          // DEBIT | CREDIT

    @Column(nullable = false, length = 3, updatable = false) private String currency;
    @Column(name = "balance_after", nullable = false, updatable = false) private Long balanceAfter;

    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb", updatable = false) private String metadata;
}
```
| Quy tắc | Diễn đạt bằng |
|---|---|
| Mỗi giao dịch có ≥ 2 dòng, tổng = 0 | Service enforce + job đối chiếu (JPA không diễn đạt được CHECK tổng hợp) |
| Không sửa/xoá entry | `@Immutable` |
| `direction` khớp dấu `amount` | Validate khi tạo entry |
| Truy vết | `populate: metadata = { "ref": "TX-...", "channel": "TRANSFER" }` |

### 4.3. `AccountBalance` (snapshot để đọc nhanh)
```java
@Entity
@Table(name = "account_balances", uniqueConstraints =
    @UniqueConstraint(name = "uq_balance_account_currency", columnNames = {"account_id", "currency"}))
public class AccountBalance extends BaseEntity {
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(nullable = false) private Long balance;     // = SUM(ledger_entries.amount) của account
    @Column(nullable = false, length = 3) private String currency;
    @Version private long version;
}
```
> **Vì sao có snapshot:** số dư vẫn là **suy ra từ ledger**, nhưng đọc realtime mỗi request thì đắt. Snapshot được cập nhật **trong cùng transaction** với việc ghi entry; job đối chiếu (audit-service) sẽ so `snapshot == SUM(entries)` để phát hiện lệch.
> Bất biến kèm theo: `balance >= 0` với `AccountType.USER` (chặn âm tiền).

### 4.4. Bảng phụ trợ khoá
```java
@Entity
@Table(name = "account_locks")   // khoá theo thứ tự id tăng dần; bảng này để theo dõi lock đang giữ (debug/tracing)
```
> Thực tế khoá chính là `SELECT ... FOR UPDATE` trong transaction. Bảng này chỉ phục vụ quan sát/demo chống deadlock, có thể bỏ nếu không cần.

---

## 5. `payment-gateway-service` — database `payment_db`

### 5.1. `PaymentIntent`
```java
@Entity
@Table(name = "payment_intents", indexes = {
    @Index(name = "ix_pi_idem", columnList = "idempotency_key", unique = true),
    @Index(name = "ix_pi_status_created", columnList = "status, created_at"),
    @Index(name = "ix_pi_provider_ref", columnList = "provider, provider_ref")
})
public class PaymentIntent extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64) private String idempotencyKey;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "wallet_account_id", nullable = false) private UUID walletAccountId;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Provider provider; // VNPAY | STRIPE | VIETQR
    @Column(name = "provider_ref", length = 64) private String providerRef;

    @Column(nullable = false) private Long amount;
    @Column(nullable = false, length = 3) private String currency;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private PaymentStatus status;   // CREATED | PENDING | SUCCEEDED | FAILED | EXPIRED

    @Column(name = "checkout_url", length = 512) private String checkoutUrl;
    @Column(name = "expires_at") private Instant expiresAt;
    @Version private long version;
}
```

### 5.2. `WebhookLog`
```java
@Entity
@Table(name = "webhook_logs", uniqueConstraints =
    @UniqueConstraint(name = "uq_webhook_provider_event", columnNames = {"provider", "event_id"}))
public class WebhookLog extends BaseEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Provider provider;
    @Column(name = "event_id", nullable = false, length = 128) private String eventId;  // chống trùng webhook
    @Column(name = "signature", length = 512) private String signature;
    @Column(nullable = false) private boolean signatureValid;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String payload;
    @Column(nullable = false) private boolean processed;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "payment_intent_id") private UUID paymentIntentId;
}
```
> **UNIQUE(provider, event_id)** là chốt chặn chống xử lý lặp webhook ở tầng DB (Redis check trước, DB constraint chốt sau).

---

## 6. `notification-service` — database `notification_db`

### 6.1. `NotificationLog`
```java
@Entity
@Table(name = "notification_logs", indexes = {
    @Index(name = "ix_nf_user_created", columnList = "user_id, created_at"),
    @Index(name = "ix_nf_status", columnList = "status")
})
public class NotificationLog extends BaseEntity {
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private Channel channel;   // EMAIL | PUSH | SMS
    @Column(name = "template_code", nullable = false, length = 32) private String templateCode;   // BALANCE_CHANGED, PAYMENT_SUCCESS, RISK_ALERT
    @Column(nullable = false, length = 255) private String recipient;
    @Column(length = 255) private String subject;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8) private SendStatus status; // PENDING | SENT | FAILED
    @Column(name = "provider_message_id", length = 128) private String providerMessageId;
    @Column(name = "error_message", length = 512) private String errorMessage;
    @Column(name = "related_transaction_id") private UUID relatedTransactionId;
    @Column(name = "sent_at") private Instant sentAt;
}
```

### 6.2. `DeadLetterEvent` (event xử lý lỗi quá số lần retry)
```java
@Entity
@Table(name = "dead_letter_events")
public class DeadLetterEvent extends BaseEntity {
    @Column(name = "topic", nullable = false, length = 64) private String topic;
    @Column(name = "event_key", length = 64) private String eventKey;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String payload;
    @Column(name = "error_message", length = 1024) private String errorMessage;
    @Column(name = "retry_count", nullable = false) private int retryCount;
}
```

---

## 7. `audit-service` — database `audit_db`

### 7.1. `AuditLog` (append-only, có chuỗi hash)
```java
@Entity
@Immutable
@Table(name = "audit_logs", indexes = {
    @Index(name = "ix_audit_event", columnList = "event_id", unique = true),
    @Index(name = "ix_audit_aggregate", columnList = "aggregate_id"),
    @Index(name = "ix_audit_occurred", columnList = "occurred_at")
})
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "event_id", nullable = false, unique = true) private UUID eventId;   // chống ghi trùng khi Kafka redeliver
    @Column(name = "event_type", nullable = false, length = 48) private String eventType;
    @Column(name = "aggregate_id") private UUID aggregateId;
    @Column(name = "actor", length = 64) private String actor;                          // userId hoặc SYSTEM
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String payload;

    @Column(name = "prev_hash", length = 64) private String prevHash;                   // hash bản ghi trước
    @Column(name = "hash", nullable = false, length = 64) private String hash;          // SHA-256(prevHash + payload)

    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;         // thời điểm event phát sinh
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;
}
```
> **Chuỗi hash** cho phép phát hiện sửa đổi: `hash_n = SHA256(hash_{n-1} + canonical(payload))`. Có endpoint verify toàn chuỗi.

### 7.2. `ReconciliationReport`
```java
@Entity
@Table(name = "reconciliation_reports", uniqueConstraints =
    @UniqueConstraint(name = "uq_recon_date", columnNames = {"report_date"}))
public class ReconciliationReport extends BaseEntity {
    @Column(name = "report_date", nullable = false) private LocalDate reportDate;
    @Column(name = "entry_count", nullable = false) private long entryCount;
    @Column(name = "total_debit", nullable = false)  private Long totalDebit;    // tổng trị tuyệt đối bên Nợ
    @Column(name = "total_credit", nullable = false) private Long totalCredit;
    @Column(name = "difference", nullable = false)   private Long difference;    // debit - credit, PHẢI = 0
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private ReconStatus status;   // BALANCED | IMBALANCED | FAILED
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String details;
}
```

---

## 8. BẢNG TỔNG HỢP ENTITY

| Service | Database | Entity |
|---|---|---|
| `eureka-server` | — | (không có) |
| `api-gateway` | — | (không có) |
| `transaction-engine` | `tx_db` | `Transaction`, `FraudAlert`, `OutboxEvent` |
| `account-ledger-service` | `ledger_db` | `Account`, `LedgerEntry`, `AccountBalance`, `AccountLock` |
| `payment-gateway-service` | `payment_db` | `PaymentIntent`, `WebhookLog` |
| `notification-service` | `notification_db` | `NotificationLog`, `DeadLetterEvent` |
| `audit-service` | `audit_db` | `AuditLog`, `ReconciliationReport` |

**Tổng: 14 entity trên 5 database**, cô lập theo service, chỉ tham chiếu nhau bằng `UUID` (không FK chéo).

---

## 9. SEED DATA BẮT BUỘC
| Database | Dữ liệu seed |
|---|---|
| `ledger_db` | `SYSTEM_MASTER`, `FEE_INCOME`, `SUSPENSE` accounts |
| `tx_db` | (rỗng) |
| `payment_db` | (rỗng) |
| `notification_db` | (rỗng) |
| `audit_db` | (rỗng) |

Seed thực hiện bằng `CommandLineRunner` (chạy idempotent: chỉ tạo nếu chưa có) hoặc `data.sql` — không dùng migration tool.

**Tài liệu tiếp theo:** `04-api-contract.md`.
