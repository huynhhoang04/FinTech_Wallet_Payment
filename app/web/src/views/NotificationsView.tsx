import { useState } from 'react'
import { withQuery } from '../api/client'
import type { Notification, PageResponse } from '../api/types'
import { dateTime } from '../api/types'
import { useAuth } from '../state/AuthProvider'
import { useAsync } from '../state/useAsync'
import { Button, Card, Empty, ErrorNote, Loading, StatusBadge } from '../ui'

export function NotificationsView() {
  const { call } = useAuth()
  const [page, setPage] = useState(0)
  const notifications = useAsync(
    () =>
      call<PageResponse<Notification>>('GET', withQuery('/api/v1/notifications', { page, size: 20 }), undefined, 'notifications').then(
        (response) => response.body,
      ),
    [page],
  )

  return (
    <Card
      title="Thông báo"
      subtitle="GET /api/v1/notifications — danh tính lấy từ JWT (docs 07 SEC-03)"
      actions={
        <Button variant="ghost" onClick={notifications.reload}>
          Tải lại
        </Button>
      }
    >
      <ErrorNote error={notifications.error} />
      {notifications.loading && <Loading />}
      {notifications.data && notifications.data.content.length === 0 && <Empty message="Chưa có thông báo." />}
      {notifications.data && notifications.data.content.length > 0 && (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Thời gian</th>
                <th>Kênh</th>
                <th>Mẫu</th>
                <th>Tiêu đề</th>
                <th>Trạng thái</th>
                <th>Người nhận</th>
              </tr>
            </thead>
            <tbody>
              {notifications.data.content.map((item) => (
                <tr key={item.id}>
                  <td className="muted small">{dateTime(item.sentAt)}</td>
                  <td>{item.channel}</td>
                  <td className="mono small">{item.templateCode}</td>
                  <td>{item.subject}</td>
                  <td>
                    <StatusBadge status={item.status} />
                  </td>
                  <td className="muted small">{item.recipient}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="row between">
            <span className="muted small">
              {notifications.data.totalElements} thông báo · trang {notifications.data.page + 1}/
              {Math.max(notifications.data.totalPages, 1)}
            </span>
            <div className="row gap">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
                ← Trước
              </Button>
              <Button variant="ghost" disabled={notifications.data.last} onClick={() => setPage((current) => current + 1)}>
                Sau →
              </Button>
            </div>
          </div>
        </>
      )}
      <p className="muted small">
        Thông báo do consumer Kafka sinh ra khi có <code>tx.completed</code> / <code>risk.alert</code> — cần chạy Kafka.
        Email gửi tới mailbox demo (SMTP), xem cột "Người nhận".
      </p>
    </Card>
  )
}
