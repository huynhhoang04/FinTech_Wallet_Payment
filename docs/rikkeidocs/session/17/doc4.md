Bài đọc: Phân tích và debug log trong Kibana bằng traceId
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Tạo và cấu hình thành thạo Data View cho tập chỉ mục quickbite-* trong giao diện quản trị Kibana.
Sử dụng Kibana Discover kết hợp ngôn ngữ truy vấn KQL để lọc log linh hoạt theo thời gian, tên service và mức độ nghiêm trọng.
Truy vết toàn diện chuỗi hành trình của một request phân tán đi qua nhiều microservices bằng mã định danh trace.id.
Phân tích và chẩn đoán chính xác nguyên nhân gốc rễ (Root Cause) của sự cố thay vì chỉ dừng lại ở triệu chứng lỗi bề mặt.
Phân loại rõ ràng giữa lỗi nghiệp vụ (Business errors), lỗi phụ thuộc liên dịch vụ (Dependency errors) và lỗi sập hạ tầng (System errors).
PHẦN 2. VẤN ĐỀ THỰC TẾ
Sau khi ngăn xếp EFK thu gom thành công toàn bộ log vào Elasticsearch, kho dữ liệu sẽ nhanh chóng tích lũy hàng trăm nghìn dòng log mỗi ngày. Dữ liệu tập trung này chỉ thực sự phát huy giá trị khi kỹ sư vận hành biết cách khai thác công cụ phân tích để khoanh vùng và xử lý sự cố.

Giả sử khách hàng phản ánh giao dịch đặt món bị lỗi vào khoảng 10:15 sáng. Quy trình điều tra hiệu quả không phải là đọc tuần tự hàng nghìn dòng log, mà cần trả lời chính xác các câu hỏi:

1. Trong khung giờ từ 10:10 đến 10:20:
   - order-service đã tiếp nhận những request nào bị lỗi ERROR?
   - Mã trace.id gắn liền với request bị lỗi đó là gì?
2. Sử dụng mã trace.id đó:
   - restaurant-service, user-service và notification-service đã xử lý các bước nào?
   - Ngoại lệ (Exception) đầu tiên xuất hiện tại service nào trong chuỗi xử lý?

Kibana Discover là công cụ trực quan hóa trung tâm cung cấp khả năng lọc đa chiều trên các trường dữ liệu có cấu trúc do Fluentd và Elasticsearch lập chỉ mục.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Data View và Trường Thời Gian (Time Field)
Trong Kibana, Data View (trước đây gọi là Index Pattern) là cơ chế định vị để Kibana biết cần đọc dữ liệu từ các chỉ mục nào trong Elasticsearch.

Pattern cấu hình: quickbite-* (khớp với tất cả các chỉ mục theo ngày do Fluentd tạo ra, ví dụ quickbite-2026.08.14).
Time Field: Bắt buộc chọn @timestamp. Điều này đảm bảo toàn bộ các bộ lọc thời gian và biểu đồ phân bố log trong Kibana sẽ căn cứ chính xác theo thời điểm log được phát sinh tại Spring Boot.
3.2 Bảng trường dữ liệu cốt lõi trong Kibana Discover
Để tối ưu hóa không gian hiển thị và tăng tốc độ đọc dữ liệu, kỹ sư vận hành nên tùy biến các cột hiển thị trong Discover:

Tên trường (Field)

Chức năng kỹ thuật

Giá trị trong điều tra sự cố

@timestamp

Thời gian thực thi chuẩn UTC.

Sắp xếp trình tự các sự kiện theo đúng dòng thời gian.

service.name

Tên microservice phát sinh log.

Phân biệt chính xác nguồn gốc sự kiện (order-service, user-service).

log.level

Mức độ nghiêm trọng.

Lọc nhanh các cảnh báo WARN hoặc lỗi ERROR.

message

Thông điệp nghiệp vụ.

Đọc tóm tắt diễn biến xử lý.

trace.id

Mã định danh luồng phân tán.

