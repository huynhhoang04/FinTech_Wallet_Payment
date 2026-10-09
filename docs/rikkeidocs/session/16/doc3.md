Bài đọc: Cấu hình Logback trong Spring Boot
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được vai trò và cơ chế hoạt động của Logback trong hệ sinh thái Spring Boot.
Đọc hiểu và phân tích các thành phần cốt lõi trong tệp cấu hình logback-spring.xml.
Cấu hình thành thạo Console Appender, Encoder và Root Logger cho các microservices trong QuickBite.
Điều chỉnh linh hoạt log level thông qua cấu hình và biến môi trường mà không cần thay đổi mã nguồn Java.
Kiểm tra và kiểm soát luồng log của ứng dụng khi triển khai thực tế bên trong Docker container.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Trong mã nguồn các service của QuickBite, lập trình viên thường xuyên sử dụng các câu lệnh log.info(), log.warn() hoặc log.error(). Tuy nhiên, các câu lệnh này chỉ chịu trách nhiệm sinh nội dung, chứ không tự quyết định thông điệp sẽ được ghi ra đâu (console hay file), theo định dạng nào (plain text hay JSON) và những mức độ nghiêm trọng nào được phép xuất hiện.

Nếu thiếu cấu hình chuẩn hóa, mỗi service sẽ hoạt động một cách tự phát:

Service này in log ra console, service khác tự ghi vào file trong container dẫn tới nguy cơ mất dữ liệu.
Service này bật mức DEBUG gây tràn đĩa, service khác chỉ ghi ERROR khiến việc truy vết trở nên bất khả thi.
Khi xảy ra sự cố trên cụm máy chủ VPS, sự thiếu đồng bộ này sẽ khiến việc tổng hợp và phân tích dữ liệu bị tê liệt. Logback đóng vai trò là engine trung tâm tiếp nhận sự kiện từ SLF4J để xử lý định dạng và điều hướng luồng xuất log:



PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Tệp cấu hình logback-spring.xml trong Spring Boot
Spring Boot sử dụng Logback làm logging framework mặc định thông qua starter spring-boot-starter-logging. Cấu hình logging được đặt trong tệp logback-spring.xml tại thư mục src/main/resources:

order-service/src/main/resources/logback-spring.xml
user-service/src/main/resources/logback-spring.xml
restaurant-service/src/main/resources/logback-spring.xml
notification-service/src/main/resources/logback-spring.xml

Nguyên tắc đặt tên: Luôn sử dụng tên logback-spring.xml thay vì logback.xml tiêu chuẩn. Tiền tố -spring cho phép Logback tận dụng đầy đủ các tính năng nâng cao của Spring như: đọc biến môi trường từ application.yml, hỗ trợ cấu hình theo Spring Profile (<springProfile>) và kế thừa các thiết lập mặc định của framework.

3.2 Các thành phần cốt lõi của cấu hình Logback
Cấu hình chuẩn của order-service được thiết lập như sau:

<configuration>
    <property name="service.name" value="order-service"/>
 
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="co.elastic.logging.logback.EcsEncoder">
            <serviceName>${service.name}</serviceName>
        </encoder>
    </appender>
 
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>

Bảng phân tích chi tiết các thẻ cấu hình:
Thẻ cấu hình

Chức năng kỹ thuật

Ý nghĩa trong QuickBite

<property>

Khai báo biến tái sử dụng trong file cấu hình.

Định nghĩa tên định danh dịch vụ (service.name).

<appender>

Xác định đích đến của dữ liệu log (Console, File, Socket).

ConsoleAppender chuyển hướng toàn bộ log ra luồng stdout/stderr.

<encoder>

Định dạng cấu trúc dữ liệu của từng dòng log.

Chuyển đổi log event thành định dạng JSON chuẩn ECS.

<root>

Logger gốc áp dụng cho toàn bộ ứng dụng.

Thiết lập mức chặn tối thiểu là INFO (bỏ qua TRACE, DEBUG).

<appender-ref>

Liên kết Root Logger với Appender cụ thể.

Gắn cấu hình Console Appender vào luồng xuất log chính.

Lưu ý: Mỗi service bắt buộc phải khai báo giá trị service.name duy nhất và chính xác để phục vụ việc phân biệt nguồn log khi chạy đồng thời nhiều container.

3.3 Phân biệt Root Logger và Package Logger
Root Logger là bộ lọc toàn cục. Khi Root Logger đặt ở mức INFO, toàn bộ các log DEBUG và TRACE từ mã nguồn ứng dụng lẫn các thư viện phụ thuộc (Spring, Hibernate, Tomcat) đều bị triệt tiêu.

Khi cần điều tra sự cố ở một module cụ thể mà không muốn làm ngập hệ thống bởi log của các thư viện bên thứ ba, ta có thể cấu hình Package Logger riêng biệt:

# Cấu hình trong application.yml
logging:
  level:
    root: INFO
    com.quickbite.order_service: DEBUG

Cấu hình này giữ cho toàn bộ hệ thống hoạt động ở mức INFO, nhưng riêng package com.quickbite.order_service sẽ xuất thêm log DEBUG để phục vụ chẩn đoán.

3.4 Quản lý Log Level linh hoạt bằng biến môi trường
Trên môi trường Production với Docker, việc thay đổi cấu hình không được phép làm gián đoạn hoặc yêu cầu build lại mã nguồn. Spring Boot hỗ trợ ánh xạ cấu hình từ biến môi trường:

