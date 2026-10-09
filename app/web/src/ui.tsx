import { useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react'
import { money } from './api/types'

export function Card({ title, subtitle, actions, children, className = '' }: { title?: ReactNode; subtitle?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card-head">
          <div>
            {title && <h2>{title}</h2>}
            {subtitle && <p className="muted small">{subtitle}</p>}
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  )
}

export function Field({ label, hint, children }: { label: ReactNode; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`input ${props.className ?? ''}`} />
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`input ${props.className ?? ''}`} />
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'ghost' | 'danger'; loading?: boolean }

export function Button({ variant = 'primary', loading = false, children, disabled, ...rest }: ButtonProps) {
  return (
    <button {...rest} className={`btn btn-${variant} ${rest.className ?? ''}`} disabled={disabled || loading}>
      {loading ? <span className="spinner" /> : null}
      {children}
    </button>
  )
}

export function Badge({ tone = 'neutral', children }: { tone?: 'neutral' | 'ok' | 'warn' | 'bad' | 'info'; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

const TONES: Record<string, 'neutral' | 'ok' | 'warn' | 'bad' | 'info'> = {
  SUCCESS: 'ok',
  SENT: 'ok',
  BALANCED: 'ok',
  ACTIVE: 'ok',
  VALID: 'ok',
  PENDING: 'warn',
  PROCESSING: 'info',
  CREATED: 'info',
  IMBALANCED: 'bad',
  FAILED: 'bad',
  EXPIRED: 'bad',
  BLOCKED: 'bad',
  LOCKED: 'bad',
}

export function StatusBadge({ status }: { status: string }) {
  return <Badge tone={TONES[status] ?? 'neutral'}>{status}</Badge>
}

export function Money({ amount, currency = 'VND', signed = false }: { amount: number; currency?: string; signed?: boolean }) {
  const tone = signed ? (amount < 0 ? 'amount-neg' : amount > 0 ? 'amount-pos' : '') : ''
  const prefix = signed && amount > 0 ? '+' : ''
  return <span className={`amount ${tone}`}>{prefix}{money(amount, currency)}</span>
}

export function Loading({ label = 'Đang tải…' }: { label?: string }) {
  return (
    <div className="loading">
      <span className="spinner" /> {label}
    </div>
  )
}

export function Empty({ message }: { message: string }) {
  return <div className="empty">{message}</div>
}

export function ErrorNote({ error }: { error: string | null }) {
  if (!error) return null
  return <div className="error-note">{error}</div>
}

export function Json({ value }: { value: unknown }) {
  return <pre className="json">{JSON.stringify(value, null, 2)}</pre>
}

export function Copy({ value, label = 'Copy' }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false)
  return (
    <button
      type="button"
      className="link"
      onClick={() => {
        void navigator.clipboard.writeText(value).then(() => {
          setCopied(true)
          window.setTimeout(() => setCopied(false), 1200)
        })
      }}
    >
      {copied ? 'Đã copy' : label}
    </button>
  )
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(event) => event.stopPropagation()}>
        <header className="modal-head">
          <h2>{title}</h2>
          <button className="link" onClick={onClose}>
            Đóng ✕
          </button>
        </header>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  )
}
