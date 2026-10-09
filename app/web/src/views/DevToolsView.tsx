import { useState } from 'react'
import { isApiError } from '../api/client'
import type { Health, OtpRequestResponse, OtpVerifyResponse } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAsync } from '../state/useAsync'
import { useRequestLog, clearLog } from '../api/log'
import { Badge, Button, Card, Copy, Empty, ErrorNote, Field, Loading, TextInput } from '../ui'

const SERVICES: Array<[string, string, string]> = [
  ['account-ledger-service', '/health/ledger', ':8082'],
  ['transaction-engine', '/health/txengine', ':8081'],
  ['payment-gateway-service', '/health/payment', ':8083'],
  ['notification-service', '/health/notification', ':8084'],
  ['audit-service', '/health/audit', ':8085'],
]

export function DevToolsView() {
  const { call, callUnsigned } = useAuth()
  const log = useRequestLog()

  const health = useAsync(async () => {
    const entries = await Promise.all(
      SERVICES.map(async ([name, path]): Promise<[string, string]> => {
        try {
          const response = await fetch(path)
          if (!response.ok) return [name, `HTTP ${response.status}`]
          const body = (await response.json()) as Health
          return [name, body.status]
        } catch {
          return [name, 'DOWN']
        }
      }),
    )
    return Object.fromEntries(entries) as Record<string, string>
  }, [])

  const [otpRef, setOtpRef] = useState('')
  const [otpCode, setOtpCode] = useState('')
  const [otpToken, setOtpToken] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const [phone, setPhone] = useState('0912345678')
  const [password, setPassword] = useState('S3cret!23')

  async function requestOtp() {
    setBusy(true)
    setError(null)
    try {
      const response = await call<OtpRequestResponse>('POST', '/api/v1/auth/otp/request', undefined, 'otp/request')
      setOtpRef(response.body.otpRef)
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    } finally {
      setBusy(false)
    }
  }

  async function verifyOtp() {
    setBusy(true)
    setError(null)
    try {
      const response = await call<OtpVerifyResponse>('POST', '/api/v1/auth/otp/verify', { otpRef, code: otpCode }, 'otp/verify')
      setOtpToken(response.body.otpToken)
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    } finally {
      setBusy(false)
    }
  }

  async function unsignedLogin() {
    setError(null)
    try {
      const response = await callUnsigned('POST', '/api/v1/auth/login', { phone, password })
      setError(`HTTP ${response.status} — ${response.status === 400 ? 'đúng như mong đợi (thiếu chữ ký)' : 'xem log'}`)
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    }
  }

  return (
    <div className="grid">
      <Card
        title="Trạng thái service"
        subtitle="GET /actuator/health (proxy tới từng cổng)"
        actions={
          <Button variant="ghost" onClick={health.reload}>
            Kiểm tra
          </Button>
        }
      >
        {health.loading && <Loading />}
        <div className="health-grid">
          {SERVICES.map(([name, , port]) => {
            const status = health.data?.[name]
            return (
              <div key={name} className="health-row">
                <span>
                  {name} <span className="muted small">{port}</span>
                </span>
                {status ? <Badge tone={status === 'UP' ? 'ok' : 'bad'}>{status}</Badge> : <span className="muted small">—</span>}
              </div>
            )
          })}
        </div>
      </Card>

      <Card title="OTP cho giao dịch lớn" subtitle="POST /api/v1/auth/otp/request · /otp/verify">
        <ErrorNote error={error} />
        <div className="row gap wrap center">
          <Button onClick={requestOtp} loading={busy}>
            Yêu cầu OTP
          </Button>
          <Field label="otpRef">
            <TextInput value={otpRef} onChange={(event) => setOtpRef(event.target.value)} />
          </Field>
          <Field label="Mã OTP (6 số)">
            <TextInput value={otpCode} onChange={(event) => setOtpCode(event.target.value)} inputMode="numeric" />
          </Field>
          <Button onClick={verifyOtp} loading={busy} disabled={!otpRef || !otpCode}>
            Xác thực
          </Button>
        </div>
        {otpToken && (
          <div className="notice">
            otpToken: <code className="wrap">{otpToken}</code> <Copy value={otpToken} />
          </div>
        )}
        <p className="muted small">
          Không có SMS trong demo — mã OTP được ghi vào log account-ledger-service:
          <code> grep "OTP issued for user" </code>.
        </p>
      </Card>

      <Card title="Demo HMAC" subtitle="Chỉ gateway mới chặn thiếu chữ ký; gọi trực tiếp service thì chữ ký bị bỏ qua.">
        <div className="row gap wrap center">
          <Field label="phone">
            <TextInput value={phone} onChange={(event) => setPhone(event.target.value)} />
          </Field>
          <Field label="password">
            <TextInput value={password} onChange={(event) => setPassword(event.target.value)} />
          </Field>
          <Button variant="ghost" onClick={unsignedLogin}>
            Gửi /auth/login KHÔNG ký
          </Button>
        </div>
        <p className="muted small">
          Để thấy <code>400 INVALID_SIGNATURE</code>: chạy gateway và đặt <code>VITE_USE_GATEWAY=true</code> khi khởi động Vite.
        </p>
      </Card>

      <Card
        title={`Nhật ký request (${log.length})`}
        subtitle="stringToSign = METHOD \\n PATH \\n QUERY \\n TIMESTAMP \\n SHA256(body)"
        actions={
          <Button variant="ghost" onClick={clearLog}>
            Xoá log
          </Button>
        }
      >
        {log.length === 0 && <Empty message="Chưa có request nào." />}
        {log.map((entry) => (
          <details key={entry.id} className={`log-row ${entry.ok ? 'ok' : 'fail'}`}>
            <summary>
              <span className="mono">{entry.method}</span> <span className="mono">{entry.path}</span>{' '}
              <Badge tone={entry.ok ? 'ok' : 'bad'}>{entry.status}</Badge>{' '}
              <span className="muted small">
                {entry.ms}ms{entry.note ? ` · ${entry.note}` : ''}
                {entry.code ? ` · ${entry.code}` : ''}
              </span>
            </summary>
            <div className="muted small">requestId: {entry.requestId ?? '—'}</div>
            <pre className="json">{entry.stringToSign}</pre>
            <pre className="json">signature = {entry.signature}</pre>
          </details>
        ))}
      </Card>
    </div>
  )
}