Xâu chuỗi toàn bộ các log liên dịch vụ của cùng 1 phiên request.

error.type

Tên lớp ngoại lệ (Exception class).

Xác định bản chất lỗi kỹ thuật (ví dụ FeignException, SQLException).

error.message

Thông điệp chi tiết của ngoại lệ.

Nắm bắt nguyên nhân cụ thể gây gián đoạn code.

3.3 Ngôn ngữ truy vấn Kibana (KQL - Kibana Query Language)
KQL cung cấp cú pháp tìm kiếm trực quan, hỗ trợ gợi ý tự động (auto-complete) trên thanh tìm kiếm của Discover:

1. Lọc theo service cụ thể:
service.name : "order-service"

2. Lọc các sự cố nghiêm trọng của một service:
service.name : "order-service" and log.level : "ERROR"

3. Tìm kiếm toàn bộ log của một chuỗi request theo traceId:
trace.id : "quickbite-demo-001"

4. Tìm các lỗi ngoại lệ liên quan đến Feign Client:
error.type : "*FeignException*"

Để xem toàn bộ lỗi, sử dụng truy vấn riêng log.level : "ERROR"; không ghép bằng or nếu mục tiêu là khoanh vùng riêng lỗi Feign.

Quy tắc điều tra vàng: Luôn thu hẹp khoảng thời gian (Time Range) ở góc trên bên phải màn hình (ví dụ: Last 15 minutes, Today) trước khi thực hiện câu truy vấn KQL để tối ưu hiệu năng tìm kiếm của Elasticsearch.

3.4 Quy trình 6 bước Debug sự cố phân tán bằng trace.id
BƯỚC 1: Thu hẹp Time Range về thời điểm phát sinh sự cố (ví dụ: ±10 phút quanh mốc báo lỗi).
   ↓
BƯỚC 2: Nhập KQL: service.name : "order-service" and log.level : "ERROR" để tìm sự kiện lỗi đầu vào.
   ↓
BƯỚC 3: Mở rộng chi tiết dòng log lỗi và sao chép giá trị của trường trace.id.
   ↓
BƯỚC 4: Xóa bộ lọc cũ, nhập KQL mới: trace.id : "<mã_trace_id_vừa_sao_chép>".
   ↓
BƯỚC 5: Sắp xếp bảng log theo chiều tăng dần của @timestamp (từ trên xuống dưới).
   ↓
BƯỚC 6: Đọc tuần tự các bước xử lý qua các service để tìm dòng ERROR đầu tiên (Root Cause).

3.5 Phân loại và định hướng xử lý sự cố qua Log
Nhóm sự cố

Triệu chứng điển hình trong Log

Hướng xử lý kỹ thuật

Lỗi nghiệp vụ có kiểm soát (Business Flow)

Xuất hiện log WARN: Restaurant is closed or not found: <restaurantId>.

Hoạt động bình thường của ứng dụng; QuickBite trả 400 Bad Request cho RuntimeException nghiệp vụ.

Lỗi dịch vụ phụ thuộc (Dependency Outage)

Xuất hiện ERROR kèm feign.RetryableException hoặc Read timed out.

Kiểm tra trạng thái sống/chết (health) và độ trễ mạng của service đích được gọi.

Lỗi hạ tầng hệ thống (System Exception)

Xuất hiện ERROR kèm CannotCreateTransactionException hoặc NullPointerException.

Kiểm tra kết nối cơ sở dữ liệu, tài nguyên RAM/CPU hoặc lỗi logic trong mã nguồn.