logging:
  level:
    root: ${LOG_LEVEL_ROOT:INFO}
    com.quickbite.order_service: ${LOG_LEVEL_ORDER_SERVICE:INFO}

Khi triển khai với Docker Compose, ta có thể ghi đè log level trực tiếp:

services:
  quickbite-order:
    environment:
      LOG_LEVEL_ROOT: INFO
      LOG_LEVEL_ORDER_SERVICE: DEBUG

3.5 Nguyên tắc Console Logging trong Container
Trong kiến trúc container, ConsoleAppender đưa toàn bộ log ra stdout/stderr. Docker Engine sẽ tự động thu gom các luồng này và phân phối tới các công cụ điều tra qua lệnh docker compose logs.

Việc cấu hình ghi file trực tiếp bên trong container là không cần thiết, làm tăng chi phí quản lý volume, phân quyền ghi đĩa và tiềm ẩn rủi ro mất dữ liệu khi container bị tái tạo.

PHẦN 4. DEMO VÀ THỰC HÀNH (CẤU HÌNH VÀ KIỂM TRA LOGBACK)
Phần thực hành tập trung vào việc đối chiếu cấu hình Logback giữa các service QuickBite và kiểm chứng cơ chế điều khiển log level trên môi trường Docker.

4.1 Rà soát cấu hình Logback của OrderService
Kiểm tra cấu trúc tệp logback-spring.xml của order-service để đảm bảo tuân thủ tiêu chuẩn:

<configuration>
    <!-- 1. Tên service định danh -->
    <property name="service.name" value="order-service"/>
 
    <!-- 2. Console Appender với ECS JSON Encoder -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="co.elastic.logging.logback.EcsEncoder">
            <serviceName>${service.name}</serviceName>
        </encoder>
    </appender>
 
    <!-- 3. Mức log mặc định của ứng dụng -->
    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>

Tiêu chí đánh giá cấu hình:

Đã khai báo dependency co.elastic.logging:logback-ecs-encoder trong build.gradle.
Sử dụng ConsoleAppender chuẩn thay vì FileAppender.
Thuộc tính service.name phản ánh chính xác tên microservice.
Mức root logger được khóa ở INFO để bảo đảm an toàn hiệu năng.
4.2 Tính nhất quán về service.name giữa các Microservices
Mỗi service trong hệ thống QuickBite cần có tệp cấu hình độc lập với tên dịch vụ tương ứng:

Service

Đường dẫn cấu hình

Giá trị service.name bắt buộc

order-service

order-service/src/main/resources/logback-spring.xml

order-service

restaurant-service

restaurant-service/src/main/resources/logback-spring.xml

restaurant-service

user-service

user-service/src/main/resources/logback-spring.xml

user-service

notification-service

notification-service/src/main/resources/logback-spring.xml

notification-service

Cảnh báo sai lầm: Sao chép file cấu hình giữa các dự án mà quên đổi giá trị service.name sẽ dẫn đến việc toàn bộ log bị gán nhầm nguồn gốc, gây sai lệch hoàn toàn dữ liệu điều tra trên Production.

4.3 Quan sát output log từ Console Appender
Khởi chạy cụm dịch vụ QuickBite và kiểm tra luồng log được xuất ra từ container:

cd ~/quickbite
docker compose ps
docker compose logs --tail 30 quickbite-order

Nếu container đang chạy và xuất hiện các dòng log, điều này chứng minh ConsoleAppender đã ghi dữ liệu thành công ra stdout và được Docker tiếp nhận.

Nếu không quan sát được log, tiến hành kiểm tra theo các bước:

Container có đang trong trạng thái running không?
Tệp cấu hình có đặt đúng tên logback-spring.xml trong thư mục src/main/resources không?
Cấu hình root logger có vô tình bị đặt lên mức ERROR khiến các log INFO bị lọc bỏ không?
4.4 Thực nghiệm điều chỉnh mức log DEBUG có kiểm soát
Khi cần điều tra chuyên sâu, cấu hình bổ sung biến môi trường trong docker-compose.yml để nâng mức log cho riêng order-service:

services:
  quickbite-order:
    environment:
      - LOG_LEVEL_ROOT=INFO
      - LOG_LEVEL_ORDER_SERVICE=DEBUG

Khởi động lại service và kiểm tra log:

docker compose up -d quickbite-order
docker compose logs -f quickbite-order

Sau khi hoàn tất việc chẩn đoán lỗi, bắt buộc phải đưa cấu hình về mức INFO để tránh lãng phí tài nguyên lưu trữ của máy chủ.

PHẦN 5. TỔNG KẾT
Vai trò của Logback: Logback là động cơ thực thi nhận log từ SLF4J, áp dụng quy tắc lọc log level, định dạng dữ liệu và điều hướng xuất log ra đích đến.
Cấu hình chuẩn trong Container: Sử dụng logback-spring.xml, xuất log qua ConsoleAppender ra stdout và đặt root logger ở mức INFO.
Định danh nguồn gốc: Thuộc tính service.name là thông tin bắt buộc để phân biệt nguồn phát log giữa các microservices.
Tối ưu hóa chẩn đoán: Sử dụng package logger và biến môi trường để bật mức DEBUG cục bộ khi cần thiết mà không phải sửa mã nguồn hay làm ảnh hưởng tới toàn bộ hệ thống.