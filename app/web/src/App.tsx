import { useState } from 'react'
import { useAuth } from './state/AuthProvider'
import { AuthView } from './views/AuthView'
import { DashboardView } from './views/DashboardView'
import { TransferView } from './views/TransferView'
import { DepositView } from './views/DepositView'
import { WithdrawView } from './views/WithdrawView'
import { TransactionsView } from './views/TransactionsView'
import { StatementView } from './views/StatementView'
import { PaymentsView } from './views/PaymentsView'
import { NotificationsView } from './views/NotificationsView'
import { AdminView } from './views/AdminView'
import { DevToolsView } from './views/DevToolsView'
import { Button } from './ui'

type NavItem = { id: string; label: string; group: string; admin?: boolean }

const NAV: NavItem[] = [
  { id: 'dashboard', label: 'Tổng quan', group: 'Ví' },
  { id: 'transfer', label: 'Chuyển tiền', group: 'Ví' },
  { id: 'deposit', label: 'Nạp tiền', group: 'Ví' },
  { id: 'withdraw', label: 'Rút tiền', group: 'Ví' },
  { id: 'transactions', label: 'Giao dịch', group: 'Lịch sử' },
  { id: 'statement', label: 'Sao kê', group: 'Lịch sử' },
  { id: 'payments', label: 'Phiên nạp tiền', group: 'Lịch sử' },
  { id: 'notifications', label: 'Thông báo', group: 'Lịch sử' },
  { id: 'admin', label: 'Quản trị', group: 'Hệ thống', admin: true },
  { id: 'dev', label: 'Dev tools', group: 'Hệ thống' },
]

export default function App() {
  const { tokens } = useAuth()
  return tokens ? <Shell /> : <AuthView />
}

function Shell() {
  const { user, isAdmin, logout, reloadUser } = useAuth()
  const [view, setView] = useState('dashboard')

  const groups = Array.from(new Set(NAV.map((item) => item.group)))

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">FinTech Wallet</div>
        {groups.map((group) => (
          <div key={group} className="nav-group">
            <div className="nav-group-label">{group}</div>
            {NAV.filter((item) => item.group === group && (!item.admin || isAdmin)).map((item) => (
              <button key={item.id} className={`nav-item ${view === item.id ? 'on' : ''}`} onClick={() => setView(item.id)}>
                {item.label}
              </button>
            ))}
          </div>
        ))}
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="muted small">
            {user ? `${user.fullName} · ${user.phone}` : '—'} {isAdmin && <span className="badge badge-info">ADMIN</span>}
          </div>
          <div className="row gap">
            <Button variant="ghost" onClick={() => void reloadUser()}>
              Tải hồ sơ
            </Button>
            <Button variant="ghost" onClick={logout}>
              Đăng xuất
            </Button>
          </div>
        </header>
        <main className="content">{renderView(view, setView)}</main>
      </div>
    </div>
  )
}

function renderView(view: string, go: (next: string) => void) {
  switch (view) {
    case 'transfer':
      return <TransferView />
    case 'deposit':
      return <DepositView />
    case 'withdraw':
      return <WithdrawView />
    case 'transactions':
      return <TransactionsView />
    case 'statement':
      return <StatementView />
    case 'payments':
      return <PaymentsView />
    case 'notifications':
      return <NotificationsView />
    case 'admin':
      return <AdminView />
    case 'dev':
      return <DevToolsView />
    default:
      return <DashboardView go={go} />
  }
}
