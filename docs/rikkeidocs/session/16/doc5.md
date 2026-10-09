Bài đọc: MDC và traceId trong luồng request phân tán
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được lý do vì sao hệ thống microservices bắt buộc phải có một mã định danh liên kết chung (traceId) cho mỗi luồng request.
Mô tả được nguyên lý hoạt động của MDC (Mapped Diagnostic Context) và cơ chế lưu trữ ngữ cảnh theo luồng (ThreadLocal) trong Spring Boot.
Đọc hiểu và triển khai được TraceIdFilter để trích xuất hoặc tự sinh traceId cho mọi HTTP request đi vào hệ thống.
Giải thích và áp dụng được FeignRequestInterceptor để lan truyền mã traceId qua các HTTP request liên dịch vụ (service-to-service).
Truy vết và xâu chuỗi được toàn bộ các dòng log phân tán thuộc về cùng một phiên giao dịch của khách hàng.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Trong kiến trúc của QuickBite, một thao tác nghiệp vụ đơn lẻ của người dùng (như đặt món) sẽ kích hoạt một chuỗi các lời gọi dịch vụ liên tiếp:

order-service tiếp nhận yêu cầu và khởi tạo đơn hàng.
order-service gọi restaurant-service qua Feign client để kiểm tra tính khả dụng của món ăn.
order-service gọi user-service để trừ số dư ví thanh toán.
order-service gọi notification-service để gửi thông báo xác nhận.
Do mỗi microservice chạy trong một Docker container độc lập và ghi log riêng rẽ, các dòng log sẽ bị phân tán trên nhiều nguồn khác nhau. Khi có hàng trăm request diễn ra đồng thời, việc dựa vào mốc thời gian (timestamp) hoàn toàn không thể giúp phân biệt các request:

10:00:01 [order-service] Order creation started: customerId=101, restaurantId=10
10:00:01 [order-service] Order creation started: customerId=202, restaurantId=20
10:00:02 [restaurant-service] Restaurant availability checked: restaurantId=10, open=true
10:00:02 [user-service] Wallet deducted: userId=101, transactionId=ORDER-500-DEDUCT

Khi xuất hiện lỗi ở bước trừ tiền, đội ngũ vận hành không thể xác định dòng Wallet balance deducted thuộc về đơn hàng của khách hàng 101 hay 202.

Giải pháp: Gắn một mã định danh duy nhất (traceId) vào đầu luồng request và sử dụng MDC để tự động đính kèm mã này vào mọi dòng log phát sinh trong suốt vòng đời của request.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Khái niệm TraceId và Cơ chế MDC (Mapped Diagnostic Context)
traceId: Là một chuỗi ký tự định danh duy nhất (thường là UUID) đại diện cho một luồng nghiệp vụ từ khi bắt đầu đến khi kết thúc qua mọi service.
MDC (Mapped Diagnostic Context): Là một cấu trúc dữ liệu dạng Map được quản lý bởi SLF4J, hoạt động trên cơ chế ThreadLocal. Bất kỳ thông tin nào được đưa vào MDC sẽ tự động sẵn sàng cho Logback chèn vào mỗi dòng log phát sinh bởi luồng (thread) đó.


Nguyên tắc an toàn bắt buộc: Phải luôn gọi MDC.remove() (hoặc MDC.clear()) bên trong khối finally. Các web server như Tomcat sử dụng Thread Pool để tái sử dụng luồng; nếu không dọn dẹp MDC, request tiếp theo được xử lý trên luồng đó sẽ bị gán nhầm traceId của request trước.

3.2 Kiến trúc TraceIdFilter trong Spring Boot
Trong mỗi microservice của QuickBite, một HTTP Filter được đăng ký để chặn các request đến đầu tiên:

package com.quickbite.order_service.configs.filter;
 
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;
 
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID_KEY = "trace.id";
 
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpServletRequest) {
            // 1. Lấy traceId từ HTTP Header hoặc tự sinh mới nếu chưa có
            String traceId = httpServletRequest.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString();
            }
 
            // 2. Gắn traceId vào MDC của luồng hiện tại
            MDC.put(MDC_TRACE_ID_KEY, traceId);
 
            // 3. Trả traceId về Response Header để Client có thể tra cứu
            if (response instanceof HttpServletResponse httpServletResponse) {
                httpServletResponse.setHeader(TRACE_ID_HEADER, traceId);
            }
        }
 
        try {
            chain.doFilter(request, response);
        } finally {
            // 4. Xóa ThreadLocal, tránh rò rỉ context sang request kế tiếp
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }
}

3.3 Cơ chế lan truyền TraceId qua Feign Client (FeignRequestInterceptor)
MDC chỉ tồn tại trong phạm vi bộ nhớ của một tiến trình Java. Khi order-service thực hiện một cuộc gọi HTTP sang restaurant-service bằng OpenFeign, ngữ cảnh MDC không tự động truyền qua mạng.

Để duy trì tính liên tục của traceId, QuickBite sử dụng FeignRequestInterceptor:

package com.quickbite.order_service.configs;
 
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
 
@Component
public class FeignRequestInterceptor implements RequestInterceptor {
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID_KEY = "trace.id";
 
    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(MDC_TRACE_ID_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            template.header(TRACE_ID_HEADER, traceId);
        }
    }
}

Quy trình phối hợp:

FeignRequestInterceptor đọc trace.id từ MDC của order-service.
Gắn giá trị này vào header HTTP X-Trace-Id của request gửi đi.
restaurant-service tiếp nhận request, TraceIdFilter của nó đọc header X-Trace-Id và nạp vào MDC nội bộ của nó.
3.4 Sơ đồ luồng lan truyền TraceId trong QuickBite

3.5 Giới hạn và lưu ý khi sử dụng MDC
Xử lý bất đồng bộ (Async / ThreadPool): Khi sử dụng @Async, CompletableFuture hoặc Reactive Streams, tiến trình xử lý sẽ chuyển sang một Thread mới. Khi đó, MDC của Thread cha không tự động sao chép sang Thread con nếu không có cơ chế TaskDecorator hoặc cấu hình propagation riêng.
Đồng nhất tên Header & Key: Tất cả các microservices trong hệ thống phải thống nhất sử dụng chung một tên header (ví dụ: X-Trace-Id) và một khóa MDC (ví dụ: trace.id).
PHẦN 4. DEMO VÀ THỰC HÀNH (TRUY VẾT REQUEST PHÂN TÁN)
Phần thực hành hướng dẫn kiểm tra sự liên kết của mã traceId khi gửi một request tạo đơn hàng đi qua nhiều container microservices.

4.1 Kiểm tra cấu hình Filter và Interceptor
Trong các service của QuickBite, đảm bảo hai thành phần sau đã được kích hoạt:

TraceIdFilter được đăng ký làm Filter ưu tiên cao nhất để bao bọc toàn bộ chu kỳ xử lý request.
FeignRequestInterceptor được đánh dấu @Component hoặc khai báo trong Feign Configuration để tự động đính kèm header vào mọi cuộc gọi HTTP hướng ngoại.
4.2 Thực nghiệm gửi Request có TraceId tùy biến
Đặt một giá trị traceId nhận diện phục vụ kiểm thử:

export DEMO_TRACE_ID="demo-trace-order-999"
export CUSTOMER_ID=1 # thay bằng ID khách hàng có thật
export RESTAURANT_ID=1 # thay bằng ID nhà hàng đang mở
export DELIVERY_ADDRESS_ID=1 # thay bằng địa chỉ thuộc khách hàng trên
export MENU_ITEM_ID=1 # thay bằng món thuộc nhà hàng trên và còn available

Gửi một HTTP POST request tạo đơn hàng tới endpoint của order-service kèm header X-Trace-Id:

curl -X POST "http://localhost:8083/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: ${DEMO_TRACE_ID}" \
  -d "{
    \"customerId\": ${CUSTOMER_ID},
    \"restaurantId\": ${RESTAURANT_ID},
    \"deliveryAddressId\": ${DELIVERY_ADDRESS_ID},
    \"items\": [{\"menuItemId\": ${MENU_ITEM_ID}, \"quantity\": 2}]
  }"

Thay các ID mẫu bằng dữ liệu thật của môi trường demo; khách hàng cần đủ số dư. Đây là các trường đúng với OrderRequest và OrderItemRequest hiện tại của QuickBite.

Quan sát: Kiểm tra response header trả về từ server, đảm bảo trường X-Trace-Id có giá trị đúng bằng demo-trace-order-999.

4.3 Truy vết toàn bộ chuỗi Log phân tán theo TraceId
Sử dụng docker compose logs kết hợp với lệnh lọc theo traceId trên toàn bộ các service liên quan:

cd ~/quickbite
docker compose logs --no-log-prefix --tail 200 \
  quickbite-order quickbite-restaurant quickbite-user quickbite-notification \
  | grep "${DEMO_TRACE_ID}"

Kết quả mong đợi: Toàn bộ các dòng log từ nhiều container khác nhau xuất hiện theo đúng trình tự nghiệp vụ với cùng một mã trace.id:

{"@timestamp":"2026-08-14T10:30:01.100Z","service.name":"order-service","trace.id":"demo-trace-order-999","message":"Order creation started: customerId=1, restaurantId=1"}
{"@timestamp":"2026-08-14T10:30:01.250Z","service.name":"restaurant-service","trace.id":"demo-trace-order-999","message":"Restaurant availability checked: restaurantId=1, open=true"}
{"@timestamp":"2026-08-14T10:30:01.400Z","service.name":"user-service","trace.id":"demo-trace-order-999","message":"Wallet deducted: userId=1, transactionId=ORDER-500-DEDUCT"}
{"@timestamp":"2026-08-14T10:30:01.500Z","service.name":"notification-service","trace.id":"demo-trace-order-999","message":"Notification sent: userId=1, type=IN_APP"}
{"@timestamp":"2026-08-14T10:30:01.600Z","service.name":"order-service","trace.id":"demo-trace-order-999","message":"Order created successfully: orderId=500"}

4.4 Kiểm tra hành vi tự sinh UUID khi không có Header
Gửi lại câu lệnh curl ở trên nhưng không truyền header -H "X-Trace-Id: ...":

curl -X POST "http://localhost:8083/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d "{\"customerId\": ${CUSTOMER_ID}, \"restaurantId\": ${RESTAURANT_ID}, \"deliveryAddressId\": ${DELIVERY_ADDRESS_ID}, \"items\": [{\"menuItemId\": ${MENU_ITEM_ID}, \"quantity\": 2}]}"

Quan sát kết quả:

TraceIdFilter tự động sinh ra một chuỗi UUID mới.
Mã UUID này xuất hiện trong X-Trace-Id của response header và được lan truyền đồng bộ sang các service phụ thuộc.
PHẦN 5. TỔNG KẾT
Giá trị của TraceId: Là sợi dây liên kết duy nhất giúp xâu chuỗi toàn bộ các sự kiện log độc lập của nhiều microservices thành một bức tranh toàn cảnh về hành trình của một request.
MDC trong Spring Boot: Cung cấp giải pháp quản lý ngữ cảnh log theo từng luồng xử lý thông qua ThreadLocal.
Cơ chế lan truyền: Sử dụng HTTP Filter để tiếp nhận/khởi tạo traceId ở đầu vào và sử dụng Feign Interceptor để truyền tiếp qua mạng sang các service phụ thuộc.
Quy tắc dọn dẹp: Luôn giải phóng MDC trong khối finally để ngăn chặn rò rỉ ngữ cảnh giữa các request trong Thread Pool.