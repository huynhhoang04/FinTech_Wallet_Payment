import { useState } from 'react'
import { isApiError } from '../api/client'
import type { LedgerEntriesInfo, Transaction } from '../api/types'
import { money, dateTime } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAccounts } from '../state/useAccounts'
import { Button, Card, ErrorNote, Field, Json, Money, Select, StatusBadge, TextInput } from '../ui'

export function TransferView() {
  const { call } = useAuth()
  const accounts = useAccounts()
  const [sourceAccountId, setSourceAccountId] = useState('')
  const [targetAccountNumber, setTarget] = useState('9704790881243')
  const [amount, setAmount] = useState('10000')
  const [description, setDescription] = useState('Tra tien an')
  const [otpToken, setOtpToken] = useState('')
  const [key, setKey] = useState(() => crypto.randomUUID())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Transaction | null>(null)
  const [entries, setEntries] = useState<LedgerEntriesInfo | null>(null)

  const source = sourceAccountId || accounts.data?.[0]?.accountId || ''
  const target = accounts.data?.find((account) => account.accountId === targetAccountNumber)

  async function send() {
    setBusy(true)
    setError(null)
    setEntries(null)
    try {
      const response = await call<Transaction>(
        'POST',
        '/api/v1/transactions/transfer',
        {
          sourceAccountId: source,
          targetAccountNumber,
          amount: Number(amount),
          currency: 'VND',
          description,
          otpToken: otpToken || undefined,
        },
        `transfer (key ${key.slice(0, 8)})`,
      )
      setResult(response.body)
      accounts.reload()
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
      setResult(null)
    } finally {
      setBusy(false)
    }
  }

  async function loadEntries() {
    if (!result) return
    try {
      const response = await call<LedgerEntriesInfo>('GET', `/api/v1/transactions/${result.transactionId}/ledger-entries`, undefined, 'ledger-entries')
      setEntries(response.body)
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    }
  }

  return (
    <div className="grid">
      <Card title="Chuyển tiền" subtitle="POST /api/v1/transactions/transfer — bắt buộc X-Idempotency-Key">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void send()
          }}
        >
          <Field label="Ví nguồn">
            <Select value={source} onChange={(event) => setSourceAccountId(event.target.value)}>
              {accounts.data?.map((account) => (
                <option key={account.accountId} value={account.accountId}>
                  {account.accountNumber} — {account.balance ? money(account.balance.available, account.currency) : '?'}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Số tài khoản đích" hint={target ? `Ví đích: ${target.accountNumber}` : undefined}>
            <TextInput value={targetAccountNumber} onChange={(event) => setTarget(event.target.value)} />
          </Field>
          <div className="row gap">
            <Field label="Số tiền (VND)">
              <TextInput value={amount} onChange={(event) => setAmount(event.target.value)} inputMode="numeric" />
            </Field>
            <Field label="Mô tả">
              <TextInput value={description} onChange={(event) => setDescription(event.target.value)} />
            </Field>
          </div>
          <Field label="OTP token (chỉ cần khi ≥ 10.000.000)" hint="Lấy OTP ở tab Dev tools → OTP.">
            <TextInput value={otpToken} onChange={(event) => setOtpToken(event.target.value)} placeholder="(bỏ trống nếu giao dịch nhỏ)" />
          </Field>

          <ErrorNote error={error} />

          <div className="row gap wrap">
            <Button type="submit" loading={busy}>
              Gửi (key {key.slice(0, 8)})
            </Button>
            <Button type="button" variant="ghost" onClick={() => void send()} disabled={busy || !result}>
              Gửi lại (cùng key)
            </Button>
            <Button type="button" variant="ghost" onClick={() => setKey(crypto.randomUUID())} disabled={busy}>
              Key mới
            </Button>
          </div>
        </form>
      </Card>

      {result && (
        <Card
          title="Kết quả"
          actions={
            <Button variant="ghost" onClick={loadEntries}>
              Xem bút toán kép
            </Button>
          }
        >
          <div className="row gap wrap center">
            <StatusBadge status={result.status} />
            <Money amount={result.amount} currency={result.currency} />
            <span className="muted small">{dateTime(result.createdAt)}</span>
          </div>
          {result.failureReason && <div className="error-note">{result.failureReason}</div>}
          <div className="muted small">
            transactionId {result.transactionId} · ledger {result.ledgerTransactionId ?? '—'}
          </div>
          {entries && (
            <table className="table">
              <thead>
                <tr>
                  <th>Tài khoản</th>
                  <th>Hướng</th>
                  <th className="right">Số tiền</th>
                  <th className="right">Số dư sau</th>
                </tr>
              </thead>
              <tbody>
                {entries.entries.map((entry) => (
                  <tr key={entry.entryId}>
                    <td className="mono">{entry.entryId.slice(0, 8)}…</td>
                    <td>{entry.direction}</td>
                    <td className="right">
                      <Money amount={entry.amountSigned} signed />
                    </td>
                    <td className="right">{money(entry.balanceAfter)}</td>
                  </tr>
                ))}
                <tr>
                  <td colSpan={4} className="right muted">
                    Σ = {entries.sum} (phải bằng 0)
                  </td>
                </tr>
              </tbody>
            </table>
          )}
          <details>
            <summary className="muted small">JSON đầy đủ</summary>
            <Json value={result} />
          </details>
        </Card>
      )}
    </div>
  )
}
