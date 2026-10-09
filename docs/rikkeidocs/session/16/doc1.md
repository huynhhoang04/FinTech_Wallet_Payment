Bài đọc: Khác biệt logging giữa development và production
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Phân biệt được mục đích và cách sử dụng log trong môi trường Development so với môi trường Production.
Giải thích được vì sao việc xem log trực tiếp trên máy cá nhân không đủ để vận hành hệ thống QuickBite trên VPS.
Sử dụng được các lệnh Docker cơ bản để xem, lọc và theo dõi log của một Spring Boot service đang chạy trong container.
Nhận diện được các thông tin không nên ghi vào log production vì lý do bảo mật và quyền riêng tư.
Mô tả được vai trò của log ứng dụng trong chuỗi vận hành: Spring Boot service → Docker container → Hệ thống thu thập log tập trung.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Ở môi trường Development, lập trình viên thường chạy một Spring Boot service trực tiếp trên máy cá nhân bằng IDE hoặc Gradle và theo dõi log ngay trong terminal. Cách làm này trực quan, phản hồi tức thì và phù hợp khi kiểm tra một service đơn lẻ tại một thời điểm.

Khi hệ thống QuickBite được triển khai trên VPS Production, các service hoạt động trong các container Docker độc lập. Một request đặt món từ người dùng thường đi qua nhiều thành phần phân tán:



Khi quy trình đặt món thất bại, đội ngũ vận hành cần xác định chính xác:

Request đã đi tới service nào và bị gián đoạn ở đâu?
Container nào trả về mã lỗi hoặc ngoại lệ (exception)?
Lỗi xảy ra ở bước nào trong toàn bộ chuỗi xử lý nghiệp vụ?
Service có đang khởi động lại (restart loop) hoặc bị mất kết nối cơ sở dữ liệu hay không?
Terminal trên máy Development hoàn toàn không thể phản ánh những gì đang diễn ra trên VPS. Do đó, logging ở Production phải được chuẩn hóa để hỗ trợ quan sát và điều tra hoạt động của toàn bộ cụm dịch vụ một cách nhất quán.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Logging trong môi trường Development
Môi trường Development phục vụ chủ yếu cho việc viết mã nguồn, kiểm tra nhanh và chẩn đoán lỗi trong quá trình phát triển. Đặc điểm chính bao gồm:

Service được khởi chạy trực tiếp từ IDE hoặc thông qua lệnh build tool (./gradlew bootRun).
Lập trình viên theo dõi luồng log của từng service riêng biệt trong terminal cục bộ.
Mức độ chi tiết có thể mở rộng tối đa (bật DEBUG, TRACE, log chi tiết câu lệnh SQL, request payload hoặc biến tạm thời).
Định dạng log ưu tiên tính trực quan, dễ đọc nhanh bằng mắt thường (human-readable, text có màu).
Dữ liệu xử lý thường là dữ liệu giả lập (mock data) trong phạm vi truy cập nội bộ có kiểm soát.
Ví dụ log dạng văn bản khi chạy order-service tại local:

2026-08-13 10:15:21 INFO OrderService - Order creation started: customerId=101, restaurantId=10
2026-08-13 10:15:22 INFO RestaurantService - Restaurant availability checked: restaurantId=10, open=true
2026-08-13 10:15:22 INFO OrderService - Order created successfully: orderId=500

Hạn chế: Định dạng text đơn giản trên rất thuận tiện khi đọc ít dòng tại local. Tuy nhiên, khi hệ thống sinh ra hàng triệu dòng log từ hàng chục container, con người không thể đọc thủ công và các công cụ thu thập tự động cũng rất khó phân tích (parse) các trường dữ liệu nếu không có cấu trúc chuẩn.

3.2 Logging trong môi trường Production
Môi trường Production phục vụ người dùng thực tế và đòi hỏi hệ thống duy trì tính ổn định liên tục. Logging trên Production phải đồng thời đáp ứng ba mục tiêu vận hành cốt lõi:

Vận hành (Operations): Nắm bắt trạng thái sống/chết (health) và mức độ sẵn sàng của từng service.
Điều tra sự cố (Troubleshooting): Nhanh chóng khoanh vùng nguyên nhân gốc rễ (root cause) khi request bị lỗi hoặc có độ trễ cao.
Kiểm toán và Phân tích (Audit & Analytics): Ghi nhận các mốc sự kiện nghiệp vụ quan trọng mà không làm rò rỉ dữ liệu nhạy cảm.
Bảng so sánh chi tiết giữa Development và Production:
Tiêu chí

Môi trường Development

Môi trường Production

Mục đích

Debug mã nguồn và kiểm tra tính năng mới

Vận hành hệ thống, điều tra sự cố và giám sát dịch vụ

Môi trường thực thi

IDE, terminal hoặc máy cá nhân

VPS, Docker container hoặc cụm máy chủ

