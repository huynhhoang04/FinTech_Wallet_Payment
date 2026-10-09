import { money } from '../api/types'
import { useAccounts } from '../state/useAccounts'
import { useAuth } from '../state/AuthProvider'
import { Badge, Button, Card, Copy, Empty, ErrorNote, Loading } from '../ui'

export function DashboardView({ go }: { go: (view: string) => void }) {
  const { user, isAdmin } = useAuth()
  const accounts = useAccounts()

  return (
    <div className="grid">
      <Card
        title="Ví của tôi"
        subtitle={user ? `${user.fullName} · ${user.phone} · KYC ${user.kycStatus}` : undefined}
        actions={
          <div className="row">
            {isAdmin && <Badge tone="info">ADMIN</Badge>}
            <Button variant="ghost" onClick={accounts.reload}>
              Tải lại
            </Button>
          </div>
        }
      >
        <ErrorNote error={accounts.error} />
        {accounts.loading && <Loading />}
        {accounts.data && accounts.data.length === 0 && <Empty message="Chưa có ví nào." />}
        {accounts.data?.map((account) => (
          <div key={account.accountId} className="account-row">
            <div>
              <div className="account-number">{account.accountNumber}</div>
              <div className="muted small">
                {account.currency} · {account.status} · <Copy value={account.accountId} label="copy id" />
              </div>
            </div>
            <div className="account-balance">
              {account.balance ? (
                <>
                  <div className="balance-available">{money(account.balance.available, account.currency)}</div>
                  <div className="muted small">khả dụng · pending {money(account.balance.pending, account.currency)}</div>
                </>
              ) : (
                <span className="muted small">không đọc được số dư</span>
              )}
            </div>
          </div>
        ))}
      </Card>

      <Card title="Thao tác nhanh">
        <div className="quick-actions">
          <Button onClick={() => go('transfer')}>Chuyển tiền</Button>
          <Button onClick={() => go('deposit')}>Nạp tiền</Button>
          <Button onClick={() => go('withdraw')}>Rút tiền</Button>
          <Button variant="ghost" onClick={() => go('transactions')}>
            Lịch sử giao dịch
          </Button>
        </div>
        <p className="muted small">
          Mỗi giao dịch động tiền cần <code>X-Idempotency-Key</code> — dev client tự sinh mỗi lần bấm. Nút "Gửi lại (cùng key)" để
          kiểm chứng idempotent replay.
        </p>
      </Card>
    </div>
  )
}
