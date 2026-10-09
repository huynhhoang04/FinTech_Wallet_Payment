Bài đọc: Structured logging với định dạng JSON
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được khái niệm Structured Logging và lợi ích vượt trội của định dạng log JSON trong kiến trúc microservices.
Phân biệt được sự khác nhau giữa log dạng văn bản tự do (unstructured text) và log có cấu trúc trường dữ liệu cố định.
Đọc hiểu và khai thác các trường dữ liệu tiêu chuẩn trong JSON log do chuẩn ECS (Elastic Common Schema) quy định.
Sử dụng thành thạo lệnh docker compose logs kết hợp với công cụ jq để lọc, truy vấn và phân tích log theo trường dữ liệu.
Đánh giá và kiểm soát các trường thông tin cần thiết trong log, ngăn chặn triệt để nguy cơ rò rỉ dữ liệu bảo mật.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Khi hệ thống chỉ gồm một service đơn lẻ, định dạng log dạng văn bản truyền thống (plain text) tương đối dễ đọc bằng mắt thường:

2026-08-14 10:15:22.456 INFO 1 --- [main] c.q.o.services.OrderService : Payment succeeded for Order ID 125

Tuy nhiên, khi hệ thống mở rộng thành hàng chục microservices chạy song song trong các container, việc tìm kiếm sự cố dựa trên text tự do bộc lộ nhiều hạn chế nghiêm trọng:

Mỗi lập trình viên có phong cách đặt câu khác nhau: service này ghi Order ID 125, service khác ghi orderId=125, service thứ ba ghi order_id: 125.
Các công cụ tự động không thể nhận diện chính xác đâu là mã lỗi, đâu là thời gian thực thi hay đâu là tên dịch vụ nếu không dùng các biểu thức chính quy (regex) phức tạp và tốn kém tài nguyên.
Structured Logging giải quyết triệt để bài toán này bằng cách chuẩn hóa mỗi sự kiện log thành một đối tượng dữ liệu có cấu trúc (thường là định dạng JSON). Thay vì một dòng văn bản thô, mỗi dòng log là một JSON object với các khóa (key-value) được định nghĩa thống nhất, giúp cả con người và máy móc đều dễ dàng xử lý.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Khái niệm và So sánh Structured Logging
Structured Logging là kỹ thuật tổ chức dữ liệu log thành các trường thông tin có tên và kiểu dữ liệu rõ ràng thay vì ghép nối thủ công vào một chuỗi ký tự dài.

So sánh trực quan:
Dạng Text truyền thống (Unstructured):

2026-08-14 10:15:22 INFO [order-service] Payment succeeded for Order ID 125

Dạng JSON có cấu trúc (Structured):

{
  "@timestamp": "2026-08-14T10:15:22.456Z",
  "log.level": "INFO",
  "service.name": "order-service",
  "log.logger": "com.quickbite.order_service.services.OrderService",
  "process.thread.name": "http-nio-8083-exec-1",
  "message": "Payment succeeded for Order ID 125"
}

Nhờ định dạng JSON, các công cụ giám sát và thu thập log có thể lập chỉ mục (index) và thực hiện các câu truy vấn lọc trực tiếp trên từng trường (service.name == 'order-service' AND log.level == 'ERROR') với tốc độ cao.

3.2 Các trường dữ liệu tiêu chuẩn trong JSON Log (Elastic Common Schema)
Hệ thống QuickBite tích hợp thư viện co.elastic.logging.logback.EcsEncoder, tự động ánh xạ thông tin log sang chuẩn ECS:

Tên trường (Field)

Kiểu dữ liệu

Ý nghĩa và Mục đích sử dụng

@timestamp

ISO-8601 String

Thời điểm chính xác log event được tạo tại microsecond.

log.level

String

Mức độ nghiêm trọng của sự kiện (INFO, WARN, ERROR).

service.name

String

Tên định danh của microservice phát sinh log.

log.logger

String

Tên đầy đủ của Java Class hoặc Logger phát sinh log.

process.thread.name

String

Tên của luồng (thread) xử lý request tương ứng.

message

String

Nội dung thông điệp nghiệp vụ cụ thể.

error.type, error.message

String

Tên class Exception và thông điệp lỗi khi có sự cố.

error.stack_trace

String

Toàn bộ dấu vết ngăn xếp ngoại lệ phục vụ debug.

trace.id

String

Mã định danh luồng request phân tán (từ MDC).

Lưu ý: Định dạng JSON chỉ cung cấp cấu trúc chứa dữ liệu; chất lượng điều tra vẫn phụ thuộc vào việc lập trình viên có viết thông điệp (message) rõ ràng và có ngữ cảnh hay không.

3.3 Cấu hình JSON Encoder trong Logback
Trong tệp logback-spring.xml, encoder mặc định được thay thế bằng EcsEncoder:

<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="co.elastic.logging.logback.EcsEncoder">
        <serviceName>${service.name}</serviceName>
    </encoder>
</appender>

Cơ chế này đảm bảo:

Log vẫn được ghi ra luồng stdout theo đúng nguyên tắc vận hành container của Docker.
Mỗi dòng log xuất ra là một chuỗi JSON độc lập (JSON-Lines / NDJSON), tương thích hoàn hảo với các hệ thống thu thập log hiện đại.
3.4 Nguyên tắc tối thiểu dữ liệu nghiệp vụ trong JSON Log
Dù JSON cho phép lưu trữ cấu trúc dữ liệu phong phú, lập trình viên vẫn phải tuân thủ nghiêm ngặt nguyên tắc tối thiểu hóa dữ liệu:

Nên đưa vào log: Các ID nghiệp vụ phục vụ tra cứu (orderId, customerId, restaurantId, transactionId).
Tuyệt đối không đưa vào log:
Không in toàn bộ payload đối tượng Request / Response lớn.
Không ghi mật khẩu, mã PIN, JWT token, mã bảo mật thẻ (CVV).
Không ghi thông tin nhận dạng cá nhân (PII) như số CCCD, nội dung tin nhắn riêng tư.
3.5 Bảng đối chiếu các tình huống tra cứu với JSON Log
Nhu cầu vận hành

Trường dữ liệu cần lọc

Cú pháp điều kiện logic

Kiểm tra lỗi của 1 service cụ thể

service.name, log.level

service.name == "order-service" AND log.level == "ERROR"

Truy vết một lỗi ngoại lệ cụ thể

error.type, log.logger

error.type : "*FeignException*"

Theo dõi hành trình request đa dịch vụ

trace.id

trace.id : "quickbite-req-001"

Giám sát thời gian thực

@timestamp, log.level

Chọn Last 10 minutes trong Time Range của Kibana, sau đó lọc log.level : "ERROR" nếu cần.

PHẦN 4. DEMO VÀ THỰC HÀNH (TRUY VẤN VÀ PHÂN TÍCH JSON LOG)
Phần thực hành hướng dẫn trích xuất và phân tích dữ liệu JSON log sinh ra từ cụm container QuickBite bằng các công cụ dòng lệnh tiêu chuẩn.

4.1 Cấu hình Dependency và Encoder của Microservice
Trong tệp build.gradle, dependency encoder được khai báo:

implementation 'co.elastic.logging:logback-ecs-encoder:1.5.0'

Và cấu hình tương ứng trong logback-spring.xml:

<property name="service.name" value="order-service"/>
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="co.elastic.logging.logback.EcsEncoder">
        <serviceName>${service.name}</serviceName>
    </encoder>
</appender>

4.2 Xuất JSON Log nguyên bản từ Docker Compose
Khi sử dụng Docker Compose, mặc định các dòng log sẽ bị chèn thêm tiền tố tên container ở đầu dòng (ví dụ: quickbite-order-1 | ). Để nhận được chuỗi JSON thuần túy phục vụ cho việc parse dữ liệu, sử dụng cờ --no-log-prefix:

cd ~/quickbite
docker compose logs --no-log-prefix --tail 20 quickbite-order

Kết quả đầu ra (mỗi dòng là 1 JSON object hợp lệ):

{"@timestamp":"2026-08-14T10:15:22.456Z","log.level":"INFO","message":"Order creation started: customerId=1, restaurantId=1","service.name":"order-service","process.thread.name":"http-nio-8083-exec-1","log.logger":"com.quickbite.order_service.services.OrderService"}

4.3 Sử dụng công cụ jq để lọc các trường dữ liệu quan trọng
Công cụ jq cho phép lọc và định dạng lại dữ liệu JSON nhanh chóng trên terminal Linux:

1. Trích xuất các trường cốt lõi dạng bảng gọn:
docker compose logs --no-log-prefix --tail 20 quickbite-order \
  | jq -c '{time: .["@timestamp"], level: .["log.level"], service: .["service.name"], msg: .message}'

2. Lọc riêng các dòng log có mức ERROR:
docker compose logs --no-log-prefix --tail 100 quickbite-order \
  | jq -c 'select(.["log.level"] == "ERROR")'

3. Trích xuất thông tin lỗi ngoại lệ kèm stacktrace:
docker compose logs --no-log-prefix --tail 100 quickbite-order \
  | jq -r 'select(.["error.type"] != null) | "\(.["@timestamp"]) [\(.["error.type"])]: \(.["error.message"])"'

4.4 Kiểm tra tính nhất quán giữa các Microservices
Theo dõi đồng thời JSON log của hai service khác nhau để đối chiếu:

docker compose logs --no-log-prefix --tail 30 quickbite-order quickbite-notification \
  | jq -c '{service: .["service.name"], level: .["log.level"], msg: .message}'

Kiểm tra:

Trường service.name phải phản ánh chính xác nguồn phát sinh (order-service hoặc notification-service).
Định dạng thời gian @timestamp phải đồng bộ theo chuẩn UTC ISO-8601.
4.5 Xử lý các lỗi phổ biến khi phân tích JSON Log
Nếu lệnh jq thông báo lỗi phân tích cú pháp (parse error), cần kiểm tra các nguyên nhân sau:

Thiếu cờ --no-log-prefix: Tiền tố mặc định của Compose khiến dòng log không còn là JSON hợp lệ.
Log phi cấu trúc bị trộn lẫn: Một số dòng log từ tiến trình khởi động của JVM hoặc script shell in trực tiếp ra console mà không qua Logback.
Chưa cấu hình ECS Encoder: Ứng dụng đang sử dụng cấu hình mặc định (plain text pattern).
PHẦN 5. TỔNG KẾT
Bản chất Structured Logging: Chuyển đổi thông điệp log từ dạng văn bản tự do sang dạng đối tượng dữ liệu có cấu trúc trường cố định.
Chuẩn ECS (Elastic Common Schema): Quy định các khóa chuẩn hóa (@timestamp, log.level, service.name, message) giúp dữ liệu log đồng nhất trong toàn bộ hệ thống microservices.
Tích hợp Docker & Dòng lệnh: Sử dụng docker compose logs --no-log-prefix kết hợp với jq để lọc và phân tích dữ liệu hiệu quả trực tiếp trên terminal máy chủ.
An toàn dữ liệu: Structured logging không làm cho dữ liệu nhạy cảm an toàn hơn; lập trình viên vẫn phải kiểm soát chặt chẽ các trường thông tin đưa vào log.