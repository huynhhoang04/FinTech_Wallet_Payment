Bài đọc: Log level và quy ước ghi log backend
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Phân biệt được ý nghĩa và phạm vi sử dụng của các mức log: TRACE, DEBUG, INFO, WARN và ERROR trong Spring Boot.
Lựa chọn được log level chính xác cho từng tình huống nghiệp vụ và sự cố kỹ thuật trong hệ thống QuickBite.
Thiết kế được thông điệp log ngắn gọn, chuẩn hóa, giàu ngữ cảnh để phục vụ điều tra lỗi nhanh chóng.
Sử dụng thành thạo parameterized logging của SLF4J để đưa ID nghiệp vụ và exception stacktrace vào log hiệu quả.
Nhận diện và loại bỏ các message log có nguy cơ để lộ thông tin nhạy cảm hoặc gây nhiễu loạn hệ thống giám sát.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Trong hệ thống QuickBite, service order-service phải xử lý rất nhiều tình huống nghiệp vụ đa dạng: khởi tạo đơn hàng, xác thực tính khả dụng của nhà hàng, trừ tiền qua cổng thanh toán, phát thông báo và kích hoạt quy trình hoàn tiền khi có sự cố. Nếu tất cả các sự kiện này đều được ghi ở cùng một mức log (log level), đội ngũ vận hành sẽ không thể xác định được sự kiện nào cần ưu tiên can thiệp.

Xét ba tình huống điển hình với các mức độ nghiêm trọng hoàn toàn khác nhau:

Đơn hàng được tạo và thanh toán thành công.
Nhà hàng tạm thời đóng cửa nên đơn hàng không thể tiếp tục xử lý.
Lỗi mất kết nối mạng tới dịch vụ thanh toán khiến giao dịch bị treo.
Nếu cả ba tình huống trên đều được ghi ở mức INFO, lỗi sập kết nối nghiêm trọng sẽ bị chìm lấp giữa hàng nghìn log nghiệp vụ bình thường. Ngược lại, nếu mọi trường hợp đều bị đẩy lên mức ERROR, đội ngũ kỹ sư sẽ bị quá tải bởi các cảnh báo giả (false alarms) và khó nhận biết sự cố thực sự.

Do đó, log level là công cụ cốt lõi để phân loại mức độ khẩn cấp của sự kiện. Việc thiết lập quy ước ghi log chuẩn giúp các dịch vụ như user-service, restaurant-service, order-service và notification-service tạo ra hệ thống log đồng nhất, an toàn và dễ dàng truy vết khi có sự cố.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Các Log Level trong Spring Boot
Spring Boot tích hợp sẵn framework logging SLF4J (kết hợp Logback làm engine mặc định). Các mức log được định nghĩa theo thứ tự tăng dần về mức độ nghiêm trọng:



Level

Ý nghĩa và Mục đích

Tình huống áp dụng trong QuickBite

TRACE

Ghi nhận chi tiết từng bước thực thi nhỏ nhất, phục vụ điều tra sâu một thuật toán hoặc hàm cụ thể.

Dòng tính toán chi tiết giá trị trung gian của từng món ăn và thuế phí.

DEBUG

Cung cấp thông tin kỹ thuật hỗ trợ chẩn đoán trong giai đoạn phát triển hoặc kiểm thử tích hợp.

Danh sách ID món ăn nhận về từ restaurant-service, thông số kết nối API.

INFO

Ghi nhận các mốc sự kiện nghiệp vụ quan trọng khi hệ thống vận hành bình thường.

Đơn hàng tạo thành công, giao dịch thanh toán hoàn tất, service khởi động xong.

WARN

Cảnh báo tình huống bất thường nhưng ứng dụng đã kiểm soát được, không làm sập tiến trình.

Nhà hàng tạm đóng, món ăn hết hàng, token xác thực sắp hết hạn.

ERROR

Báo cáo lỗi kỹ thuật không mong muốn khiến thao tác nghiệp vụ thất bại, cần can thiệp xử lý.

Không thể kết nối database, timeout khi gọi dịch vụ thanh toán, ngoại lệ chưa bắt.

Quy tắc triển khai: Mức TRACE và DEBUG sinh ra lượng dữ liệu rất lớn nên chỉ được kích hoạt có thời hạn khi cần chẩn đoán lỗi đặc biệt. Trên môi trường Production, mức cấu hình chuẩn khuyến nghị là INFO.

3.2 Phân biệt rõ ràng giữa INFO, WARN và ERROR
Ba mức log này thường xuyên bị sử dụng nhầm lẫn trong thực tế:

Mức INFO: Dành cho các sự kiện mong đợi và xử lý thành công. Cần ghi rõ hành động và đối tượng liên quan:

log.info("Order created: orderId={}, customerId={}, restaurantId={}",
        savedOrder.getId(), savedOrder.getCustomerId(), savedOrder.getRestaurantId());

