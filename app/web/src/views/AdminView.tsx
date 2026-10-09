import { useState } from 'react'
import { isApiError, withQuery } from '../api/client'
import type { AuditLog, ChainVerification, PageResponse, ReconciliationReport } from '../api/types'
import { dateTime, money, shortId } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAsync } from '../state/useAsync'
import { Badge, Button, Card, Empty, ErrorNote, Field, Json, Loading, StatusBadge, TextInput } from '../ui'

export function AdminView() {
  const { call } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [verification, setVerification] = useState<ChainVerification | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [running, setRunning] = useState(false)

  const reports = useAsync(
    () => call<ReconciliationReport[]>('GET', '/api/v1/reconciliation/reports', undefined, 'reports').then((response) => response.body),
    [],
  )
  const logs = useAsync(
    () => call<PageResponse<AuditLog>>('GET', withQuery('/api/v1/audit/logs', { page: 0, size: 20 }), undefined, 'audit-logs').then((response) => response.body),
    [],
  )

  async function verifyChain() {
    setVerifying(true)
    setError(null)
    try {
      const response = await call<ChainVerification>('GET', '/api/v1/audit/verify-chain', undefined, 'verify-chain')
      setVerification(response.body)
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    } finally {
      setVerifying(false)
    }
  }

  async function runReconciliation() {
    setRunning(true)
    setError(null)
    try {
      await call<ReconciliationReport>('POST', withQuery('/api/v1/reconciliation/run', { date }), undefined, 'reconciliation-run')
      reports.reload()
    } catch (cause) {
      setError(isApiError(cause) ? `${cause.code}: ${cause.message}` : String(cause))
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="grid">
      <Card
        title="Toàn vẹn sổ cái"
        subtitle="GET /api/v1/audit/verify-chain — chuỗi hash audit + tổng sổ cái"
        actions={
          <Button onClick={verifyChain} loading={verifying}>
            Kiểm tra chuỗi hash
          </Button>
        }
      >
        <ErrorNote error={error} />
        {verification && (
          <div className="row gap wrap center">
            {verification.valid ? <Badge tone="ok">HỢP LỆ</Badge> : <Badge tone="bad">ĐỨT GÃY</Badge>}
            <span className="muted small">
              đã kiểm {verification.checkedEntries} bản ghi
              {verification.firstBrokenAt ? ` · hỏng tại ${shortId(verification.firstBrokenAt)}` : ''}
            </span>
          </div>
        )}
      </Card>

      <Card
        title="Đối chiếu (reconciliation)"
        subtitle="GET /api/v1/reconciliation/reports · POST /api/v1/reconciliation/run"
        actions={
          <div className="row gap center">
            <Field label="">
              <TextInput type="date" value={date} onChange={(event) => setDate(event.target.value)} />
            </Field>
            <Button onClick={runReconciliation} loading={running}>
              Chạy đối chiếu
            </Button>
          </div>
        }
      >
        <ErrorNote error={reports.error} />
        {reports.loading && <Loading />}
        {reports.data && reports.data.length === 0 && <Empty message="Chưa có báo cáo." />}
        {reports.data && reports.data.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Trạng thái</th>
                <th className="right">Entry</th>
                <th className="right">Σ Nợ</th>
                <th className="right">Σ Có</th>
                <th className="right">Lệch</th>
              </tr>
            </thead>
            <tbody>
              {reports.data.map((report) => (
                <tr key={report.reportDate}>
                  <td>{report.reportDate}</td>
                  <td>
                    <StatusBadge status={report.status} />
                  </td>
                  <td className="right">{report.entryCount}</td>
                  <td className="right">{money(report.totalDebit)}</td>
                  <td className="right">{money(report.totalCredit)}</td>
                  <td className="right">
                    <b className={report.difference === 0 ? 'amount-pos' : 'amount-neg'}>{money(report.difference)}</b>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card
        title="Audit log"
        subtitle="GET /api/v1/audit/logs — append-only, có prevHash/hash"
        actions={
          <Button variant="ghost" onClick={logs.reload}>
            Tải lại
          </Button>
        }
      >
        <ErrorNote error={logs.error} />
        {logs.loading && <Loading />}
        {logs.data && logs.data.content.length === 0 && <Empty message="Chưa có bản ghi audit." />}
        {logs.data && logs.data.content.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Ghi lúc</th>
                <th>Loại</th>
                <th>Aggregate</th>
                <th className="mono">hash</th>
              </tr>
            </thead>
            <tbody>
              {logs.data.content.map((entry) => (
                <tr key={entry.id}>
                  <td className="muted small">{dateTime(entry.recordedAt)}</td>
                  <td>{entry.eventType}</td>
                  <td className="mono small">{shortId(entry.aggregateId)}</td>
                  <td className="mono small">{shortId(entry.hash)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
