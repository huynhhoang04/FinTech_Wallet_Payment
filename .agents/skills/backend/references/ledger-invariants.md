# Ledger Invariants, Locking, and Isolation

Reference for the `backend` skill. This is the correctness core of the project: read it before
touching `account-ledger-service` or any code that posts to it.

## 1. Double-entry rules

1. Every transaction writes **at least two** `ledger_entries` rows: one Debit, one Credit.
2. Per transaction: `SUM(amount) = 0`, and at least one positive and one negative amount.
3. Amounts are signed: Debit negative, Credit positive. `direction` must agree with the sign.
4. Entries are **immutable**: `@Immutable`, no update, no delete. Corrections are new reversing
   entries, never edits.
5. An account's balance is `SUM(amount)` over its entries. `account_balances` is a **cache** of
   that sum, updated in the same transaction, and verified by the reconciliation job.
6. User accounts may not go negative: check `balance + delta >= 0` inside the lock.
   System accounts (`SYSTEM_MASTER`, `FEE_INCOME`, `SUSPENSE`) carry the counterpart and may be
   negative by design.

Worked example - transfer 100,000 VND from A to B with a 1,000 fee:

```
DR  account A   -101,000
CR  account B   +100,000
CR  FEE_INCOME  +  1,000
                ---------
SUM                     0
```

Deposit of 500,000 via a payment provider:

```
DR  SUSPENSE (or BANK_CLEARING)  -500,000
CR  user wallet                  +500,000
```

## 2. Why not `UPDATE balance = balance - X`

The requirement forbids it. A mutable balance column is the classic way money silently drifts:
one missed update, one double-fired retry, and the ledger no longer explains the balance. The
append-only entry log is the source of truth and is auditable.

If you need speed, keep the snapshot table - but it is a **derived cache**, and every read is
still justifiable from the entries.

## 3. Concurrency and deadlock prevention

The scenario: A transfers to B while B transfers to A at the same moment. Naive locking grabs
`A` then `B` in one transaction and `B` then `A` in the other - a classic deadlock.

Rule: **lock accounts in ascending `accountId` order, all at once, before posting.**

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void postTransfer(UUID txId, UUID sourceId, UUID targetId, long amount) {
    List<UUID> ordered = Stream.of(sourceId, targetId).sorted().toList();   // ascending

    for (UUID id : ordered) {
        Account a = accountRepository.lockById(id);       // SELECT ... FOR UPDATE
        if (a.getStatus() != AccountStatus.ACTIVE) throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
    }

    long sourceBalance = balanceRepository.lockBalance(sourceId).getBalance();
    if (sourceBalance + (-amount) < 0) throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);

    ledgerRepository.saveAll(List.of(
        entry(txId, sourceId, -amount, EntryDirection.DEBIT),
        entry(txId, targetId, +amount, EntryDirection.CREDIT)));

    balanceRepository.applyDelta(sourceId, -amount);
    balanceRepository.applyDelta(targetId, +amount);
}
```

```java
// Repository - the lock must be a real row lock, not a JPA findById
@Query(value = "SELECT * FROM accounts WHERE id = :id FOR UPDATE", nativeQuery = true)
Optional<Account> lockById(UUID id);

@Query(value = "SELECT * FROM account_balances WHERE account_id = :id FOR UPDATE", nativeQuery = true)
Optional<AccountBalance> lockBalance(UUID id);
```

Notes:
- Never acquire locks in a different order "just this once" - that is how the deadlock returns.
- A single-account operation (deposit/withdraw) still locks that one account.
- Redisson locks in `transaction-engine` serialize the *request*; the database row lock remains
  the final guarantee. Both layers exist on purpose.

## 4. Isolation levels

| Operation | Isolation | Why |
|---|---|---|
| Read balance / statement | `READ_COMMITTED` | Cheap, no anomaly that matters for a single row read |
| Transfer / deposit / withdraw (multi-row) | `REPEATABLE_READ` | Default for posting; stable snapshot while locking two accounts |
| Reconciliation totals | `REPEATABLE_READ` | Totals must be computed from one consistent snapshot |

`SERIALIZABLE` is the escape hatch if a specific race is proven - it is not the default, because
it increases `40001` retries and slows the money path. If you enable it, you must handle the
retry loop explicitly.

Postgres also has no read locks by default, so `SELECT ... FOR UPDATE` is the mechanism - an
ordinary `SELECT` will not block the other transaction.

## 5. Transactions and retries

- The whole posting is one transaction: entries + snapshot + the transaction status change.
- Do not call Kafka or gRPC inside the DB transaction. Write an outbox row; the publisher runs
  afterwards.
- `PostTransfer` is **not** retried automatically. Re-delivery is safe because the posting is
  keyed by `transaction_id`: if entries already exist for that id, return the previous result
  instead of posting again.

```java
if (entryRepository.existsByTransactionId(txId)) {
    return LedgerResult.previous(txId);       // idempotent replay, no new entries
}
```

## 6. Verification queries

```sql
-- 1. No transaction may be unbalanced (must return zero rows)
SELECT transaction_id, SUM(amount) AS net
FROM ledger_entries
GROUP BY transaction_id
HAVING SUM(amount) <> 0;

-- 2. Snapshot must equal the entry sum (must return zero rows)
SELECT b.account_id, b.balance, COALESCE(SUM(e.amount), 0) AS computed
FROM account_balances b
LEFT JOIN ledger_entries e ON e.account_id = b.account_id
GROUP BY b.account_id, b.balance
HAVING b.balance <> COALESCE(SUM(e.amount), 0);

-- 3. Total of the whole ledger must be zero
SELECT COALESCE(SUM(amount), 0) FROM ledger_entries;
```

Queries 1-3 are what the reconciliation job runs; a non-zero result is the `LedgerImbalance`
alert.

## 7. Anti-patterns (reject in review)

- `UPDATE accounts SET balance = balance - ?` with no entry written.
- Locking accounts in insertion order instead of ascending id order.
- Posting two entries in two separate transactions.
- Catching `InsufficientBalance` and continuing.
- Editing or deleting a `ledger_entries` row to "fix" a mistake.
- Computing a balance with floating point.
- Trusting the snapshot without a reconciliation job to prove it.
