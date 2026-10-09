Bài đọc: Cấu hình cảnh báo cơ bản khi service gặp sự cố
 

PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Hiểu rõ cơ chế hoạt động của hệ thống cảnh báo (Alerting) trong Grafana.
Cấu hình điểm nhận cảnh báo (Contact Point) qua Telegram hoặc Discord Webhook.
Thiết lập quy tắc cảnh báo (Alert Rule) tự động kích hoạt khi có container microservice bị sập hoặc CPU của VPS vượt ngưỡng an toàn.
 

PHẦN 2. VẤN ĐỀ THỰC TẾ
Sở hữu các Dashboard giám sát đẹp mắt mới chỉ giúp chúng ta giải quyết phần ngọn khi có sự cố. Chúng ta không thể phân công kỹ sư ngồi nhìn vào màn hình Grafana 24/7 để chờ xem khi nào biểu đồ chuyển sang màu đỏ.

Thực tế, khi có một microservice (ví dụ order-service) bị sập vào lúc 2 giờ sáng, khách hàng không thể thanh toán được.
Đến 8 giờ sáng, khi khách hàng phàn nàn và gửi ticket báo lỗi, đội ngũ kỹ thuật mới tá hỏa đi kiểm tra và khởi động lại dịch vụ.
Để hệ thống vận hành tự động và phản ứng tức thì, chúng ta cần cấu hình Grafana tự động giám sát các chỉ số ngầm. Ngay khi phát hiện bất thường, Grafana phải chủ động bắn tin nhắn cảnh báo trực tiếp vào nhóm chat (Telegram/Discord) của đội ngũ DevOps để xử lý ngay lập tức.

 

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Luồng xử lý Cảnh báo (Alerting Workflow) của Grafana
Hệ thống Alerting trong Grafana hoạt động qua 3 bước khép kín:

Alert Rule (Quy tắc): Định nghĩa điều kiện lỗi dựa trên câu lệnh PromQL (ví dụ: up == 0 trong 2 phút).
Contact Point (Kênh liên lạc): Định nghĩa nơi sẽ gửi thông tin cảnh báo (Telegram Bot Token, Discord Webhook URL, Email, Slack...).
Notification Policy (Chính sách): Khớp các nhãn (Labels) của Alert Rule với Contact Point để phân phối tin nhắn đến đúng nhóm kỹ thuật phụ trách.
3.2 Các trạng thái của một Alert Rule
Normal (Hoạt động bình thường): Chỉ số nằm trong ngưỡng an toàn.
Pending (Chờ kích hoạt): Chỉ số đã vượt ngưỡng lỗi nhưng chưa đủ thời gian kiểm thử (ví dụ: CPU > 90% nhưng mới duy trì được 1 phút, trong khi cấu hình yêu cầu phải kéo dài 5 phút). Trạng thái này giúp lọc bớt các cảnh báo ảo (false positive).
Firing (Kích hoạt cảnh báo): Chỉ số vượt ngưỡng vượt quá thời gian cấu hình. Grafana chính thức phát tin nhắn cảnh báo tới Contact Point.
Resolved (Đã khắc phục): Chỉ số quay lại ngưỡng an toàn, Grafana tự động gửi tin nhắn báo hệ thống đã phục hồi ổn định.
 

PHẦN 4. DEMO VÀ THỰC HÀNH (THIẾT LẬP CẢNH BÁO TELEGRAM/DISCORD)
Học viên thực hiện cấu hình cảnh báo tự động khi microservice bị sập (DOWN).

4.1 Cấu hình Contact Point (Kênh nhận tin nhắn)
Trong bài thực hành này, chúng ta sử dụng Discord Webhook (hoặc Telegram Bot):

Trên Discord, tạo một channel #system-alerts, vào cài đặt channel -> Integrations -> Webhooks -> Tạo Webhook mới và sao chép URL (Webhook URL).
Trên Grafana, vào menu bên trái -> Alerting -> Contact points.
Nhấn + Add contact point.
Thiết lập cấu hình:
Name: Discord-Alerts
Integration: Chọn Discord (hoặc Telegram).
Webhook URL: Dán URL vừa sao chép từ Discord vào.
Nhấn nút Test để gửi thử một tin nhắn mẫu. Kiểm tra channel Discord xem có nhận được tin nhắn test của Grafana hay không.
Nhấn Save contact point.
4.2 Tạo Alert Rule giám sát trạng thái Microservices
Chúng ta thiết lập quy tắc: "Cảnh báo ngay nếu có bất kỳ service nào có trạng thái DOWN quá 2 phút".

Vào Alerting -> Alert rules -> Nhấn + Create rule.
Thiết lập bước 1. Define query and alert condition:
Chọn Data Source là Prometheus.
Nhập câu lệnh PromQL kiểm tra trạng thái hoạt động:
up == 0

Thiết lập bước 2. Set evaluation behavior:
Folder: Tạo một thư mục tên là QuickBite Alerts.
Evaluation group: Tạo group mới chạy định kỳ mỗi 1m (1 phút).
Pending period (For): Điền 2m (Nếu service sập liên tục quá 2 phút mới kích hoạt cảnh báo).
Thiết lập bước 3. Configure notifications:
Chọn Contact Point mặc định là Discord-Alerts đã cấu hình ở trên.
Nhấn Save and exit ở góc trên bên phải.
4.3 Kiểm chứng cảnh báo (Simulate Incident)
Truy cập vào VPS và chủ động tắt thử container order-service để tạo sự cố giả lập:
docker compose stop order-service

Đợi khoảng 1 phút, vào tab Alert rules trên Grafana sẽ thấy rule chuyển sang trạng thái màu vàng Pending.
Tiếp tục đợi thêm 2 phút, rule chuyển sang màu đỏ Firing.
Kết quả mong đợi: Nhóm Discord #system-alerts nhận được tin nhắn cảnh báo chi tiết có dạng: [Firing] mock-service-monitor up == 0 ... order-service:8083 is down.
Khởi động lại container:
docker compose start order-service

Đợi 1-2 phút, Discord sẽ nhận được tin nhắn màu xanh báo trạng thái [Resolved].