Mức WARN: Ghi nhận sự kiện bất thường nhưng có kịch bản xử lý sẵn. Không nên ghi WARN cho mọi lỗi HTTP 4xx đơn giản (như người dùng nhập sai định dạng form), mà chỉ dành cho các biến cố nghiệp vụ đáng lưu tâm:

log.warn("Order rejected because restaurant is unavailable: restaurantId={}",
        request.getRestaurantId());

Mức ERROR: Dành cho các lỗi khiến chức năng bị gián đoạn hoặc thất bại. Khi bắt được exception, bắt buộc phải truyền đối tượng exception vào tham số cuối cùng để Logback tự động in stacktrace:

log.error("Payment processing failed: orderId={}", savedOrder.getId(), exception);

Lưu ý: Tuyệt đối không dùng ERROR cho các trường hợp nghiệp vụ thông thường (như nhà hàng tạm đóng), và không dùng WARN để che giấu các lỗi hạ tầng nghiêm trọng (như mất kết nối cơ sở dữ liệu).

3.3 Quy ước đặt tên và cấu trúc thông điệp Log (Log Message)
Một message log chuẩn mực trong backend cần trả lời chính xác ba câu hỏi:

Sự kiện gì đã xảy ra?
Đối tượng hoặc tài nguyên nào bị ảnh hưởng?
Nguyên nhân hoặc kết quả cụ thể là gì?
Các nguyên tắc cần tuân thủ:

Mở đầu rõ ràng: Bắt đầu bằng hành động hoặc trạng thái cụ thể: Order created, Payment failed, Notification sent.
Chuẩn hóa tên trường (Key-Value): Sử dụng các khóa nhất quán trong toàn bộ hệ thống (orderId, customerId, restaurantId, reason).
Tránh log đối tượng thô: Không in toàn bộ JSON request/response object; chỉ trích xuất các ID định danh quan trọng.
Không dùng thông điệp mơ hồ: Loại bỏ các câu log vô nghĩa như Error occurred, Failed here, Test 123.
Bảo vệ dữ liệu nhạy cảm: Tuyệt đối không ghi mật khẩu, JWT token, mã bí mật, header xác thực hoặc thông tin cá nhân của khách hàng.
So sánh cú pháp:
// Cách viết không đạt: Không có ngữ cảnh, không biết bản ghi nào bị ảnh hưởng
log.error("Payment failed");
 
// Cách viết chuẩn: Định danh chính xác bản ghi, lý do và đính kèm exception
log.error("Payment failed: orderId={}, reason={}", orderId, reason, exception);

3.4 Parameterized Logging và xử lý Exception với SLF4J
SLF4J sử dụng cú pháp dấu ngoặc nhọn {} làm vị trí giữ chỗ (placeholder). Cơ chế này giúp tối ưu hiệu năng vì chuỗi ký tự chỉ được nối khi log level đó thực sự được kích hoạt:

log.info("Order accepted by restaurant: orderId={}", order.getId());
log.warn("Menu item unavailable: menuItemId={}, restaurantId={}",
        itemReq.getMenuItemId(), request.getRestaurantId());
log.error("Notification delivery failed: userId={}", userId, exception);

Tránh nối chuỗi thủ công:
// Không nên: Gây lãng phí bộ nhớ và khó xử lý stacktrace
log.error("Exception during payment for Order ID " + savedOrder.getId(), exception);
 
// Nên dùng: Tối ưu hiệu năng, cấu trúc đồng nhất và giữ nguyên stacktrace
log.error("Payment processing failed: orderId={}", savedOrder.getId(), exception);

3.5 Rà soát và loại bỏ nguy cơ rò rỉ dữ liệu riêng tư
Trong service notification-service, thông điệp gửi tới người dùng có thể chứa các thông tin nhạy cảm. Việc ghi toàn bộ tiêu đề và nội dung thông báo vào log sẽ vi phạm nghiêm trọng chính sách bảo mật:

// Không an toàn: Làm lộ nội dung thông báo và dữ liệu cá nhân
log.info("Notification sent successfully to user {}: Title='{}', Content='{}', Type={}",
        request.getUserId(), request.getTitle(), request.getContent(), request.getType());
 
// An toàn và chuẩn hóa: Chỉ lưu trữ ID định danh và loại thông báo phục vụ tra cứu
log.info("Notification sent: notificationId={}, userId={}, type={}",
        notification.getId(), notification.getUserId(), notification.getType());

PHẦN 4. DEMO VÀ THỰC HÀNH (RÀ SOÁT VÀ CHUẨN HÓA LOG TRONG QUICKBITE)
Phần thực hành này tập trung vào việc đánh giá chất lượng các dòng log hiện có trong mã nguồn QuickBite, đối chiếu với các nguyên tắc chuẩn hóa và quan sát kết quả thực tế trên container.

