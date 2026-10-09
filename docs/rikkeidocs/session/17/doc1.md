Bài đọc: Logging tập trung trong hệ thống microservices và tổng quan EFK
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được các thách thức và hạn chế nghiêm trọng khi kiểm tra log rời rạc trên từng container trong hệ thống QuickBite.
Mô tả được luồng di chuyển dữ liệu log tập trung từ Spring Boot service đến Elasticsearch và giao diện Kibana.
Phân biệt rõ ràng vai trò và trách nhiệm của từng thành phần trong ngăn xếp EFK: Elasticsearch, Fluentd và Kibana.
Giải thích được nguyên lý tại sao Docker logging driver phải giao tiếp với Fluentd qua địa chỉ host (127.0.0.1:24224) thay vì dùng Docker DNS nội bộ.
Xác định được các ranh giới bảo mật mạng trên máy chủ VPS nhằm bảo vệ an toàn cho các cổng dữ liệu và giao diện quản trị log.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Hệ thống QuickBite bao gồm nhiều microservices chạy độc lập trong các Docker container: quickbite-order, quickbite-user, quickbite-restaurant và quickbite-notification. Khi một khách hàng phản ánh sự cố tạo đơn hàng thất bại, kỹ sư vận hành phải mở đồng thời nhiều cửa sổ terminal để theo dõi log của từng container riêng biệt:

# Kiểm tra log rời rạc trên từng container
docker compose logs --tail 50 quickbite-order
docker compose logs --tail 50 quickbite-user
docker compose logs --tail 50 quickbite-restaurant
docker compose logs --tail 50 quickbite-notification

Phương pháp kiểm tra thủ công này bộc lộ ba điểm nghẽn nghiêm trọng khi hệ thống vận hành trên Production:

Dữ liệu phân mảnh: Log bị chia cắt theo từng container, không có công cụ tìm kiếm và lọc dữ liệu tập trung trên toàn hệ thống.
Nguy cơ mất dữ liệu: Container là tài nguyên có vòng đời ngắn (ephemeral). Khi container bị crash hoặc tạo lại trong quá trình deploy, dữ liệu log cục bộ trên host có thể bị xóa hoặc khó truy cập lại để phân tích nguyên nhân gốc rễ.
Thiếu khả năng tương quan (Correlation): Không thể thực hiện các truy vấn trực quan theo mốc thời gian thực, mức độ nghiêm trọng (log.level), tên dịch vụ (service.name) hoặc mã hành trình request (trace.id) trên cùng một màn hình điều khiển.
Giải pháp Logging tập trung (Centralized Logging) thu gom toàn bộ dữ liệu log từ tất cả các container về một trung tâm lưu trữ duy nhất, hỗ trợ đánh chỉ mục, tìm kiếm và phân tích trực quan theo thời gian thực.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Kiến trúc EFK Stack trong hệ thống QuickBite
Ngăn xếp EFK (Elasticsearch - Fluentd - Kibana) là giải pháp logging tập trung chuẩn công nghiệp, vận hành theo mô hình phân tách trách nhiệm rõ ràng:



Bảng phân công trách nhiệm các thành phần trong EFK:
Thành phần

Vai trò kỹ thuật

Trách nhiệm trong QuickBite

Spring Boot App

Nguồn phát log (Log Producer)

Ghi log định dạng JSON ra stdout/stderr qua ECS Logback Encoder.

Fluentd

Bộ thu gom & Xử lý (Log Collector/Shipper)

Nhận log từ Docker driver, bóc tách JSON, bổ sung metadata và đẩy vào Elasticsearch có buffer an toàn.

Elasticsearch

Kho lưu trữ & Tìm kiếm (Log Storage/Search)

Lưu trữ lâu dài, lập chỉ mục toàn văn (Full-text index) và xử lý các truy vấn tốc độ cao.

Kibana

Giao diện điều khiển (Visualization UI)

Cung cấp giao diện web Discover giúp kỹ sư tra cứu log, lọc theo trace.id và phân tích sự cố.

Nguyên tắc thiết kế: Ứng dụng Spring Boot tuyệt đối không gọi trực tiếp Elasticsearch qua HTTP. Việc tách biệt giúp ứng dụng không bị phụ thuộc vào tính sẵn sàng của kho log và giữ cho mã nguồn thuần khiết.

3.2 Ranh giới mạng quickbite-net và cơ chế kết nối Collector
Trong hệ thống QuickBite, Docker network quickbite-net được sử dụng làm mạng nội bộ dùng chung giữa các microservices và cụm EFK:

