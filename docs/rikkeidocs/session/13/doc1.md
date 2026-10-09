Bài đọc: Khái niệm giám sát hệ thống (Metrics) và Kiến trúc hoạt động của Prometheus
 

PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được tầm quan trọng của việc giám sát hệ thống (Monitoring) và định nghĩa các chỉ số định lượng (Metrics).
Phân biệt được 4 loại Metrics cơ bản trong Prometheus: Counter, Gauge, Histogram, và Summary.
So sánh được cơ chế hoạt động, ưu nhược điểm của mô hình thu thập kiểu kéo (Pull-based) so với kiểu đẩy (Push-based).
Mô tả được luồng di chuyển dữ liệu tổng quan trong kiến trúc của Prometheus.
 

PHẦN 2. VẤN ĐỀ THỰC TẾ
Khi dự án QuickBite được đưa lên vận hành chính thức trên môi trường Production Cloud, toàn bộ hệ thống lúc này hoạt động giống như một chiếc "hộp đen" (Black-box). Client vẫn gửi yêu cầu và nhận phản hồi bình thường, nhưng đội ngũ vận hành hoàn toàn không biết chuyện gì đang xảy ra bên trong VPS.

Thỉnh thoảng, dịch vụ order-service phản hồi rất chậm hoặc bị treo vào khung giờ cao điểm (11h30 - 12h30). Lúc này, chúng ta đối mặt với hàng loạt câu hỏi:

Máy chủ VPS có đang bị quá tải CPU hay không?
Dung lượng bộ nhớ RAM của VPS hay Heap Memory của JVM trong container có bị cạn kiệt dẫn đến sập ứng dụng không?
Tần suất hệ thống thực hiện Garbage Collection (dọn rác RAM) là bao nhiêu?
Lượng request đổ vào API Gateway tăng đột biến ở endpoint nào?
Nếu không có số liệu đo đạc (Metrics) liên tục theo thời gian, chúng ta hoàn toàn "mù" thông tin và chỉ có thể đoán mò nguyên nhân, dẫn đến thời gian khắc phục sự cố (Downtime) kéo dài, ảnh hưởng nghiêm trọng đến trải nghiệm người dùng. Hệ thống cần một giải pháp giám sát tập trung để liên tục thu thập và lưu trữ các thông số này.

 

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Giám sát hệ thống (Monitoring) và Metrics
Giám sát (Monitoring): Là việc thu thập, xử lý và trực quan hóa các dữ liệu đo đạc liên tục từ hệ thống phần cứng (CPU, RAM, Disk) và phần mềm (lượng request, thời gian xử lý API, trạng thái JVM) để phát hiện sự cố sớm và hỗ trợ tối ưu hiệu năng.
Metrics (Chỉ số đo lường): Là các giá trị số học biểu thị trạng thái hệ thống đi kèm với mốc thời gian (Timestamp) và các nhãn định danh (Labels).
Ví dụ: http_requests_total{method="POST", handler="/api/v1/orders"} 1250 tại thời điểm 2026-07-09 20:00:00.
3.2 4 loại Metrics cốt lõi trong Prometheus
Prometheus phân loại dữ liệu số thành 4 nhóm cơ bản:

Counter (Bộ đếm):
Giá trị chỉ có thể tăng lên hoặc reset về 0 khi ứng dụng khởi động lại.
Ví dụ: Tổng số request đã nhận (http_requests_total), tổng số lỗi xảy ra, tổng thời gian CPU xử lý.
Gauge (Thước đo):
Giá trị có thể tăng hoặc giảm tự do tại bất kỳ thời điểm nào.
Ví dụ: Dung lượng RAM đang sử dụng (jvm_memory_used_bytes), số lượng luồng đang chạy (jvm_threads_live_threads), dung lượng ổ cứng trống.
Histogram (Biểu đồ phân phối):
Đo lường tần suất xuất hiện của dữ liệu bằng cách chia nhỏ dữ liệu vào các khoảng (buckets) có sẵn và đếm số lượng phần tử rơi vào từng khoảng đó.
Ví dụ: Phân phối thời gian phản hồi API (http_server_requests_seconds_bucket).
Summary (Tóm tắt phân vị):
Tương tự Histogram nhưng tính toán trực tiếp các giá trị phân vị (như 50%, 90%, 99% request có phản hồi dưới bao nhiêu giây) ngay tại phía Client trước khi gửi cho Prometheus.
3.3 Cơ chế hoạt động của Prometheus: Pull-based vs Push-based
Prometheus nổi tiếng vì sử dụng mô hình Pull-based để thu thập dữ liệu:

Mô hình Pull-based (Prometheus sử dụng):
Prometheus Server đóng vai trò trung tâm, định kỳ gửi các request HTTP GET tới các máy con/dịch vụ (targets) để kéo dữ liệu metrics về lưu trữ.
Ưu điểm:
Bảo vệ Server giám sát không bị nghẽn (DDoS) khi các dịch vụ Client gặp sự cố tải cao và gửi dữ liệu dồn dập.
Dễ dàng phát hiện khi một service bị sập (nếu Prometheus không kéo được dữ liệu, nó sẽ báo target trạng thái DOWN).
Phù hợp với việc giám sát các dịch vụ chạy lâu dài (long-running services).
Mô hình Push-based (Các hệ thống truyền thống như InfluxDB, Jaeger):
Các ứng dụng con chủ động gọi API gửi dữ liệu metrics về Server giám sát.
Ưu điểm: Phù hợp với các tác vụ chạy ngắn hạn (Short-lived jobs/Serverless) khi ứng dụng bật lên chạy xong rồi tắt ngay, không đủ thời gian để Prometheus kéo dữ liệu.
 

PHẦN 4. DEMO VÀ THỰC HÀNH (SƠ ĐỒ TRAFFIC FLOW SCRAPING TRÊN PRODUCTION)
Trong kiến trúc giám sát của Session 13, luồng di chuyển dữ liệu kéo metrics từ các container dịch vụ của hệ thống QuickBite được thiết kế như sau:



Các Target hiển thị cổng metrics: Mỗi container dịch vụ (như user-service, order-service hay node-exporter giám sát hệ điều hành) sẽ hiển thị một cổng và đường dẫn web chứa dữ liệu thô dạng text.
Prometheus gửi request định kỳ: Prometheus Server đọc file cấu hình prometheus.yml. Cứ sau mỗi 15 giây (Scrape Interval), Prometheus gửi request HTTP GET vào các URL này.
Lưu trữ dữ liệu: Dữ liệu text nhận được từ các target sẽ được Prometheus phân tích cú pháp, gán mốc thời gian nhận và nạp trực tiếp vào kho lưu trữ dữ liệu chuỗi thời gian (TSDB) nằm trên ổ cứng VPS.