4.1 Rà soát các câu lệnh log trong OrderService
Xem xét các đoạn mã ghi log hiện tại trong lớp OrderService.java của order-service:

// Các câu lệnh hiện tại trong OrderService.java
log.info("Order creation started: customerId={}, restaurantId={}",
        request.getCustomerId(), request.getRestaurantId());
log.warn("Restaurant is closed or not found: {}", request.getRestaurantId());
log.warn("Payment failed for Order ID {}: {}", savedOrder.getId(), errorMessage);
log.info("Payment succeeded for Order ID {}", savedOrder.getId());
log.error("Payment processing failed: orderId={}", savedOrder.getId(), exception);

Bảng phân tích và đề xuất cải tiến:

Lời gọi Log hiện tại

Level

Đánh giá kỹ thuật

Đề xuất chuẩn hóa

Order creation started...

INFO

Phù hợp cho sự kiện bắt đầu nghiệp vụ; thông điệp dùng tên khóa định danh rõ ràng.

Giữ customerId và restaurantId; không ghi request body.

Restaurant is closed or not found...

WARN

Phù hợp vì là bất thường có kiểm soát; thông điệp hiện tại vẫn gộp hai nguyên nhân có thể khác nhau.

Khi mô hình lỗi được tách riêng, ghi rõ reason thay vì suy đoán trong log.

Payment failed for Order ID...

WARN

Cần xem xét: nếu thanh toán thất bại do lỗi kỹ thuật thì phải là ERROR, nếu do thẻ hết hạn thì WARN.

log.warn("Payment declined: orderId={}, reason={}", order.getId(), reason);

Payment processing failed...

ERROR

Đúng mức độ nghiêm trọng; exception được truyền ở tham số cuối để Logback ghi stack trace.

Giữ placeholder, không nối chuỗi khi ghi log.

4.2 Thực nghiệm và quan sát Log luồng đặt hàng
Khởi chạy và theo dõi luồng log thời gian thực của service tiếp nhận đơn hàng:

cd ~/quickbite
docker compose logs -f quickbite-order

Thực hiện gửi request đặt món từ client (hoặc Postman).
Quan sát các dòng log xuất hiện trên màn hình và phân tích:
Dòng log nào đánh dấu thời điểm bắt đầu tiếp nhận request?
Log có thể hiện đầy đủ trạng thái thành công hay thất bại của các bước xử lý không?
Trường hợp xảy ra lỗi, log có cung cấp đủ stacktrace và ID nghiệp vụ để tra cứu không?
(Nhấn Ctrl + C để kết thúc phiên theo dõi log).

4.3 Rà soát an toàn dữ liệu trong NotificationService
Trong NotificationService.java, câu lệnh log sau khi phát thông báo đang ghi nhận toàn bộ payload:

// Log hiện tại
log.info("Notification sent successfully to user {}: Title='{}', Content='{}', Type={}",
        request.getUserId(), request.getTitle(), request.getContent(), request.getType());

Nhược điểm: Trường Content có thể chứa mã OTP, số dư hoặc nội dung riêng tư của khách hàng.

Đề xuất chuẩn hóa thay thế:

// Log chuẩn hóa
log.info("Notification sent: notificationId={}, userId={}, type={}",
        notification.getId(), notification.getUserId(), notification.getType());

4.4 Quản lý mức Log trong cấu hình Logback Production
Trong tệp logback-spring.xml của mỗi service, mức log gốc (root logger) được thiết lập mặc định ở mức INFO:

<root level="INFO">
    <appender-ref ref="CONSOLE" />
</root>

Ý nghĩa vận hành:

Toàn bộ các dòng log ở mức TRACE và DEBUG sẽ tự động bị bỏ qua trong môi trường chạy bình thường.
Chỉ các log từ mức INFO, WARN và ERROR mới được xuất ra stdout/stderr của container, giúp giảm tải dung lượng đĩa và tối ưu hiệu năng xử lý của hệ thống.
PHẦN 5. TỔNG KẾT
Nguyên tắc Log Level: Sử dụng INFO cho sự kiện nghiệp vụ quan trọng, WARN cho các biến cố bất thường đã kiểm soát, và ERROR cho các sự cố kỹ thuật làm gián đoạn hệ thống. Mức TRACE và DEBUG chỉ bật khi cần chẩn đoán chuyên sâu.
Cấu trúc Message chuẩn: Thông điệp log phải rõ ràng, ngắn gọn, tuân thủ định dạng key-value với các ID nghiệp vụ quan trọng (orderId, userId) và tuyệt đối không ghi dữ liệu nhạy cảm.
Parameterized Logging: Luôn dùng placeholder {} của SLF4J để tối ưu bộ nhớ và truyền đối tượng exception ở tham số cuối cùng để bảo toàn stacktrace.
Cấu hình Production: Giữ mức root logger ở INFO trên môi trường sản phẩm để đảm bảo hiệu năng và giảm nhiễu hệ thống.