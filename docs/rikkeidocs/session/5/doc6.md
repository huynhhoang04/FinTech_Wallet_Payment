Bài đọc: Luồng yêu cầu đặt hàng end-to-end từ Client qua API Gateway
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, bạn sẽ có khả năng:

Phác thảo và giải thích sơ đồ luồng dữ liệu (Data flow) của một giao dịch đặt món ăn hoàn chỉnh đi qua toàn bộ 5 container trong hệ thống QuickBite.
Phân tích được cơ chế phối hợp hoạt động (Service Orchestration) giữa các dịch vụ độc lập để hoàn tất một chu trình nghiệp vụ phức tạp.
Thực hiện viết mã xử lý lỗi và thiết lập giao dịch bù (Compensating Transaction) để hoàn tiền ví cho người dùng khi đơn hàng bị nhà hàng từ chối.
Đọc hiểu và truy vết dòng nhật ký đan xen (interlaced logs) của các container để chẩn đoán lỗi trong môi trường phân tán.
  

2. Vấn đề thực tập (Nỗi đau của việc thất thoát tài chính gặp lỗi phân tán)
Trong một hệ thống đơn thể (Monolithic Application) sử dụng một database duy nhất, việc đảm bảo tính nhất quán dữ liệu rất dễ dàng. Lập trình viên chỉ cần thêm annotation @Transactional vào hàm đặt hàng. Nếu bước trừ tiền thành công nhưng bước gán tài xế bị lỗi, cơ sở dữ liệu sẽ tự động rollback (quay lui), trả lại mọi thứ như ban đầu.

 

Tuy nhiên, trong kiến trúc Microservices của QuickBite:

Dữ liệu ví tiền nằm ở DB quickbite_user_db.
Dữ liệu đơn hàng nằm ở DB quickbite_order_db.
 

Nếu chúng ta không thiết kế luồng xử lý lỗi phân tán chặt chẽ, tình huống sau sẽ xảy ra:

Khách đặt đơn, order-service gọi sang user-service trừ thành công 100.000đ trong ví của khách hàng và cập nhật số dư mới vào DB quickbite_user_db.
order-service gọi tiếp sang restaurant-service báo nhà hàng chuẩn bị món.
Nhà hàng bấm nút "Từ chối" vì nguyên liệu đã hết.
order-service cập nhật trạng thái đơn thành CANCELLED.
 

Hậu quả chí mạng: Tiền của khách hàng đã bị trừ mất tiêu nhưng không nhận được đồ ăn. Không có cơ chế tự động rollback nào của database cứu được lỗi này vì hai database nằm ở hai container hoàn toàn khác biệt. Hệ thống bị thất thoát tài chính và khách hàng sẽ khiếu nại.

 

Để giải quyết bài toán này, kỹ sư DevOps và Backend phải cùng phối hợp thiết kế một kịch bản giao tiếp Runtime hoàn chỉnh và cơ chế giao dịch bù bằng code (Compensating Transaction).

 

3. Kiến thức cốt lõi (Luồng chạy Runtime và Giao dịch bù - Sage Pattern)
3.1. Vòng đời 6 bước của một yêu cầu Đặt món (End-to-End Runtime Scenario)
Dưới đây là kịch bản tích hợp dòng dữ liệu chạy thực tế qua cụm container QuickBite khi khách hàng đặt món ăn:



Bước 1 (Tiếp nhận): Khách hàng chọn món và bấm nút Đặt đơn. Request đi tới cổng 8080 của API Gateway. Gateway kiểm tra định tuyến và chuyển tiếp request tới cổng 8083 của order-service.
Bước 2 (Tạo đơn tạm): order-service tạo một đơn hàng mới trong DB quickbite_order_db ở trạng thái tạm thời status = PENDING, đồng thời ghi nhận mốc thời gian vào lịch sử chuyển đổi trạng thái OrderStatusHistory.
Bước 3 (Thanh toán): order-service gửi HTTP Post gọi sang user-service (cổng 8081) để kiểm tra số dư ví (UserWallet).
Nếu ví đủ tiền: user-service trừ tiền trực tiếp trong DB quickbite_user và trả về kết quả thành công (true). order-service cập nhật đơn hàng thành ACCEPTED.
Nếu ví không đủ tiền: order-service cập nhật đơn hàng thành FAILED (Thất bại) và dừng luồng xử lý.
Bước 4 (Chuẩn bị món): order-service gọi sang restaurant-service (cổng 8082) để gửi thông báo cho nhà hàng chuẩn bị món ăn.
Trường hợp nhà hàng chấp nhận: Tiến hành đi tiếp sang Bước 5.
rường hợp nhà hàng từ chối (Hết món): Kích hoạt Giao dịch bù (Compensating Transaction): order-service gọi lại API hoàn tiền của user-service để cộng lại số tiền tương ứng vào UserWallet của khách hàng, cập nhật status đơn hàng thành CANCELLED.
Bước 5 (Giao hàng): order-service kích hoạt luồng điều phối tìm tài xế (quét các User có Role = DRIVER bên user-service). Khi tài xế bấm nhận đơn, gán driverId vào đơn hàng và cập nhật trạng thái đơn thành SHIPPING.
Bước 6 (Đẩy thông báo): Ở mỗi bước chuyển đổi trạng thái đơn hàng (PENDING -> ACCEPTED -> SHIPPING -> DELIVERED), order-service đều gửi một HTTP Post sang notification-service (cổng 8084). Dịch vụ này sẽ tạo lịch sử thông báo và giả lập gửi email/tin nhắn thông báo tới người dùng.
 