Phạm vi quan sát

Thường tập trung vào 1 service đơn lẻ

Toàn bộ cụm microservices và hạ tầng liên quan

Định dạng dữ liệu

Text tự do, ưu tiên mắt người đọc

Cấu trúc ổn định (JSON), dễ phân tích và tìm kiếm tự động

Mức độ chi tiết

Có thể bật DEBUG / TRACE / hiển thị SQL

Chỉ giữ mức cần thiết (INFO, WARN, ERROR) để tối ưu hiệu năng

Tính chất dữ liệu

Dữ liệu kiểm thử giả lập

Dữ liệu người dùng thực -> Phải tuân thủ bảo mật nghiêm ngặt

Lưu trữ và vòng đời

Tồn tại tạm thời trong phiên làm việc

Có chính sách lưu trữ tập trung, luân chuyển và dọn dẹp (rotation)

Lưu ý quan trọng: Tuyệt đối không áp dụng nguyên vẹn cấu hình logging từ Development lên Production. Việc ghi log quá chi tiết (toàn bộ câu lệnh SQL, request/response body) sẽ làm tăng dung lượng lưu trữ đột biến, gây nghẽn I/O hệ thống và có nguy cơ để lộ thông tin nhạy cảm.

3.3 Cơ chế ghi log của Container ra Standard Output (stdout / stderr)
Trong kiến trúc container hóa với Docker, ứng dụng được chuẩn hóa để ghi luồng log thông tin ra stdout và luồng log lỗi ra stderr. Docker Engine sẽ thu thập trực tiếp các luồng này và cung cấp giao diện quản lý thông qua lệnh:

docker logs <container-name>

Mô hình này thiết lập ranh giới trách nhiệm rõ ràng:

Spring Boot Service: Chỉ chịu trách nhiệm sinh ra nội dung log ra console chuẩn.
Docker Engine: Chịu trách nhiệm bắt luồng stdout/stderr, gán nhãn container và lưu trữ tạm thời qua Logging Driver.
Hệ thống Log tập trung: Đảm nhận việc thu gom, đánh chỉ mục (indexing), lưu trữ dài hạn và hỗ trợ truy vấn.
3.4 Quy định bảo mật: Không ghi thông tin nhạy cảm vào log
Log trên Production có thể được phân quyền cho nhiều bộ phận truy cập hoặc chuyển tiếp qua các hệ thống giám sát. Vì vậy, tuyệt đối không được ghi trực tiếp các dữ liệu sau:

Mật khẩu người dùng, mật khẩu kết nối cơ sở dữ liệu, API key.
JWT access token, refresh token, secret key hoặc nội dung header Authorization.
Dữ liệu định danh cá nhân (PII) không bắt buộc cho quá trình xử lý sự cố.
Toàn bộ request body khi chứa dữ liệu giao dịch hoặc thông tin riêng tư.
Khi cần định danh đối tượng để phục vụ điều tra, hãy sử dụng ID nghiệp vụ (như userId, orderId) hoặc sử dụng kỹ thuật che giấu dữ liệu (masking).

Ví dụ không an toàn (Vi phạm bảo mật):

Login request: username=nam, password=Rikkei@123, token=eyJhbGciOi...

Ví dụ an toàn (Được khuyến nghị):

Login failed: userId=USR-1024, reason=INVALID_CREDENTIALS

PHẦN 4. DEMO VÀ THỰC HÀNH (SO SÁNH LOG LOCAL VÀ LOG DOCKER)
Phần thực hành này sử dụng cụm dịch vụ QuickBite đã được triển khai trên máy chủ. Học viên không cần tạo thêm logging service riêng.

4.1 Kiểm tra các service QuickBite trên Ubuntu Server
Di chuyển tới thư mục mã nguồn QuickBite trên server và kiểm tra trạng thái các container:

cd ~/quickbite
docker compose ps

Kết quả hiển thị cho thấy các service đã sẵn sàng hoạt động với trạng thái Up hoặc running:

NAME SERVICE STATUS
quickbite-quickbite-order-1 quickbite-order running
quickbite-quickbite-rest... quickbite-restaurant running
quickbite-quickbite-user-1 quickbite-user running

Lưu ý: Tên container trong cột NAME có thể thay đổi tùy thuộc vào phiên bản Docker Compose và tên thư mục gốc. Khi thực hiện các lệnh tiếp theo, học viên cần sử dụng đúng tên service hoặc tên container tương ứng trên hệ thống của mình.

4.2 Xem và theo dõi log của một service
Xem toàn bộ log đã sinh ra từ trước đến nay của order-service:

docker compose logs quickbite-order

Theo dõi log mới phát sinh theo thời gian thực (live streaming):

docker compose logs -f quickbite-order

Thao tác: Mở một cửa sổ terminal khác và gửi một request tới API của QuickBite. Quan sát các dòng log mới xuất hiện đồng thời trong cửa sổ đang chạy docker compose logs -f.

