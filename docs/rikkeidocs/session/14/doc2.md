Bài đọc: Kết nối Grafana với Prometheus
 

PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Cấu hình kết nối thành công Prometheus làm nguồn dữ liệu (Data Source) bên trong giao diện quản trị của Grafana.
Giải thích được cơ chế phân giải tên miền nội bộ của Docker Network khi kết nối hai container này.
Sử dụng chức năng "Explore" của Grafana để viết và chạy thử nghiệm các câu lệnh truy vấn PromQL thô.
 

PHẦN 2. VẤN ĐỀ THỰC TẾ
Sau khi cài đặt xong Grafana ở Lesson 01, chúng ta mới chỉ có một giao diện hiển thị rỗng, chưa có bất kỳ dữ liệu nào. Khi cố gắng cấu hình kết nối từ Grafana sang Prometheus:

Nhiều học viên điền địa chỉ URL kết nối là http://localhost:9090. Kết quả là Grafana báo lỗi Connection refused (Từ chối kết nối).
Một số học viên dùng IP Public của VPS http://<vps_public_ip>:9090 nhưng cũng bị lỗi vì trước đó tường lửa UFW đã chặn cổng 9090 không cho bên ngoài truy cập.
Để kết nối chính xác và bảo mật, chúng ta cần tận dụng mạng ảo nội bộ của Docker. Bài học này sẽ hướng dẫn chi tiết cách thiết lập kết nối chuẩn Production giữa hai dịch vụ này.

 

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Docker Network DNS resolution
Do container Grafana (quickbite-grafana) và container Prometheus (quickbite-prometheus) chạy chung mạng ảo quickbite-net, chúng có khả năng giao tiếp với nhau bằng tên container thay vì IP vật lý:

Nếu chúng ta điền localhost hoặc 127.0.0.1 vào cấu hình Grafana, container Grafana sẽ tự hiểu là đang gọi đến chính nó (cổng 9090 của container Grafana, vốn không tồn tại dịch vụ nào).
Địa chỉ kết nối chính xác phải là:
http://quickbite-prometheus:9090

Nhân Docker DNS sẽ tự dịch chuyển tên

quickbite-prometheus

thành IP nội bộ tương ứng trong mạng ảo để kết nối an toàn.

3.2 Khái niệm Data Source trong Grafana
Data Source: Là các bộ kết nối (connectors) tích hợp sẵn trong Grafana để giao tiếp với các hệ thống cơ sở dữ liệu bên ngoài.
Với Prometheus, Grafana sẽ dùng giao thức HTTP REST API để gửi các câu lệnh PromQL, nhận về dữ liệu dạng JSON chuỗi thời gian và vẽ biểu đồ.
 

PHẦN 4. DEMO VÀ THỰC HÀNH (KẾT NỐI PROMETHEUS DATA SOURCE)
Học viên thực hiện kết nối Prometheus vào Grafana và chạy thử truy vấn thô.

4.1 Các bước cấu hình trên giao diện Grafana
Đăng nhập vào Grafana (http://<vps_public_ip>:3000).
Ở thanh menu bên trái, nhấn vào biểu tượng bánh răng Connections -> Chọn Data sources.
Nhấn nút Add data source.
Chọn nguồn dữ liệu là Prometheus từ danh sách hiển thị.
Cấu hình các thông số cơ bản:
Name: Prometheus (Để mặc định làm nguồn dữ liệu chính - Default).
Connection HTTP URL: http://quickbite-prometheus:9090
Kéo xuống cuối trang và nhấn nút Save & test.
Kết quả mong đợi: Xuất hiện thông báo màu xanh lá cây: "Data source is working" (Nguồn dữ liệu đang hoạt động tốt).


4.2 Thử nghiệm truy vấn PromQL trong tab Explore
Nhấn vào biểu tượng la bàn Explore trên thanh công cụ bên trái.
Đảm bảo nguồn dữ liệu được chọn ở góc trên bên trái là Prometheus.
Chuyển sang chế độ gõ câu lệnh (Code Mode):
Mặc định Grafana (từ phiên bản 9+) sẽ ở chế độ Builder (chọn qua selectbox).
Nhấn nút Code ở góc trên bên phải thanh công cụ truy vấn (nằm bên cạnh nút Builder) để chuyển sang chế độ tự do gõ PromQL.
Nhập một câu lệnh PromQL của Backend Java (đã cấu hình quét từ Session 13):
Để xem dung lượng RAM mà các ứng dụng Java Spring Boot đang sử dụng, gõ:
jvm_memory_used_bytes

Hoặc chọn các metric khác có sẵn như: up, process_cpu_usage, http_server_requests_seconds_count.
Nhấn nút Run query (hoặc tổ hợp phím Shift + Enter).
Kết quả mong đợi: Biểu đồ chuỗi thời gian hiển thị biến động RAM của các ứng dụng Java (user-service, order-service,...) được vẽ thành công ngay phía dưới.