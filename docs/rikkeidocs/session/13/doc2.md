Bài đọc: Tích hợp Spring Boot Actuator và cấu hình xuất chỉ số (Metrics Exposure)
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Tích hợp thành công các thư viện Spring Boot Actuator và Micrometer Prometheus Registry vào mã nguồn dự án Spring Boot.
Cấu hình chọn lọc các endpoints giám sát thông qua tệp tin application.yml.
Áp dụng nhãn ứng dụng tự động (Common Tags) vào toàn bộ metrics của dịch vụ để phân biệt nguồn gốc dữ liệu.
Giải thích được các nguyên tắc bảo mật thông tin khi phơi bày (expose) dữ liệu Actuator trên môi trường Production.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Mỗi microservice viết bằng Java Spring Boot (như user-service, order-service) thực chất là một tiến trình chạy ngầm phức tạp trong máy ảo Java Virtual Machine (JVM). Khi ứng dụng có dấu hiệu chạy chậm, lập trình viên cần biết các thông số kỹ thuật nội bộ của JVM:

Lượng RAM cấp phát cho vùng nhớ Heap (Heap Memory) đã dùng bao nhiêu byte?
Số lượng Thread đang hoạt động đồng thời (Live Threads) là bao nhiêu?
Tiến trình Garbage Collection (GC) dọn rác RAM hoạt động mất bao lâu và tần suất thế nào?
Tuy nhiên, các thông tin này mặc định nằm ẩn sâu bên trong JVM. Nếu chúng ta tự viết code Java để định kỳ đo đạc và in ra log file thì sẽ cực kỳ tốn công và làm log phình to rất nhanh. Chúng ta cần một giải pháp có sẵn để tự động đo đạc toàn bộ các thông số này, đồng thời xuất (expose) chúng ra dưới dạng một endpoint web chuẩn hóa để máy chủ Prometheus có thể truy cập kéo dữ liệu về.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Spring Boot Actuator và Endpoints
Spring Boot Actuator: Là một module chính thống của Spring Boot, cung cấp các tính năng sẵn dùng (Out-of-the-box) giúp kiểm tra và tương tác với ứng dụng đang chạy.
Endpoints: Actuator cung cấp các endpoint HTTP để hiển thị thông tin. Một số endpoint phổ biến:
/actuator/health: Trạng thái sức khỏe ứng dụng (UP/DOWN).
/actuator/info: Thông tin chung của ứng dụng (phiên bản, tác giả).
/actuator/metrics: Danh sách các chỉ số thô được đo đạc.
3.2 Bộ chuyển đổi Micrometer Prometheus Registry
Mặc dù Actuator thu thập được rất nhiều chỉ số qua endpoint /actuator/metrics, nhưng dữ liệu trả về ở đây có định dạng JSON phân tầng phức tạp của Spring Boot. Prometheus Server hoàn toàn không thể đọc và hiểu được cấu trúc JSON này.

Để giải quyết, chúng ta sử dụng thư viện Micrometer kết hợp với Prometheus Registry. Micrometer đóng vai trò là một lớp trừu tượng đo đạc (giống như SLF4J đối với logging). Khi tích hợp Prometheus Registry, Micrometer sẽ tự động dịch chuyển toàn bộ dữ liệu chỉ số thu thập được từ Actuator sang định dạng văn bản thô (Plain text) theo đúng cú pháp dòng lệnh của Prometheus và xuất ra tại endpoint chuyên biệt:

/actuator/prometheus

3.3 Nguyên lý bảo mật Endpoint giám sát
Các endpoint của Actuator (đặc biệt là metrics và prometheus) chứa các thông số cực kỳ chi tiết về cấu trúc hệ thống, cổng chạy, tên class, tài nguyên RAM/CPU. Nếu hacker tiếp cận được các thông tin này, họ có thể phân tích sơ đồ hệ thống để lên kịch bản tấn công khai thác.

Do đó, trên môi trường Production, chúng ta áp dụng các nguyên tắc bảo mật nghiêm ngặt:

Chỉ mở các endpoint cần thiết: Tuyệt đối không dùng cấu hình mở toang hoác include: "*" mà chỉ mở cụ thể health và prometheus.
Cô lập mạng: Chỉ cho phép truy cập các cổng microservices nội bộ (như 8081, 8083) từ mạng ảo của Docker. Không cấu hình proxy chuyển tiếp các cổng này ra ngoài qua Nginx. Chỉ duy nhất container Prometheus nằm chung mạng Docker mới có quyền kéo dữ liệu từ các endpoint này.
PHẦN 4. DEMO VÀ THỰC HÀNH (CẤU HÌNH XUẤT METRICS CHO ORDER-SERVICE)
Học viên thực hiện cấu hình mã nguồn và tệp thiết lập cho dịch vụ order-service để phơi bày dữ liệu metrics sang dạng Prometheus.

4.1 Cấu hình Dependencies trong tệp build.gradle
Thêm 2 thư viện Actuator và Micrometer Prometheus vào khối dependencies của dự án order-service:

dependencies {
    // ... các dependencies khác giữ nguyên ...
 
    # 1. Thư viện Actuator thu thập thông số JVM
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
 
    # 2. Thư viện dịch metrics sang định dạng Prometheus
    implementation 'io.micrometer:micrometer-registry-prometheus'
}

(Sau khi thêm, nhớ chạy ./gradlew build hoặc Refresh Gradle project trên IDE để tải thư viện về).

4.2 Cấu hình tệp tin application.yml của Service
Chỉnh sửa file cấu hình src/main/resources/application.yml của dự án order-service:

spring:
  application:
    name: order-service
 
management:
  endpoints:
    web:
      exposure:
        # Chỉ mở hai endpoint health và prometheus ra ngoài HTTP
        include: health, prometheus
  metrics:
    tags:
      # Tự động gắn nhãn "application" chứa tên ứng dụng vào mọi metrics được tạo ra
      application: ${spring.application.name}

4.3 Kiểm chứng kết nối tại Local
Khởi chạy dự án order-service ở máy local của bạn.
Mở trình duyệt web hoặc công cụ Postman truy cập vào đường dẫn:
http://localhost:8083/actuator/prometheus

Kết quả mong đợi: Nhận về dữ liệu văn bản thô chứa hàng ngàn dòng metrics có định dạng bắt đầu bằng chữ hoặc dấu gạch dưới, kèm theo giá trị số ở cuối:
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{application="order-service",area="heap",id="G1 Eden Space",} 4.194304E7
jvm_memory_used_bytes{application="order-service",area="nonheap",id="Metaspace",} 3.456128E7