Nhấn tổ hợp phím Ctrl + C để dừng việc theo dõi log. Thao tác này chỉ ngắt lệnh hiển thị log, hoàn toàn không làm dừng hoặc ảnh hưởng đến tiến trình container đang chạy.

4.3 Giới hạn và lọc khoảng thời gian log cần xem
Khi hệ thống đã chạy lâu, lượng log tích lũy rất lớn. Việc đọc toàn bộ file log sẽ làm chậm quá trình điều tra. Hãy sử dụng các tùy chọn lọc:

Xem log phát sinh trong khoảng thời gian gần nhất (ví dụ: 10 phút qua):

docker compose logs --since 10m quickbite-order

Bổ sung mốc thời gian (timestamp) chi tiết cho từng dòng log:

docker compose logs -t --since 10m quickbite-order

Chỉ lấy một số lượng dòng log cuối cùng (ví dụ: 50 dòng mới nhất):

docker compose logs --tail 50 quickbite-order

Các tham số trên giúp kỹ sư vận hành nhanh chóng khoanh vùng thời điểm xảy ra sự cố thay vì phải cuộn qua hàng nghìn dòng thông tin không liên quan.

4.4 Xem log của toàn bộ cụm service
Để có cái nhìn tổng quan về hoạt động của toàn bộ hệ thống Compose:

docker compose logs --tail 50

Theo dõi trực tiếp log của tất cả các container cùng lúc:

docker compose logs -f

Trường hợp chỉ cần theo dõi các service liên quan trực tiếp đến một luồng nghiệp vụ (ví dụ: tạo đơn hàng giữa order-service và restaurant-service):

docker compose logs -f quickbite-order quickbite-restaurant

Để kiểm tra chính xác danh sách tên các service được định nghĩa trong docker-compose.yml, sử dụng lệnh:

docker compose config --services

4.5 So sánh cách xem log giữa Development và Production
Trong Development: Ứng dụng thường được chạy trực tiếp:

./gradlew bootRun

Toàn bộ log được in ra terminal của tiến trình Spring Boot đang chạy cục bộ.

Trong Production (Docker): Ứng dụng vẫn sinh log ra console nhưng việc truy xuất được ủy quyền thông qua Docker Engine:

docker compose logs -f quickbite-order

Docker không can thiệp vào logic nghiệp vụ của ứng dụng mà đóng vai trò làm cầu nối tiếp nhận và cung cấp cơ chế truy xuất dữ liệu log từ container một cách an toàn.

4.6 Kiểm tra tình huống lỗi thực tế và kiểm soát an toàn thông tin
Học viên thực hiện gửi một request lỗi (ví dụ: dữ liệu không hợp lệ hoặc sai thông tin xác thực) tới API QuickBite, sau đó tiến hành kiểm tra log:

docker compose logs --since 5m quickbite-order

Khi phân tích kết quả log thu được, cần đối chiếu các tiêu chí sau:

Có xác định được chính xác service nào đã tiếp nhận và xử lý request không?
Thông báo lỗi hoặc stacktrace có cung cấp đủ nguyên nhân để khắc phục sự cố không?
Log có vô tình để lộ mật khẩu, token xác thực hoặc thông tin bảo mật của cơ sở dữ liệu không?
Có trường dữ liệu cá nhân nào của người dùng bị ghi thừa vào log không?
Dòng log có giúp phân biệt rõ ràng giữa lỗi nghiệp vụ (như nhập sai dữ liệu - 4xx) và lỗi hệ thống (như sập kết nối - 5xx) không?
PHẦN 5. TỔNG KẾT
Trong môi trường Development, log chủ yếu phục vụ nhu cầu quan sát nhanh của lập trình viên trên một service đơn lẻ. Khi đưa lên Production, log trở thành dữ liệu vận hành sống còn của toàn bộ hệ thống, đòi hỏi phải được chuẩn hóa để phục vụ việc giám sát, điều tra lỗi và phân tích tự động.

Các nguyên tắc trọng tâm cần ghi nhớ:

Không dùng tư duy và cấu hình xem log cục bộ của môi trường Development để áp dụng cho Production.
Đối với ứng dụng chạy trong container, luôn cấu hình ghi log ra stdout và stderr để Docker Engine tiếp nhận.
Sử dụng linh hoạt các lệnh docker compose logs với các cờ --tail, --since, -f, -t để nâng cao hiệu quả điều tra sự cố.
Tuyệt đối tuân thủ quy tắc an toàn thông tin: không ghi mật khẩu, token, khóa bí mật hoặc dữ liệu cá nhân nhạy cảm vào log.
Chuẩn hóa định dạng log để sẵn sàng tích hợp vào các hệ thống quản lý log tập trung trong các giai đoạn tiếp theo.
