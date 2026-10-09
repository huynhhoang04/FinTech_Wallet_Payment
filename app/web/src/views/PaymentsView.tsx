import { useState } from 'react'
import { withQuery } from '../api/client'
import type { PageResponse, PaymentIntent } from '../api/types'
import { dateTime, money, shortId } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAsync } from '../state/useAsync'
import { Button, Card, Empty, ErrorNote, Loading, Money, StatusBadge } from '../ui'

export function PaymentsView() {
  const { call } = useAuth()
  const [page, setPage] = useState(0)
  const intents = useAsync(
    () =>
      call<PageResponse<PaymentIntent>>('GET', withQuery('/api/v1/payments', { page, size: 20 }), undefined, 'payments').then(
        (response) => response.body,
      ),
    [page],
  )

  return (
    <Card
      title="Lịch sử nạp tiền"
      subtitle="GET /api/v1/payments — PaymentIntent"
      actions={
        <Button variant="ghost" onClick={intents.reload}>
          Tải lại
        </Button>
      }
    >
      <ErrorNote error={intents.error} />
      {intents.loading && <Loading />}
      {intents.data && intents.data.content.length === 0 && <Empty message="Chưa có phiên nạp tiền." />}
      {intents.data && intents.data.content.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Thời gian</th>
                <th>Provider</th>
                <th>Trạng thái</th>
                <th className="right">Số tiền</th>
                <th>Checkout</th>
                <th>ID</th>
              </tr>
            </thead>
            <tbody>
              {intents.data.content.map((intent) => (
                <tr key={intent.paymentIntentId}>
                  <td className="muted small">{dateTime(intent.createdAt)}</td>
                  <td>{intent.provider}</td>
                  <td>
                    <StatusBadge status={intent.status} />
                  </td>
                  <td className="right">
                    <Money amount={intent.amount} currency={intent.currency} />
                  </td>
                  <td className="small">
                    <a href={intent.checkoutUrl} target="_blank" rel="noreferrer" className="link">
                      mở
                    </a>
                  </td>
                  <td className="mono small">{shortId(intent.paymentIntentId)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="row between">
            <span className="muted small">
              {intents.data.totalElements} phiên · trang {intents.data.page + 1}/{Math.max(intents.data.totalPages, 1)}
            </span>
            <div className="row gap">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
                ← Trước
              </Button>
              <Button variant="ghost" disabled={intents.data.last} onClick={() => setPage((current) => current + 1)}>
                Sau →
              </Button>
            </div>
          </div>
        </>
      )}
      <p className="muted small">Hết hạn sau 15 phút; job nền tự chuyển PENDING quá hạn sang EXPIRED (docs 07 FIN-13).</p>
    </Card>
  )
}
