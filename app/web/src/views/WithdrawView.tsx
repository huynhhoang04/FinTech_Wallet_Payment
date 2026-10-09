import { useState } from 'react'
import { isApiError } from '../api/client'
import type { Transaction } from '../api/types'
import { money, dateTime } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAccounts } from '../state/useAccounts'
import { Button, Card, ErrorNote, Field, Json, Money, Select, StatusBadge, TextInput } from '../ui'

export function WithdrawView() {
  const { call } = useAuth()
  const accounts = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [amount, setAmount] = useState('20000')
  const [bankCode, setBankCode] = useState('VCB')
  const [bankAccount, setBankAccount] = useState('0123456789')
  const [otpToken, setOtpToken] = useState('')
  const [key, setKey] = useState(() => crypto.randomUUID())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<Transaction | null>(null)

  const account = accountId || accounts.data?.[0]?.accountId || ''

  async function send() {
    setBusy(true)
    setError(null)
    try {
      const response = await call<Transaction>(
        'POST',
        '/api/v1/transactions/withdraw',
        { accountId: account, amount: Number(amount), currency: 'VND', bankCode, bankAccount, otpToken: otpToken || undefined },
        `withdraw (key ${key.slice(0, 8)})`,
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

  return (
    <div className="grid">
      <Card title="Rút tiền" subtitle="POST /api/v1/transactions/withdraw — trừ ví, chuyển ra ngân hàng (SUSPENSE)">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void send()
          }}
        >
          <Field label="Ví nguồn">
            <Select value={account} onChange={(event) => setAccountId(event.target.value)}>
              {accounts.data?.map((item) => (
                <option key={item.accountId} value={item.accountId}>
                  {item.accountNumber} — {item.balance ? money(item.balance.available, item.currency) : '?'}
                </option>
              ))}
            </Select>
          </Field>
          <div className="row gap">
            <Field label="Số tiền (VND)">
              <TextInput value={amount} onChange={(event) => setAmount(event.target.value)} inputMode="numeric" />
            </Field>
            <Field label="Mã ngân hàng">
              <TextInput value={bankCode} onChange={(event) => setBankCode(event.target.value)} />
            </Field>
            <Field label="Số tài khoản nhận">
              <TextInput value={bankAccount} onChange={(event) => setBankAccount(event.target.value)} />
            </Field>
          </div>
          <Field label="OTP token (chỉ cần khi ≥ 10.000.000)">
            <TextInput value={otpToken} onChange={(event) => setOtpToken(event.target.value)} />
          </Field>

          <ErrorNote error={error} />

          <div className="row gap wrap">
            <Button type="submit" loading={busy}>
              Rút (key {key.slice(0, 8)})
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
        <Card title="Kết quả">
          <div className="row gap wrap center">
            <StatusBadge status={result.status} />
            <Money amount={result.amount} currency={result.currency} />
            <span className="muted small">{dateTime(result.createdAt)}</span>
          </div>
          {result.failureReason && <div className="error-note">{result.failureReason}</div>}
          <details>
            <summary className="muted small">JSON đầy đủ</summary>
            <Json value={result} />
          </details>
        </Card>
      )}
    </div>
  )
}