Bên trong Docker Network (quickbite-net): Fluentd và Kibana giao tiếp trực tiếp với Elasticsearch bằng tên service nội bộ (http://elasticsearch:9200) nhờ cơ chế phân giải tên miền Docker DNS.
Giao tiếp giữa Docker Daemon và Fluentd: Docker Logging Driver được thực thi bởi tiến trình dockerd chạy trực tiếp trên hệ điều hành Ubuntu host (không nằm trong network namespace của container). Do đó, cấu hình logging driver của các service phải gửi log về địa chỉ loopback đã publish trên host:
fluentd-address = 127.0.0.1:24224

Cảnh báo cấu hình: Tuyệt đối không khai báo fluentd-address: "fluentd:24224". Docker daemon trên host không sử dụng Docker DNS của container, việc dùng tên miền này sẽ khiến Docker không thể kết nối tới Fluentd và làm gián đoạn việc khởi động container.

3.3 Luồng xử lý và biến đổi của một Log Event
Khi order-service phát sinh một dòng log thanh toán thành công:

Spring Boot sinh JSON Log:
{
  "@timestamp": "2026-08-14T10:15:22.456Z",
  "log.level": "INFO",
  "service.name": "order-service",
  "trace.id": "quickbite-demo-001",
  "message": "Payment succeeded for Order ID 125"
}

Docker Logging Driver đóng gói: Docker daemon bắt luồng stdout, đóng gói chuỗi JSON trên vào trường log và gắn thêm các metadata container: container_id, container_name, source.
Fluentd tiếp nhận và phân tích: Fluentd nhận gói tin tại cổng 24224, sử dụng bộ lọc parser JSON để bung trường log thành các trường dữ liệu độc lập, hợp nhất với metadata container.
Elasticsearch lập chỉ mục: Bản ghi được ghi vào index theo ngày (ví dụ: quickbite-2026.08.14), cho phép Kibana truy vấn tức thì theo bất kỳ trường nào.
3.4 Nguyên tắc bảo mật mạng và ranh giới truy cập trên VPS
Cổng dịch vụ

Thành phần

Phạm vi Bind IP

Hướng dẫn truy cập an toàn

24224

Fluentd Forward Input

127.0.0.1:24224

Chỉ mở cho Docker daemon cục bộ trên host gửi log; cấm mở ra Internet.

9200

Elasticsearch REST API

127.0.0.1:9200

Chỉ dùng cho giao tiếp nội bộ giữa các container trong quickbite-net và kiểm tra qua localhost.

5601

Kibana Web Dashboard

127.0.0.1:5601

Chỉ bind vào loopback máy chủ; kỹ sư truy cập qua SSH Tunnel hoặc Reverse Proxy có xác thực.

PHẦN 4. DEMO VÀ THỰC HÀNH (ĐỐI CHIẾU KIẾN TRÚC LOGGING)
Phần thực hành tập trung vào việc xác minh hạ tầng mạng và kiểm tra tính sẵn sàng của dữ liệu log trước khi tích hợp vào EFK Stack.

4.1 Kiểm tra hạ tầng mạng dùng chung
Kiểm tra mạng Docker quickbite-net trên máy chủ Ubuntu Server:

docker network inspect quickbite-net

Yêu cầu kỹ thuật: Mạng quickbite-net phải đang hoạt động và chứa các container ứng dụng của QuickBite. Cụm EFK khi khởi tạo sẽ tham gia vào cùng mạng này.

4.2 Xác minh nguồn dữ liệu Log chuẩn hóa từ Spring Boot
Kiểm tra cấu hình ConsoleAppender và EcsEncoder trong order-service/src/main/resources/logback-spring.xml:

<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="co.elastic.logging.logback.EcsEncoder">
        <serviceName>order-service</serviceName>
    </encoder>
</appender>

Kiểm tra dòng log xuất ra từ container để xác nhận định dạng JSON đã sẵn sàng cho Collector:

cd ~/quickbite
docker compose logs --no-log-prefix --tail 10 quickbite-order

Dữ liệu đầu ra là các chuỗi JSON hợp lệ trên từng dòng, đây chính là đầu vào tiêu chuẩn để Fluentd tiếp nhận mà không cần chỉnh sửa mã nguồn ứng dụng.

4.3 Bảng so sánh hiệu quả vận hành trước và sau khi có EFK Stack
Tiêu chí so sánh

Quản lý Log truyền thống (Docker Logs)

Quản lý Log tập trung (EFK Stack)

Vị trí lưu trữ

Phân tán cục bộ trong file JSON của từng container.

Tập trung toàn bộ trong kho lưu trữ Elasticsearch.

Khả năng tìm kiếm

Giới hạn theo từng container qua lệnh dòng lệnh.

Tìm kiếm toàn văn (Full-text search), lọc đa chiều trên Kibana.

Truy vết đa dịch vụ

Ghép nối thủ công qua nhiều cửa sổ terminal.

Truy vấn 1 chạm theo trace.id bao quát toàn bộ request flow.

Vòng đời dữ liệu

Dễ bị mất khi container bị xóa hoặc build lại.

Được lưu trữ bền vững trên Persistent Volume của Elasticsearch.

Khả năng trực quan

Chỉ xem được text thô dạng dòng lệnh.

Trực quan hóa biểu đồ lỗi, tần suất request và Dashboard thời gian thực.

PHẦN 5. TỔNG KẾT
Kiến trúc phân tầng EFK: Spring Boot tạo log chuẩn ECS ra stdout, Docker Logging Driver chuyển tiếp, Fluentd phân tích và đệm dữ liệu, Elasticsearch lưu trữ/lập chỉ mục, và Kibana cung cấp giao diện trực quan hóa.
Cơ chế kết nối mạng: Docker logging driver chạy tại host nên giao tiếp với Fluentd qua 127.0.0.1:24224, trong khi các container EFK giao tiếp với nhau qua Docker network quickbite-net.
An ninh hệ thống: Các cổng 24224, 9200 và 5601 luôn được bảo vệ ở phạm vi loopback (127.0.0.1), không expose trực tiếp ra ngoài Internet.