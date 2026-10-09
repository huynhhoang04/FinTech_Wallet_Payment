Bài đọc: Tạo dashboard giám sát Spring Boot service
 

PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Import thành công Dashboard chuyên dụng cho JVM và Spring Boot (ID 4701 hoặc 11378) vào Grafana.
Đọc hiểu các thông số cơ bản về bộ nhớ JVM (Heap, Non-Heap) và tiến trình dọn rác Garbage Collection (GC).
Giám sát được hiệu năng các yêu cầu HTTP (Throughput, Latency) gửi tới các REST API của Spring Boot.
 

PHẦN 2. VẤN ĐỀ THỰC TẾ
Khi vận hành cụm Microservices Spring Boot trên Production, chúng ta thường gặp phải các lỗi ngầm rất khó phát hiện từ bên ngoài:

Ứng dụng đột ngột phản hồi chậm chạp mà không có lỗi cụ thể, CPU tăng nhẹ, nghi ngờ do máy ảo Java chạy GC liên tục để giải phóng bộ nhớ.
Có hiện tượng rò rỉ bộ nhớ (Memory Leak), RAM tăng liên tục qua các ngày và cuối cùng container bị sập đột ngột (OOM Exit 137).
Lượng request đổ về API tăng đột biến nhưng không rõ API nào đang chịu tải lớn nhất và tốc độ phản hồi trung bình là bao nhiêu mili-giây.
Dữ liệu thô từ Spring Boot Actuator kết hợp với Grafana Dashboard sẽ phơi bày toàn bộ "nội tạng" của máy ảo JVM, giúp chúng ta chủ động tìm ra nguyên nhân gốc rễ của các lỗi hiệu năng này.

 

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Lựa chọn Dashboard JVM quốc dân
Với dữ liệu đo đạc bằng thư viện Micrometer từ Spring Boot Actuator, mã ID Dashboard phổ biến và đầy đủ nhất là 4701 (JVM (Micrometer)) hoặc 11378.
Dashboard này tự động phân tích hàng trăm chỉ số của JVM để vẽ thành các khu vực riêng biệt: Bộ nhớ JVM, Trạng thái Thread, Tiến trình Garbage Collection, và Thống kê HTTP Requests.
3.2 Các chỉ số JVM quan trọng cần theo dõi
JVM Memory (Bộ nhớ Heap):
Chỉ số: jvm_memory_used_bytes{area="heap"} và jvm_memory_max_bytes{area="heap"}.
Ý nghĩa: Hiển thị lượng RAM thực tế đang bị chiếm dụng bởi các đối tượng Java (Heap Space) so với ngưỡng tối đa được cấp phát (Xmx). Nếu lượng dùng tiến sát Max liên tục, hệ thống sắp bị tràn bộ nhớ (OOM).
Garbage Collection (GC Pauses):
Chỉ số: rate(jvm_gc_pause_seconds_sum[5m]).
Ý nghĩa: Đo đạc thời gian ứng dụng bị dừng hoàn toàn (Stop-the-world) để dọn rác. Nếu tổng thời gian GC pauses quá cao, ứng dụng sẽ bị giật lag nghiêm trọng.
Trạng thái luồng (Thread States):
Chỉ số: jvm_threads_states_threads.
Ý nghĩa: Theo dõi số lượng luồng đang chạy (Runnable), đang chờ (Waiting) hoặc bị khóa (Blocked). Nếu số lượng thread Blocked tăng đột biến, hệ thống đang gặp hiện tượng nghẽn cổ chai (Deadlock hoặc nghẽn DB Connection Pool).
3.3 Đo lường Hiệu năng HTTP Request
Throughput (Lưu lượng):
sum(rate(http_server_requests_seconds_count[5m])) by (uri, status)

Ý nghĩa:

Tính số lượng request HTTP/giây đổ về từng endpoint, phân chia theo mã trạng thái HTTP (200, 404, 500) để biết API nào đang bị lỗi nhiều nhất.

Latency (Độ trễ phản hồi trung bình):
sum(rate(http_server_requests_seconds_sum[5m])) by (uri) / sum(rate(http_server_requests_seconds_count[5m])) by (uri)

Ý nghĩa:

Lấy tổng thời gian xử lý request chia cho tổng số lượng request để tính ra thời gian xử lý trung bình của mỗi API.

 

PHẦN 4. DEMO VÀ THỰC HÀNH (IMPORT DASHBOARD JVM MICROMETER)
Học viên thực hiện tích hợp Dashboard giám sát Spring Boot Service.

4.1 Các bước thực hiện
Đăng nhập vào Grafana.
Chọn Dashboards -> + Create New -> Import.
Nhập ID:
4701

(Hoặc có thể sử dụng ID 11378 để có giao diện hiện đại hơn).

Nhấn Load.
Đổi tên thành QuickBite JVM Monitoring và chọn Data Source là Prometheus.
Nhấn nút Import.
Sử dụng bộ lọc Application ở góc trên bên trái Dashboard để chọn xem thông số của từng service riêng lẻ (như order-service hoặc user-service).
