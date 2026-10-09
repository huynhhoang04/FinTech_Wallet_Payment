import { useState } from 'react'
import { withQuery } from '../api/client'
import type { StatementInfo } from '../api/types'
import { dateTime, money } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAccounts } from '../state/useAccounts'
import { useAsync } from '../state/useAsync'
import { Button, Card, Empty, ErrorNote, Loading, Money, Select } from '../ui'

export function StatementView() {
  const { call } = useAuth()
  const accounts = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [page, setPage] = useState(0)

  const account = accountId || accounts.data?.[0]?.accountId || ''
  const statement = useAsync(
    () =>
      call<StatementInfo>(
        'GET',
        withQuery(`/api/v1/accounts/${account}/statement`, { page, size: 20, fromEpochMs: 0, toEpochMs: Date.now() }),
        undefined,
        'statement',
      ).then((response) => response.body),
    [account, page],
  )

  return (
    <Card
      title="Sao kê"
      subtitle="GET /api/v1/accounts/{id}/statement — số dư đầu/cuối kỳ + các dòng sổ cái"
      actions={
        <div className="row gap">
          <Select value={account} onChange={(event) => setAccountId(event.target.value)}>
            {accounts.data?.map((item) => (
              <option key={item.accountId} value={item.accountId}>
                {item.accountNumber}
              </option>
            ))}
          </Select>
          <Button variant="ghost" onClick={statement.reload}>
            Tải lại
          </Button>
        </div>
      }
    >
      <ErrorNote error={statement.error} />
      {statement.loading && <Loading />}
      {statement.data && (
        <>
          <div className="row gap wrap">
            <div className="stat">
              <span className="muted small">Số dư đầu kỳ</span>
              <b>{money(statement.data.openingBalance)}</b>
            </div>
            <div className="stat">
              <span className="muted small">Số dư cuối kỳ</span>
              <b>{money(statement.data.closingBalance)}</b>
            </div>
          </div>
          {statement.data.entries.length === 0 && <Empty message="Không có bút toán trong kỳ." />}
          {statement.data.entries.length > 0 && (
            <table className="table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Hướng</th>
                  <th className="right">Số tiền</th>
                  <th className="right">Số dư sau</th>
                </tr>
              </thead>
              <tbody>
                {statement.data.entries.map((entry) => (
                  <tr key={entry.entryId}>
                    <td className="muted small">{dateTime(entry.atEpochMs)}</td>
                    <td>{entry.direction}</td>
                    <td className="right">
                      <Money amount={entry.amountSigned} signed />
                    </td>
                    <td className="right">{money(entry.balanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div className="row gap">
            <Button variant="ghost" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
              ← Trang trước
            </Button>
            <Button variant="ghost" disabled={statement.data.entries.length < 20} onClick={() => setPage((current) => current + 1)}>
              Trang sau →
            </Button>
          </div>
        </>
      )}
    </Card>
  )
}
