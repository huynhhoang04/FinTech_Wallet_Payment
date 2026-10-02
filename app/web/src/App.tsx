import { useState } from 'react'
import { call, type ApiCall } from './api/client'

type LoginResponse = { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number }
type OtpResponse = { otpRef: string; expiresIn: number }

type Step = { label: string; result: ApiCall }

export default function App() {
  const [phone, setPhone] = useState('0912345678')
  const [password, setPassword] = useState('S3cret!23')
  const [tokens, setTokens] = useState<LoginResponse | null>(null)
  const [otpRef, setOtpRef] = useState('')
  const [otpCode, setOtpCode] = useState('')
  const [steps, setSteps] = useState<Step[]>([])
  const [busy, setBusy] = useState(false)

  async function run(label: string, action: () => Promise<ApiCall>) {
    setBusy(true)
    try {
      const result = await action()
      setSteps((previous) => [{ label, result }, ...previous])
    } finally {
      setBusy(false)
    }
  }

  const login = () =>
    run('POST /api/v1/auth/login', async () => {
      const result = await call('POST', '/api/v1/auth/login', { phone, password })
      if (result.ok) {
        setTokens(result.body as LoginResponse)
      }
      return result
    })

  const me = () => run('GET /api/v1/auth/me', () => call('GET', '/api/v1/auth/me', undefined, tokens?.accessToken))

  const requestOtp = () =>
    run('POST /api/v1/auth/otp/request', async () => {
      const result = await call('POST', '/api/v1/auth/otp/request', undefined, tokens?.accessToken)
      if (result.ok) {
        setOtpRef((result.body as OtpResponse).otpRef)
      }
      return result
    })

  const verifyOtp = () =>
    run('POST /api/v1/auth/otp/verify', () =>
      call('POST', '/api/v1/auth/otp/verify', { otpRef, code: otpCode }, tokens?.accessToken),
    )

  const loginWithoutSignature = () =>
    run('POST /api/v1/auth/login (khong ky HMAC)', async () => {
      const response = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone, password }),
      })
      return {
        status: response.status,
        ok: response.ok,
        requestId: response.headers.get('X-Request-Id'),
        stringToSign: '(khong ky)',
        signature: '(khong co)',
        body: JSON.parse((await response.text()) || 'null'),
      }
    })

  return (
    <main>
      <h1>FinTech Wallet — dev client</h1>
      <p className="hint">
        Gọi qua gateway (Vite proxy → :8080). Mọi request đều được ký HMAC-SHA256.
        <br />
        Log luồng ở backend: <code>POST /api/v1/auth/login -&gt; AuthController.login</code> rồi các
        class/method tham gia, thụt lề theo độ sâu.
      </p>

      <fieldset>
        <legend>Đăng nhập</legend>
        <label>
          <span>phone</span>
          <input value={phone} onChange={(event) => setPhone(event.target.value)} />
        </label>
        <label>
          <span>password</span>
          <input value={password} onChange={(event) => setPassword(event.target.value)} />
        </label>
        <button onClick={login} disabled={busy}>
          POST /auth/login
        </button>
        <button onClick={loginWithoutSignature} disabled={busy}>
          Gửi thiếu chữ ký (mong đợi 400)
        </button>
      </fieldset>

      <fieldset>
        <legend>Đã đăng nhập thì gọi tiếp</legend>
        <button onClick={me} disabled={busy || !tokens}>
          GET /auth/me
        </button>
        <button onClick={requestOtp} disabled={busy || !tokens}>
          POST /auth/otp/request
        </button>
        <label>
          <span>
            otpRef {otpRef && <em>(lấy từ response)</em>} — mã OTP nằm trong log ledger, grep "OTP issued"
          </span>
          <input value={otpRef} onChange={(event) => setOtpRef(event.target.value)} />
        </label>
        <label>
          <span>code</span>
          <input value={otpCode} onChange={(event) => setOtpCode(event.target.value)} />
        </label>
        <button onClick={verifyOtp} disabled={busy || !otpRef}>
          POST /auth/otp/verify
        </button>
      </fieldset>

      {tokens && (
        <fieldset>
          <legend>Token</legend>
          <div className="tokens">accessToken: {tokens.accessToken}</div>
          <div className="tokens">refreshToken: {tokens.refreshToken}</div>
          <div className="line">
            {tokens.tokenType}, expiresIn {tokens.expiresIn}s
          </div>
        </fieldset>
      )}

      <fieldset>
        <legend>Request đã gửi ({steps.length})</legend>
        {steps.length === 0 && <div className="line">Chưa có request nào.</div>}
        {steps.map((step, index) => (
          <div key={index} className={`step ${step.result.ok ? 'ok' : 'fail'}`}>
            <div className="line">
              <b>{step.label}</b> → HTTP {step.result.status}
            </div>
            {step.result.requestId && <div className="line">X-Request-Id: {step.result.requestId}</div>}
            <details>
              <summary className="line">stringToSign &amp; chữ ký</summary>
              <pre>{step.result.stringToSign}</pre>
              <pre>signature = {step.result.signature}</pre>
            </details>
            <pre>{JSON.stringify(step.result.body, null, 2)}</pre>
          </div>
        ))}
      </fieldset>
    </main>
  )
}
