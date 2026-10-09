import { useState } from 'react'
import { isApiError } from '../api/client'
import type { PaymentIntent, PaymentProvider, Transaction, WebhookResponse } from '../api/types'
import { dateTime, money } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAccounts } from '../state/useAccounts'
import { Button, Card, ErrorNote, Field, Json, Money, Select, StatusBadge, TextInput } from '../ui'

export function DepositView() {
  const { call } = useAuth()
  const accounts = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [amount, setAmount] = useState('50000')
  const [provider, setProvider] = useState<PaymentProvider>('VNPAY')
  const [key, setKey] = useState(() => crypto.randomUUID())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [created, setCreated] = useState<Transaction | null>(null)
  const [intent, setIntent] = useState<PaymentIntent | null>(null)
  const [callback, setCallback] = useState<WebhookResponse | null>(null)

  const account = accountId || accounts.data?.[0]?.accountId || ''

  async function createDeposit() {
    setBusy(true)
    setError(null)
    setIntent(null)
    setCallback(null)
    try {
      const response = await call<Transaction>(
        'POST',
        '/api/v1/transactions/deposit',
        { accountId: account, amount: Number(amount), currency: 'VND', provider },
        `deposit (key ${key.slice(0, 8)})`,
      )
      setCreated(response.body)
      if (response.body.paymentIntentId) void refresh(response.body.paymentIntentId)
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
      setCreated(null)
    } finally {
      setBusy(false)
    }
  }

  async function refresh(paymentIntentId = created?.paymentIntentId ?? '') {
    if (!paymentIntentId) return
    try {
      const result = await call<PaymentIntent>('GET', `/api/v1/payments/intents/${paymentIntentId}`, undefined, 'intent')
      setIntent(result.body)
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    }
  }

  async function simulateCallback() {
    if (!created?.paymentIntentId) return
    setBusy(true)
    setError(null)
    try {
      const result = await call<WebhookResponse>('POST', `/internal/payments/${created.paymentIntentId}/simulate-callback`, undefined, 'simulate-callback')
      setCallback(result.body)
      await refresh()
      if (created.transactionId) {
        const refreshed = await call<Transaction>('GET', `/api/v1/transactions/${created.transactionId}`, undefined, 'transaction')
        setCreated(refreshed.body)
      }
      accounts.reload()
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid">
      <Card title="Nạp tiền" subtitle="POST /api/v1/transactions/deposit → tạo PaymentIntent, ví chỉ được cộng khi provider xác nhận">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            void createDeposit()
          }}
        >
          <Field label="Ví nhận">
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
            <Field label="Provider">
              <Select value={provider} onChange={(event) => setProvider(event.target.value as PaymentProvider)}>
                <option value="VNPAY">VNPAY</option>
                <option value="STRIPE">STRIPE</option>
                <option value="VIETQR">VIETQR</option>
              </Select>
            </Field>
          </div>

          <ErrorNote error={error} />

          <div className="row gap wrap">
            <Button type="submit" loading={busy}>
              Tạo phiên nạp (key {key.slice(0, 8)})
            </Button>
            <Button type="button" variant="ghost" onClick={() => setKey(crypto.randomUUID())} disabled={busy}>
              Key mới
            </Button>
          </div>
        </form>
      </Card>

      {created && (
        <Card
          title="Phiên nạp tiền"
          actions={
            <div className="row gap">
              <Button variant="ghost" onClick={() => void refresh()}>
                Tải trạng thái
              </Button>
              <Button onClick={simulateCallback} loading={busy} disabled={!created.paymentIntentId}>
                Giả lập ngân hàng callback
              </Button>
            </div>
          }
        >
          <div className="row gap wrap center">
            <StatusBadge status={created.status} />
            {intent && <StatusBadge status={intent.status} />}
            <Money amount={created.amount} currency={created.currency} />
          </div>
          <dl className="kv">
            <dt>paymentIntentId</dt>
            <dd className="mono">{created.paymentIntentId ?? '—'}</dd>
            <dt>checkoutUrl</dt>
            <dd>
              {created.checkoutUrl ? (
                <a href={created.checkoutUrl} target="_blank" rel="noreferrer" className="link">
                  {created.checkoutUrl}
                </a>
              ) : (
                '—'
              )}
            </dd>
            {intent?.qrContent && (
              <>
                <dt>qrContent</dt>
                <dd className="mono small">{intent.qrContent}</dd>
              </>
            )}
          </dl>

          {callback && (
            <div className="notice">
              Webhook: received={String(callback.received)} · eventId={callback.eventId} · duplicate={String(callback.duplicate)}
            </div>
          )}

          <p className="muted small">
            Ví chỉ tăng khi consumer Kafka <code>payment.succeeded</code> gọi ledger. Local không có Kafka (hoặc
            <code> app.payment.publish-enabled=false</code>) nên giao dịch sẽ đứng ở <code>PENDING</code> — chạy kèm
            docker-compose (Kafka) để thấy số dư tăng.
          </p>

          <details>
            <summary className="muted small">JSON giao dịch</summary>
            <Json value={created} />
          </details>
        </Card>
      )}
    </div>
  )
}
