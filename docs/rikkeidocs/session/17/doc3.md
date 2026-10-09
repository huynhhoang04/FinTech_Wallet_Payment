Bài đọc: Gửi log từ Spring Boot đến Fluentd
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Cấu hình được Docker Logging Driver fluentd cho từng microservice trong hệ thống QuickBite.
Giải thích được cấu trúc gói tin do Docker Driver gửi tới Fluentd và cơ chế giải mã trường log chứa chuỗi JSON gốc.
Thiết lập hệ thống định tuyến nhãn (Tag) chuẩn hóa để phân luồng và nhận diện log của từng service.
Kiểm tra và xác nhận luồng dữ liệu log đi từ ứng dụng Spring Boot qua Fluentd và được ghi vào Elasticsearch index.
Nhận diện và xử lý các lỗi phổ biến khi cấu hình kết nối giữa Docker Daemon và Fluentd Collector.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Ứng dụng Spring Boot trong QuickBite đã được cấu hình ghi JSON log chuẩn ECS ra luồng stdout. Nếu chỉ sử dụng logging driver mặc định của Docker (json-file), các dòng log này chỉ được ghi vào tệp đệm trên máy chủ host để phục vụ lệnh docker compose logs. Fluentd Collector hoàn toàn không tự động đọc được dữ liệu này nếu không có cơ chế chuyển giao trực tiếp.

Docker cung cấp sẵn logging driver chuyên dụng fluentd. Khi kích hoạt driver này, Docker Daemon sẽ tự động đóng gói mỗi sự kiện log xuất hiện tại stdout/stderr thành một thông điệp và chuyển tiếp tới Fluentd qua giao thức forward:

{
  "container_id": "9a8b7c6d5e...",
  "container_name": "/quickbite-order",
  "source": "stdout",
  "tag": "quickbite.order",
  "log": "{\"@timestamp\":\"2026-08-14T10:15:22.456Z\",\"service.name\":\"order-service\",\"message\":\"Payment succeeded for Order ID 125\"}"
}

Vấn đề kỹ thuật đặt ra: Toàn bộ JSON log của Spring Boot đang bị bọc bên trong trường log dưới dạng một chuỗi văn bản (String). Do đó, Fluentd cần một bộ lọc phân tích (Parser Filter) để trích xuất chuỗi này thành các trường dữ liệu độc lập (service.name, log.level, trace.id), giúp Elasticsearch có thể lập chỉ mục và tìm kiếm chi tiết.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Cấu hình Docker Logging Driver fluentd trong Docker Compose
Trong tệp docker-compose.yml, khối logging được định nghĩa ở cấp độ từng service:

logging:
  driver: fluentd
  options:
    fluentd-address: "127.0.0.1:24224"
    fluentd-async: "true"
    tag: "quickbite.order"

Phân tích chi tiết các tham số tùy chọn:
Tùy chọn (Option)

Giá trị cấu hình

Ý nghĩa và Vai trò vận hành

fluentd-address

127.0.0.1:24224

Địa chỉ IP loopback và cổng dịch vụ của Fluentd đã được publish trên máy chủ host.

fluentd-async

"true"

Chế độ gửi log bất đồng bộ. Cho phép container ứng dụng khởi động bình thường ngay cả khi Fluentd tạm thời chưa sẵn sàng; Docker daemon sẽ tự động đệm log trong bộ nhớ và kết nối lại trong nền.

tag

quickbite.<service_name>

Nhãn định danh nguồn phát log, được Fluentd sử dụng làm căn cứ định tuyến và lọc dữ liệu.

Lưu ý vận hành: Tùy chọn fluentd-async: "true" giúp hệ thống không bị nghẽn (non-blocking) khi khởi động, nhưng không thay thế cho việc duy trì sự ổn định của Fluentd. Nếu Fluentd bị sập quá lâu, bộ nhớ đệm của Docker daemon sẽ bị đầy và dẫn tới nguy cơ thất thoát dữ liệu log.

3.2 Chuẩn hóa hệ thống Tag cho các Microservices QuickBite
Để Fluentd có thể tự động gom cụm và xử lý theo mẫu <filter quickbite.**>, các service cần tuân thủ quy ước đặt tag thống nhất:

Microservice

Tên Container chuẩn

Tag cấu hình trong Compose

order-service

quickbite-order

quickbite.order

restaurant-service

quickbite-restaurant

quickbite.restaurant

user-service

quickbite-user

quickbite.user

notification-service

quickbite-notification

quickbite.notification

3.3 Cơ chế bóc tách JSON bằng Parser Filter trong Fluentd
Để chuyển đổi chuỗi JSON thô trong trường log thành các trường dữ liệu có cấu trúc, Fluentd sử dụng cấu hình parser:

<filter quickbite.**>
  @type parser
  key_name log
  reserve_data true
  <parse>
    @type json
  </parse>
</filter>

key_name log: Chỉ định trường dữ liệu chứa chuỗi JSON cần giải mã.
reserve_data true: Tham số tối quan trọng. Giữ nguyên toàn bộ các metadata do Docker bổ sung (container_name, container_id, source) và hợp nhất cùng với các trường ECS được giải mã từ Spring Boot (@timestamp, service.name, trace.id).
3.4 Tính độc lập tuyệt đối của ứng dụng Spring Boot
Mô hình chuyển tiếp log qua Docker Logging Driver mang lại lợi thế kiến trúc vượt trội:

Ứng dụng Spring Boot hoàn toàn không cần thêm bất kỳ thư viện Fluentd SDK nào trong build.gradle.
Ứng dụng không cần biết địa chỉ IP, cổng hay cấu hình của Elasticsearch.
Mã nguồn nghiệp vụ chỉ tương tác với chuẩn SLF4J; việc thu gom, chuyển đổi và lưu trữ được ủy quyền hoàn toàn cho lớp hạ tầng container.
PHẦN 4. DEMO VÀ THỰC HÀNH (KẾT NỐI QUICKBITE VÀO EFK)
Phần thực hành hướng dẫn cấu hình tích hợp logging driver cho cụm microservices QuickBite và kiểm tra luồng dữ liệu log trên Elasticsearch.

4.1 Kiểm tra trạng thái sẵn sàng của Fluentd Collector
Trước khi áp dụng logging driver cho các microservices, Fluentd phải đang chạy và lắng nghe kết nối tại cổng 24224 trên host:

cd ~/quickbite/efk
docker compose ps
docker compose logs --tail 30 fluentd

Xác nhận: Container quickbite-fluentd ở trạng thái running và đã tải thành công cấu hình forward input.

4.2 Cập nhật cấu hình Logging Driver trong docker-compose.yml
Mở tệp ~/quickbite/docker-compose.yml và bổ sung khối logging cho từng microservice:

# 1. Cấu hình cho quickbite-order
quickbite-order:
  # ... (giữ nguyên các cấu hình build, ports, environment hiện có)
  logging:
    driver: fluentd
    options:
      fluentd-address: "127.0.0.1:24224"
      fluentd-async: "true"
      tag: "quickbite.order"
 
# 2. Cấu hình cho quickbite-restaurant
quickbite-restaurant:
  logging:
    driver: fluentd
    options:
      fluentd-address: "127.0.0.1:24224"
      fluentd-async: "true"
      tag: "quickbite.restaurant"
 
# 3. Cấu hình cho quickbite-user
quickbite-user:
  logging:
    driver: fluentd
    options:
      fluentd-address: "127.0.0.1:24224"
      fluentd-async: "true"
      tag: "quickbite.user"
 
# 4. Cấu hình cho quickbite-notification
quickbite-notification:
  logging:
    driver: fluentd
    options:
      fluentd-address: "127.0.0.1:24224"
      fluentd-async: "true"
      tag: "quickbite.notification"

4.3 Tái tạo Container để áp dụng Logging Driver mới
Logging driver là cấu hình tĩnh gắn liền với quá trình khởi tạo container. Do đó, cần sử dụng cờ --force-recreate để Docker tạo lại các container với driver mới:

cd ~/quickbite
docker compose up -d --force-recreate
docker compose ps

4.4 Kiểm tra luồng dữ liệu tới Fluentd và Elasticsearch Index
Mở cửa sổ theo dõi log của Fluentd trong thời gian thực:
cd ~/quickbite/efk
docker compose logs -f fluentd

Gửi một request nghiệp vụ bất kỳ tới QuickBite API hoặc khởi động lại một service để phát sinh log.

 
Kiểm tra chỉ mục (Index) mới tạo trong Elasticsearch:

curl http://127.0.0.1:9200/_cat/indices?v

Kết quả mong đợi: Xuất hiện index theo ngày dạng quickbite-YYYY.MM.DD với số lượng tài liệu (docs.count) tăng dần theo số lượng log phát sinh:

health status index uuid pri rep docs.count docs.deleted store.size
yellow open quickbite-2026.08.14 8xK9LmPqR1s2TuVwXyZ34A 1 1 142 0 125.4kb

4.5 Bảng chẩn đoán và khắc phục sự cố kết nối
Hiện tượng lỗi

Nguyên nhân gốc rễ

Hướng xử lý

Container ứng dụng không thể khởi động

Fluentd chưa chạy hoặc địa chỉ fluentd-address bị sai/chưa mở cổng 24224.

Kiểm tra docker compose ps tại thư mục efk/, đảm bảo Fluentd đang chạy và cổng 127.0.0.1:24224 đã được publish.

Fluentd nhận log nhưng Elasticsearch không có index

Cấu hình <match> trong fluent.conf sai địa chỉ hoặc Elasticsearch bị lỗi bộ nhớ.

Xem log của Fluentd qua docker compose logs fluentd và kiểm tra sức khỏe cụm qua curl 127.0.0.1:9200/_cat/health?v.

Log trong Elasticsearch bị hiển thị dạng text thô trong trường log

Parser JSON của Fluentd chưa hoạt động hoặc tag không khớp với pattern <filter quickbite.**>.

Kiểm tra lại cú pháp key_name log và đảm bảo tag bắt đầu bằng tiền tố quickbite..

PHẦN 5. TỔNG KẾT
Cơ chế hoạt động: Docker Logging Driver fluentd tự động bắt luồng stdout/stderr và chuyển tiếp trực tiếp sang Fluentd Collector mà không yêu cầu thay đổi mã nguồn Java.
Định tuyến nhãn (Tagging): Khai báo tag theo tiền tố quickbite.<service> giúp phân luồng và xử lý đồng bộ toàn bộ microservices.
Giải mã dữ liệu: Parser Filter của Fluentd với cấu hình reserve_data true giúp hợp nhất hoàn hảo giữa metadata hạ tầng container và nội dung log nghiệp vụ ECS.
Tạo chỉ mục tự động: Cấu hình Elasticsearch output của Fluentd tự động gom nhóm log vào các index theo ngày (quickbite-YYYY.MM.DD).