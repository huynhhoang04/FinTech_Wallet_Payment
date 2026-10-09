import { useState } from 'react'
import { isApiError } from '../api/client'
import { useAuth } from '../state/AuthProvider'
import { Button, Card, ErrorNote, Field, TextInput } from '../ui'

type Tab = 'login' | 'register'

export function AuthView() {
  const { login, register, busy } = useAuth()
  const [tab, setTab] = useState<Tab>('login')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const [phone, setPhone] = useState('0912345678')
  const [password, setPassword] = useState('S3cret!23')
  const [email, setEmail] = useState('a@b.com')
  const [fullName, setFullName] = useState('Nguyen Van A')

  async function onLogin() {
    setError(null)
    setNotice(null)
    try {
      await login({ phone, password })
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    }
  }

  async function onRegister() {
    setError(null)
    setNotice(null)
    try {
      const result = await register({ phone, email, password, fullName })
      setNotice(`Đã tạo ví ${result.accountNumber} (userId ${result.userId}). Giờ đăng nhập.`)
      setTab('login')
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-brand">
        <h1>FinTech Wallet</h1>
        <p className="muted">Dev client — chạy local, gọi trực tiếp các service (không qua gateway).</p>
      </div>
      <Card
        title={tab === 'login' ? 'Đăng nhập' : 'Đăng ký'}
        actions={
          <div className="seg">
            <button className={tab === 'login' ? 'seg-on' : ''} onClick={() => setTab('login')}>
              Đăng nhập
            </button>
            <button className={tab === 'register' ? 'seg-on' : ''} onClick={() => setTab('register')}>
              Đăng ký
            </button>
          </div>
        }
      >
        <Field label="Số điện thoại">
          <TextInput value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="0912345678" />
        </Field>
        {tab === 'register' && (
          <>
            <Field label="Email">
              <TextInput value={email} onChange={(event) => setEmail(event.target.value)} placeholder="a@b.com" />
            </Field>
            <Field label="Họ tên">
              <TextInput value={fullName} onChange={(event) => setFullName(event.target.value)} />
            </Field>
          </>
        )}
        <Field label="Mật khẩu">
          <TextInput type="password" value={password} onChange={(event) => setPassword(event.target.value)} />
        </Field>

        <ErrorNote error={error} />
        {notice && <div className="notice">{notice}</div>}

        {tab === 'login' ? (
          <Button onClick={onLogin} loading={busy}>
            Đăng nhập
          </Button>
        ) : (
          <Button onClick={onRegister} loading={busy}>
            Tạo tài khoản + ví VND
          </Button>
        )}

        <p className="muted small">
          Tài khoản seed: <code>0912345678</code> (admin) và <code>0987654321</code>, mật khẩu <code>S3cret!23</code>.
        </p>
      </Card>
    </div>
  )
}
