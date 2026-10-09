import { useState } from 'react'
import { isApiError, withQuery } from '../api/client'
import type { FraudAlert, LedgerEntriesInfo, PageResponse, Transaction } from '../api/types'
import { dateTime, money, shortId } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAccounts } from '../state/useAccounts'
import { useAsync } from '../state/useAsync'
import { Button, Card, Empty, ErrorNote, Json, Loading, Modal, Money, Select, StatusBadge } from '../ui'

export function TransactionsView() {
  const { call } = useAuth()
  const accounts = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<Transaction | null>(null)

  const account = accountId || accounts.data?.[0]?.accountId || ''

  const list = useAsync(
    () =>
      call<PageResponse<Transaction>>(
        'GET',
        withQuery('/api/v1/transactions', { accountId: account, page, size: 20 }),
        undefined,
        'transactions',
      ).then((response) => response.body),
    [account, page],
  )

  return (
    <div className="grid">
      <Card
        title="Lịch sử giao dịch"
        subtitle="GET /api/v1/transactions?accountId="
        actions={
          <div className="row gap">
            <Select value={account} onChange={(event) => setAccountId(event.target.value)}>
              {accounts.data?.map((item) => (
                <option key={item.accountId} value={item.accountId}>
                  {item.accountNumber}
                </option>
              ))}
            </Select>
            <Button variant="ghost" onClick={list.reload}>
              Tải lại
            </Button>
          </div>
        }
      >
        <ErrorNote error={list.error} />
        {list.loading && <Loading />}
        {list.data && list.data.content.length === 0 && <Empty message="Chưa có giao dịch." />}
        {list.data && list.data.content.length > 0 && (
          <>
            <table className="table clickable">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Loại</th>
                  <th>Trạng thái</th>
                  <th className="right">Số tiền</th>
                  <th>Mô tả</th>
                  <th>ID</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((transaction) => (
                  <tr key={transaction.transactionId} onClick={() => setSelected(transaction)}>
                    <td className="muted small">{dateTime(transaction.createdAt)}</td>
                    <td>{transaction.type}</td>
                    <td>
                      <StatusBadge status={transaction.status} />
                    </td>
                    <td className="right">
                      <Money amount={transaction.amount} currency={transaction.currency} />
                    </td>
                    <td className="muted small">{transaction.description ?? '—'}</td>
                    <td className="mono small">{shortId(transaction.transactionId)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="row between">
              <span className="muted small">
                {list.data.totalElements} giao dịch · trang {list.data.page + 1}/{Math.max(list.data.totalPages, 1)}
              </span>
              <div className="row gap">
                <Button variant="ghost" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
                  ← Trước
                </Button>
                <Button variant="ghost" disabled={list.data.last} onClick={() => setPage((current) => current + 1)}>
                  Sau →
                </Button>
              </div>
            </div>
          </>
        )}
      </Card>

      {selected && <TransactionDetail transaction={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}

function TransactionDetail({ transaction, onClose }: { transaction: Transaction; onClose: () => void }) {
  const { call } = useAuth()
  const entries = useAsync(
    () =>
      call<LedgerEntriesInfo>('GET', `/api/v1/transactions/${transaction.transactionId}/ledger-entries`, undefined, 'ledger-entries').then(
        (response) => response.body,
      ),
    [transaction.transactionId],
  )
  const alerts = useAsync(
    () =>
      call<FraudAlert[]>('GET', `/api/v1/transactions/${transaction.transactionId}/fraud-alerts`, undefined, 'fraud-alerts').then(
        (response) => response.body,
      ),
    [transaction.transactionId],
  )

  return (
    <Modal title={`Giao dịch ${shortId(transaction.transactionId)}`} onClose={onClose}>
      <div className="row gap wrap center">
        <StatusBadge status={transaction.status} />
        <span>{transaction.type}</span>
        <Money amount={transaction.amount} currency={transaction.currency} />
      </div>
      {transaction.failureReason && <div className="error-note">{transaction.failureReason}</div>}

      <h3>Bút toán kép</h3>
      <ErrorNote error={entries.error} />
      {entries.loading && <Loading />}
      {entries.data && (
        <table className="table">
          <thead>
            <tr>
              <th>Account</th>
              <th>Hướng</th>
              <th className="right">Số tiền</th>
              <th className="right">Số dư sau</th>
              <th>Thời gian</th>
            </tr>
          </thead>
          <tbody>
            {entries.data.entries.map((entry) => (
              <tr key={entry.entryId}>
                <td className="mono small">{shortId(entry.entryId)}</td>
                <td>{entry.direction}</td>
                <td className="right">
                  <Money amount={entry.amountSigned} signed />
                </td>
                <td className="right">{money(entry.balanceAfter)}</td>
                <td className="muted small">{dateTime(entry.atEpochMs)}</td>
              </tr>
            ))}
            <tr>
              <td colSpan={5} className="right muted">
                Σ = {entries.data.sum} (phải bằng 0)
              </td>
            </tr>
          </tbody>
        </table>
      )}

      <h3>Cảnh báo gian lận</h3>
      <ErrorNote error={alerts.error} />
      {alerts.data && alerts.data.length === 0 && <Empty message="Không có cảnh báo." />}
      {alerts.data?.map((alert, index) => (
        <div key={index} className="row gap wrap center">
          <StatusBadge status={alert.decision} />
          <span>{alert.ruleCode}</span>
          <span className="muted small">{alert.severity}</span>
        </div>
      ))}

      <details>
        <summary className="muted small">JSON giao dịch</summary>
        <Json value={transaction} />
      </details>
    </Modal>
  )
}