PHẦN 4. DEMO VÀ THỰC HÀNH (TRUY VẾT LỖI TẠO ĐƠN TRÊN KIBANA)
4.1 Tạo Data View trên giao diện Kibana
Mở trình duyệt web truy cập Kibana qua SSH Tunnel: http://127.0.0.1:5601.
Truy cập menu bên trái: Management → Stack Management → Data Views.
Nhấn Create data view và nhập các thông số:
Name: quickbite-logs
Index pattern: quickbite-*
Timestamp field: Chọn @timestamp từ danh sách thả xuống.
Nhấn Save data view to Kibana.
4.2 Cấu hình giao diện Discover
Điều hướng tới mục Analytics → Discover.
Chọn Data View vừa tạo là quickbite-logs.
Đặt bộ lọc thời gian: Last 15 minutes.
Ở thanh danh sách trường bên trái (Available fields), nhấn dấu cộng + tại các trường:
service.name
log.level
message
trace.id
Màn hình Discover sẽ chuyển sang dạng bảng dữ liệu gọn gàng, trực quan và dễ đọc.

4.3 Thực nghiệm gửi Request có TraceId kiểm thử
Trên terminal máy chủ Ubuntu, gửi một request đặt món kèm mã traceId xác định:

# Đặt mã định danh kiểm thử
export TEST_TRACE_ID="trace-kibana-verify-888"
export CUSTOMER_ID=1 # thay bằng ID khách hàng có thật
export RESTAURANT_ID=1 # thay bằng ID nhà hàng đang mở
export DELIVERY_ADDRESS_ID=1 # thay bằng địa chỉ thuộc khách hàng trên
export MENU_ITEM_ID=1 # thay bằng món thuộc nhà hàng trên và còn available
 
# Gửi HTTP request tới order-service
curl -X POST "http://localhost:8083/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: ${TEST_TRACE_ID}" \
  -d "{
    \"customerId\": ${CUSTOMER_ID},
    \"restaurantId\": ${RESTAURANT_ID},
    \"deliveryAddressId\": ${DELIVERY_ADDRESS_ID},
    \"items\": [{\"menuItemId\": ${MENU_ITEM_ID}, \"quantity\": 1}]
  }"

Thay các ID mẫu bằng dữ liệu thật của môi trường demo; khách hàng cần đủ số dư.

4.4 Lần theo hành trình Request trên Discover
Trên thanh tìm kiếm KQL của Kibana, nhập câu lệnh:
trace.id : "trace-kibana-verify-888"

Nhấn Update (hoặc phím Enter).
Đảm bảo cột @timestamp được sắp xếp theo chiều tăng dần (Oldest first).
Phân tích kết quả hiển thị: Toàn bộ hành trình của request xuất hiện mạch lạc qua từng service:

order-service: Bắt đầu tiếp nhận đơn hàng (Order creation started).
restaurant-service: Kiểm tra trạng thái nhà hàng (Restaurant availability checked: ...).
user-service: Thực hiện trừ tiền ví điện tử (Wallet deducted).
notification-service: Gửi thông báo thành công (Notification sent).
order-service: Hoàn tất giao dịch (Order created successfully).
4.5 Xử lý các tình huống không tìm thấy Log trên Kibana
Nếu nhập KQL nhưng Kibana báo No results match your search criteria:

Kiểm tra Time Range: Mở rộng khoảng thời gian tìm kiếm lên Last 1 hour hoặc Today.
Kiểm tra Elasticsearch Index: Chạy lệnh curl http://127.0.0.1:9200/_cat/indices?v trên host để xem chỉ mục có đang nhận dữ liệu hay không.
Kiểm tra bộ lọc KQL: Đảm bảo trường tìm kiếm được gõ đúng chính tả trace.id thay vì traceId hay trace_id.
PHẦN 5. TỔNG KẾT
Trung tâm điều tra Kibana: Biến toàn bộ dữ liệu log JSON thô trong Elasticsearch thành công cụ phân tích trực quan mạnh mẽ.
Data View chuẩn: Sử dụng index pattern quickbite-* với time field @timestamp để đồng bộ thời gian sự kiện.
Ngôn ngữ KQL: Hỗ trợ lọc đa điều kiện trên các trường có cấu trúc (service.name, log.level, trace.id).
Truy vết phân tán: Sử dụng trace.id để xâu chuỗi toàn bộ nhật ký liên dịch vụ, cho phép xác định chính xác nguyên nhân gốc rễ của sự cố chỉ trong vài giây.