3.2. Khái niệm về Saga Pattern và Giao dịch bù (Compensating Transaction)
Vì không thể dùng @Transactional toàn cục, chúng ta áp dụng mô hình Saga Pattern dạng Orchestration (Điều phối):

● order-service đóng vai trò là Orchestrator (Bộ điều phối). Nó quản lý từng bước đi của đơn hàng.

● Nếu một bước ở giữa bị thất bại (ví dụ Bước 4 lỗi), Orchestrator có nhiệm vụ chủ động gửi các request ngược lại các dịch vụ trước đó để hoàn tác dữ liệu (gọi là giao dịch bù - ví dụ gọi API cộng lại tiền).

 

4. Demo thực tế: Theo dõi dòng log tổng hợp của hệ thống
Khi hệ thống đa container của QuickBite đang chạy, bạn có thể chạy lệnh sau để stream log đan xen của cả cụm trong thời gian thực:

docker compose logs -f

 

Khi bạn gửi request đặt món thành công thông qua Gateway, luồng log in ra màn hình terminal sẽ đan xen nhau theo trình tự nghiệp vụ như sau:

gateway-service-1        | [Gateway] Chuyển tiếp request POST /api/v1/orders tới order-service:8083

order-service-1          | [Order] Nhận yêu cầu tạo đơn từ Customer ID: 1, Restaurant ID: 2.

order-service-1          | [Order] Lưu đơn hàng ID: 99 thành công vào database (Trạng thái: PENDING).

order-service-1          | [Order] Đang gửi yêu cầu trừ tiền ví (100000đ) sang user-service...

user-service-1           | [User] Nhận yêu cầu trừ tiền ví cho User ID: 1. Số dư hiện tại: 150000đ.

user-service-1           | [User] Trừ tiền thành công. Số dư mới: 50000đ. Lưu DB thành công.

order-service-1          | [Order] Kết quả thanh toán: THÀNH CÔNG. Cập nhật trạng thái đơn thành ACCEPTED.

order-service-1          | [Order] Đang thông báo thực đơn đơn hàng sang restaurant-service...

restaurant-service-1     | [Restaurant] Nhận yêu cầu chuẩn bị đơn hàng ID: 99. Trạng thái nhà hàng: ĐANG MỞ CỬA.

restaurant-service-1     | [Restaurant] Merchant chấp nhận đơn hàng.

order-service-1          | [Order] Nhà hàng đã chấp nhận. Tiến hành tìm tài xế giao hàng...

order-service-1          | [Order] Đã gán tài xế ID: 5. Cập nhật trạng thái đơn thành: SHIPPING.

order-service-1          | [Order] Gửi sự kiện trạng thái đơn hàng sang notification-service...

notification-service-1   | [Notification] Nhận yêu cầu thông báo đơn hàng ID: 99. Tạo thông báo IN_APP cho User ID: 1.

notification-service-1   | [Notification] Gửi thông báo thành công (Trạng thái: SENT).

 

5. Hiểu lầm thường gặp (Giao dịch phân tán 2PC)
Hiểu lầm thường gặp: Có thể thiết lập giao dịch phân tán sử dụng giao thức Two-Phase Commit (2PC) hoặc cơ chế XA Transactions trong hệ thống Microservices để đảm bảo các database được rollback đồng thời mà không cần viết thêm code xử lý lỗi.
Sự thật:
Giao thức 2PC yêu cầu một bộ quản trị giao dịch (Transaction Coordinator) khóa (lock) tài nguyên trên tất cả các database liên quan trong suốt quá trình xác nhận.
Việc này làm giảm đáng kể hiệu năng của hệ thống (do các kết nối database bị giữ khóa rất lâu), dễ gây ra hiện tượng nghẽn mạng (Deadlock) và làm mất đi tính độc lập của các service.
Vì vậy, các hệ thống microservices hiện đại hầu hết đều từ bỏ 2PC để chuyển sang chấp nhận tính nhất quán cuối cùng (Eventual Consistency) thông qua Saga Pattern, chấp nhận viết code xử lý lỗi để hoàn tiền ví hoặc hủy đơn thủ công nhằm đổi lấy hiệu năng và tính ổn định cao cho hệ thống.
 

6. Tài liệu tham khảo
Kiến trúc Saga Pattern trong Microservices:

Saga Pattern - Microservices.io

So sánh tính nhất quán mạnh và nhất quán cuối cùng:

Eventual Consistency vs. Strong Consistency - AWS Guide

Quản lý log phân tán với Docker Compose:

View container logs - Docker Docs