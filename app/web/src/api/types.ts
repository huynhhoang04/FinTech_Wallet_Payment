// DTOs mirroring the backend responses (docs 04). Types are structural: only the fields the UI uses.

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

// ---- identity (account-ledger-service) ----
export type RegisterResponse = { userId: string; walletAccountId: string; accountNumber: string; status: string }
export type LoginResponse = { accessToken: string; refreshToken: string; tokenType: string; expiresIn: number }
export type UserProfile = { userId: string; phone: string; email: string; fullName: string; kycStatus: string }
export type OtpRequestResponse = { otpRef: string; expiresIn: number }
export type OtpVerifyResponse = { otpToken: string; valid: boolean }

// ---- accounts / transactions (transaction-engine) ----
export type AccountInfo = { accountId: string; accountNumber: string; currency: string; status: string }
export type BalanceInfo = { accountId: string; currency: string; available: number; pending: number }
export type LedgerEntry = {
  entryId: string
  amountSigned: number
  direction: 'DEBIT' | 'CREDIT'
  balanceAfter: number
  atEpochMs: number
}
export type LedgerEntriesInfo = { transactionId: string; entries: LedgerEntry[]; sum: number }
export type StatementInfo = { openingBalance: number; closingBalance: number; entries: LedgerEntry[] }

export type TransactionStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED'
export type Transaction = {
  transactionId: string
  type: 'TRANSFER' | 'DEPOSIT' | 'WITHDRAW'
  status: TransactionStatus
  sourceAccountId: string | null
  targetAccountId: string | null
  amount: number
  fee: number
  currency: string
  description: string | null
  failureReason: string | null
  ledgerTransactionId: string | null
  paymentIntentId: string | null
  checkoutUrl: string | null
  createdAt: string
  updatedAt: string
}
export type FraudAlert = { ruleCode: string; severity: string; decision: string; details: string }

// ---- payments (payment-gateway-service) ----
export type PaymentProvider = 'VNPAY' | 'STRIPE' | 'VIETQR'
export type PaymentIntent = {
  paymentIntentId: string
  provider: string
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED'
  amount: number
  currency: string
  checkoutUrl: string
  qrContent: string
  expiresAt: string
  createdAt: string
}
export type WebhookResponse = { received: boolean; eventId: string; duplicate: boolean }

// ---- notifications ----
export type Notification = {
  id: string
  channel: string
  templateCode: string
  subject: string
  status: string
  recipient: string
  relatedTransactionId: string | null
  sentAt: string | null
}

// ---- audit / reconciliation ----
export type AuditLog = {
  id: string
  eventId: string
  eventType: string
  aggregateId: string | null
  actor: string | null
  prevHash: string | null
  hash: string
  occurredAt: string
  recordedAt: string
}
export type ChainVerification = { valid: boolean; checkedEntries: number; firstBrokenAt: string | null }
export type ReconciliationReport = {
  reportDate: string
  entryCount: number
  totalDebit: number
  totalCredit: number
  difference: number
  status: 'BALANCED' | 'IMBALANCED' | 'FAILED'
}

export type HealthComponent = { status: string }
export type Health = { status: string; components?: Record<string, HealthComponent> }

const vnd = new Intl.NumberFormat('vi-VN')

/** Money is an integer in the smallest unit; format only at the edge (docs 03). */
export function money(amount: number, currency = 'VND'): string {
  return `${vnd.format(amount)} ${currency}`
}

export function dateTime(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const date = typeof value === 'number' ? new Date(value) : new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('vi-VN', { hour12: false })
}

export function shortId(value: string | null | undefined): string {
  if (!value) return '—'
  return value.length > 13 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value
}
