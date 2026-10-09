Bài đọc: Tạo dashboard giám sát VPS
 

PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Import (nhập) thành công các Dashboard có sẵn từ cộng đồng Grafana để giám sát hạ tầng.
Giải thích được cấu trúc và các loại Panel hiển thị chính trong Grafana (Graph, Stat, Gauge).
Đọc hiểu và giải thích các câu lệnh PromQL cơ bản dùng để đo đạc CPU, RAM và Disk của máy chủ VPS từ dữ liệu Node Exporter.
 

PHẦN 2. VẤN ĐỀ THỰC TẾ
Khi bắt đầu tự thiết kế một bảng điều khiển (Dashboard) giám sát VPS, DevOps thường gặp bế tắc:

Không biết nên biểu diễn thông số CPU, RAM, Disk dưới dạng biểu đồ nào cho dễ nhìn (hình tròn, cột hay đồ thị đường thẳng).
Việc viết các câu lệnh PromQL phức tạp để tính tỷ lệ phần trăm CPU sử dụng thực tế (loại bỏ thời gian CPU rảnh rỗi - idle) rất khó khăn đối với người mới bắt đầu.
Việc tự vẽ thủ công từng ô chỉ số (Panel) tốn rất nhiều thời gian và công sức.
Để giải quyết vấn đề này, Grafana cung cấp một kho tàng Dashboard chia sẻ từ cộng đồng (Grafana Community Dashboards). Chúng ta chỉ cần import ID của Dashboard mẫu chuẩn thế giới và tùy chỉnh lại cho khớp với hệ thống của mình.

 

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Cơ chế Import Dashboard bằng ID
Cộng đồng Grafana chia sẻ các thiết kế Dashboard dưới dạng các tệp cấu hình JSON. Mỗi Dashboard được cấp một mã số ID định danh duy nhất trên trang chủ grafana.com/grafana/dashboards.
Dashboard quốc dân để giám sát phần cứng Linux sử dụng Node Exporter có ID nổi tiếng là 1860 (Node Exporter Full).
Bằng cách nhập ID này, Grafana sẽ tự động tải tệp cấu hình JSON về, khởi tạo toàn bộ các đồ thị chuyên nghiệp chỉ trong vài giây.
3.2 Các câu lệnh PromQL cốt lõi giám sát VPS
Tính phần trăm CPU đang sử dụng:
100 - (avg by (instance) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

Giải thích:

Lấy 100% trừ đi tỷ lệ CPU ở trạng thái rảnh rỗi (

mode="idle"

) trung bình trong 5 phút gần nhất để ra lượng CPU thực sự đang làm việc.

Tính tỷ lệ % bộ nhớ RAM đã sử dụng:
(node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes) / node_memory_MemTotal_bytes * 100

Giải thích:

Lấy tổng RAM trừ đi RAM khả dụng để ra lượng RAM đã dùng, sau đó chia cho tổng dung lượng RAM để tính tỷ lệ phần trăm.

Tính phần trăm ổ đĩa cứng SSD đã sử dụng (phân vùng /):
100 - ((node_filesystem_avail_bytes{mountpoint="/"} * 100) / node_filesystem_size_bytes{mountpoint="/"})

 

PHẦN 4. DEMO VÀ THỰC HÀNH (IMPORT DASHBOARD NODE EXPORTER FULL)
Học viên thực hiện tích hợp Dashboard giám sát VPS vào hệ thống.

4.1 Các bước Import Dashboard
Truy cập giao diện Grafana.
Nhấn vào biểu tượng Dashboards (ô vuông) ở menu bên trái -> Chọn + Create New -> Chọn Import.
Tại ô Import via grafana.com, nhập mã ID:
1860

Nhấn nút Load.
Giao diện cấu hình Dashboard xuất hiện:
Name: Có thể đổi tên thành QuickBite VPS Monitoring.
Folder: Chọn General.
Prometheus: Chọn nguồn dữ liệu Prometheus đã kết nối ở Lesson 02.
Nhấn nút Import.
Kết quả mong đợi: Hệ thống lập tức chuyển hướng sang một giao diện Dashboard cực kỳ chuyên nghiệp hiển thị chi tiết biểu đồ CPU, RAM, dung lượng đĩa, tốc độ đọc/ghi đĩa SSD và băng thông card mạng của VPS.


 