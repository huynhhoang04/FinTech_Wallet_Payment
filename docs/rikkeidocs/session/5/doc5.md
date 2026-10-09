Bài đọc: Spring Cloud Gateway và định tuyến yêu cầu trong kiến trúc đa dịch vụ
 

 1. Mục tiêu bài học
Sau khi hoàn thành bài học này, bạn sẽ có khả năng:

Biên soạn chính xác tệp cấu hình YAML định nghĩa định tuyến của Spring Cloud Gateway.
Phân biệt và cấu hình thành công các thành phần cốt lõi của một Route: ID, URI, Predicates (điều kiện lọc) và Filters (bộ lọc thay đổi request/response).
Tích hợp container dịch vụ API Gateway vào hệ thống docker-compose.yml chung.
● Thực hiện đóng kín cổng dịch vụ của các microservices nội bộ và mở duy nhất cổng gateway để bảo mật hệ thống.
 
 

2. Vấn đề thực tế (Nỗi đau của việc định tuyến thủ công bằng code)
Khi quyết định đặt một API Gateway làm cửa ngõ cho 4 dịch vụ QuickBite, nếu chúng ta tự viết một ứng dụng Spring Boot thông thường sử dụng RestTemplate trong Controller để nhận và chuyển tiếp thủ công từng API:

Nỗi đau viết mã lặp (Boilerplate code): Bạn phải viết hàng tá các class Controller chỉ để nhận request rồi gửi tiếp đi. Khi một microservice phía sau thêm mới 10 API, bạn lại phải sửa code của Gateway để bổ sung các Endpoint tương ứng và deploy lại.
Nỗi đau thay đổi đường dẫn (Path rewriting): Giả sử Client gọi tới Gateway bằng đường dẫn /api/v1/orders/123 nhưng dịch vụ nội bộ order-service lại được code để nhận đường dẫn dạng /orders/123 (bỏ tiền tố /api/v1). Việc xử lý cắt chuỗi URL thủ công bằng code Java cho hàng trăm API sẽ dẫn đến rủi ro sai cú pháp và cực kỳ khó quản lý.
 

Spring Cloud Gateway ra đời để giải quyết nỗi đau này bằng cách cung cấp một bộ khung cấu hình Declarative (khai báo YAML) cực kỳ mạnh mẽ, giúp cấu hình toàn bộ các tuyến định tuyến và xử lý URL chỉ trong vài dòng cấu hình mà không cần viết một dòng code Java nào.

 

3. Kiến thức cốt lõi (Các thành phần của Route trong Spring Cloud Gateway)
Mô hình định tuyến của Spring Cloud Gateway hoạt động dựa trên ba thành phần nền tảng:



 

Route (Tuyến đường): Là đối tượng định tuyến cơ bản của gateway. Nó bao gồm một ID duy nhất, một URI đích của dịch vụ phía sau, một tập hợp các điều kiện lọc (Predicates) và các bộ lọc (Filters).
Predicate (Điều kiện khớp): Là một hàm điều kiện logic giúp Gateway xác định xem request hiện tại có khớp với Route này hay không.
Ví dụ: - Path=/api/v1/users/** có nghĩa là "nếu đường dẫn request bắt đầu bằng /api/v1/users/ thì sẽ áp dụng Route này".
Filter (Bộ lọc xử lý): Là các bộ tiền xử lý (pre-filter) và hậu xử lý (post-filter) cho phép sửa đổi request hoặc response trước và sau khi gửi đến dịch vụ đích (ví dụ: StripPrefix=2 hoặc AddRequestHeader).
 

4. Demo thực tế: Cấu hình Spring Cloud Gateway và tích hợp Docker Compose 
4.1. Biên soạn tệp cấu hình application.yml cho Gateway
Dưới đây là cấu hình hoàn chỉnh của dịch vụ Gateway (gateway-service), định tuyến các request dựa trên path pattern đến 4 container Spring Boot nội bộ của QuickBite:

server:

  port: 8080

 

spring:

  cloud:

    gateway:

      routes:

        # 1. Tuyến đường định hướng tới user-service

        - id: user_route

          uri: http://${USER_SVC_HOST:localhost}:${USER_SVC_PORT:8081}

          predicates:

            - Path=/api/v1/users/**

 

        # 2. Tuyến đường định hướng tới restaurant-service

        - id: restaurant_route

          uri: http://${RESTAURANT_SVC_HOST:localhost}:${RESTAURANT_SVC_PORT:8082}

          predicates:

            - Path=/api/v1/restaurants/**

 

        # 3. Tuyến đường định hướng tới order-service

        - id: order_route

          uri: http://${ORDER_SVC_HOST:localhost}:${ORDER_SVC_PORT:8083}

          predicates:

            - Path=/api/v1/orders/**

 

        # 4. Tuyến đường định hướng tới notification-service

        - id: notification_route

          uri: http://${NOTIFICATION_SVC_HOST:localhost}:${NOTIFICATION_SVC_PORT:8084}

          predicates:

            - Path=/api/v1/notifications/**

 

 

Giải thích cấu hình:

● Các biến ${USER_SVC_HOST:localhost} giúp linh hoạt trỏ về localhost khi dev ở máy cá nhân hoặc trỏ về tên container khi chạy trong Docker.

● Ký tự ** đại diện cho mọi đường dẫn con ở phía sau (Wildcard).

 

4.2. Cấu hình Tích hợp trong docker-compose.yml và .env
Chúng ta kế thừa và phát triển file .env cùng file docker-compose.yml từ Lesson 2. Lúc này, chúng ta định nghĩa đầy đủ cả 4 dịch vụ backend cùng dịch vụ Gateway, kết nối chéo qua mạng ngoài quickbite-net tới container database quickbite-db đang chạy ngầm.

 

1. Tệp cấu hình /quickbite-project/.env:

# Database Common Configuration

DB_HOST=quickbite-db

DB_PORT=5432

 

# User Service Settings

USER_DB_NAME=quickbite_user_db

USER_DB_USERNAME=quickbite_user

USER_DB_PASSWORD=quickbite_user

USER_SERVER_PORT=8081

 

# Restaurant Service Settings

RESTAURANT_DB_NAME=quickbite_restaurant_db

RESTAURANT_DB_USERNAME=quickbite_restaurant

RESTAURANT_DB_PASSWORD=quickbite_restaurant

RESTAURANT_SERVER_PORT=8082

 

# Order Service Settings

ORDER_DB_NAME=quickbite_order_db

ORDER_DB_USERNAME=quickbite_order

ORDER_DB_PASSWORD=quickbite_order

ORDER_SERVER_PORT=8083

 

# Notification Service Settings

NOTIFICATION_DB_NAME=quickbite_notification_db

NOTIFICATION_DB_USERNAME=quickbite_notification

NOTIFICATION_DB_PASSWORD=quickbite_notification

NOTIFICATION_SERVER_PORT=8084

 

# Gateway Settings

GATEWAY_SERVER_PORT=8080

USER_SVC_HOST=user-service

USER_SVC_PORT=8081

RESTAURANT_SVC_HOST=restaurant-service

RESTAURANT_SVC_PORT=8082

ORDER_SVC_HOST=order-service

ORDER_SVC_PORT=8083

NOTIFICATION_SVC_HOST=notification-service

NOTIFICATION_SVC_PORT=8084

 

2. Tệp cấu hình /quickbite-project/docker-compose.yml:
 Chúng ta xóa bỏ thuộc tính ports của các backend service nội bộ và thay thế bằng expose để đóng kín hệ thống, chỉ mở duy nhất cổng 8080 của Gateway ra ngoài máy host vật lý.

services:

  # 1. User Service

  user-service:

    build:

      context: ./user-service

    container_name: user-service

    expose:

      - "8081"

    environment:

      - DB_HOST=${DB_HOST}

      - DB_PORT=${DB_PORT}

      - DB_NAME=${USER_DB_NAME}

      - DB_USERNAME=${USER_DB_USERNAME}

      - DB_PASSWORD=${USER_DB_PASSWORD}

      - SERVER_PORT=${USER_SERVER_PORT}

    networks:

      - quickbite-net

 

  # 2. Restaurant Service

  restaurant-service:

    build:

      context: ./restaurant-service

    container_name: restaurant-service

    expose:

      - "8082"

    environment:

      - DB_HOST=${DB_HOST}

      - DB_PORT=${DB_PORT}

      - DB_NAME=${RESTAURANT_DB_NAME}

      - DB_USERNAME=${RESTAURANT_DB_USERNAME}

      - DB_PASSWORD=${RESTAURANT_DB_PASSWORD}

      - SERVER_PORT=${RESTAURANT_SERVER_PORT}

    networks:

      - quickbite-net

 

  # 3. Order Service

  order-service:

    build:

      context: ./order-service

    container_name: order-service

    expose:

      - "8083"

    environment:

      - DB_HOST=${DB_HOST}

      - DB_PORT=${DB_PORT}

      - DB_NAME=${ORDER_DB_NAME}

      - DB_USERNAME=${ORDER_DB_USERNAME}

      - DB_PASSWORD=${ORDER_DB_PASSWORD}

      - SERVER_PORT=${ORDER_SERVER_PORT}

      - RESTAURANT_SVC_HOST=${RESTAURANT_SVC_HOST}

      - RESTAURANT_SVC_PORT=${RESTAURANT_SVC_PORT}

    networks:

      - quickbite-net

 

  # 4. Notification Service

  notification-service:

    build:

      context: ./notification-service

    container_name: notification-service

    expose:

      - "8084"

    environment:

      - DB_HOST=${DB_HOST}

      - DB_PORT=${DB_PORT}

      - DB_NAME=${NOTIFICATION_DB_NAME}

      - DB_USERNAME=${NOTIFICATION_DB_USERNAME}

      - DB_PASSWORD=${NOTIFICATION_DB_PASSWORD}

      - SERVER_PORT=${NOTIFICATION_SERVER_PORT}

    networks:

      - quickbite-net

 

  # 5. API Gateway (Điểm truy cập duy nhất mở ra host)

  gateway-service:

    build:

      context: ./gateway-service

    container_name: gateway-service

    ports:

      - "${GATEWAY_SERVER_PORT}:${GATEWAY_SERVER_PORT}"

    environment:

      - USER_SVC_HOST=${USER_SVC_HOST}

      - USER_SVC_PORT=${USER_SVC_PORT}

      - RESTAURANT_SVC_HOST=${RESTAURANT_SVC_HOST}

      - RESTAURANT_SVC_PORT=${RESTAURANT_SVC_PORT}

      - ORDER_SVC_HOST=${ORDER_SVC_HOST}

      - ORDER_SVC_PORT=${ORDER_SVC_PORT}

      - NOTIFICATION_SVC_HOST=${NOTIFICATION_SVC_HOST}

      - NOTIFICATION_SVC_PORT=${NOTIFICATION_SVC_PORT}

    networks:

      - quickbite-net

 

networks:

  quickbite-net:

    external: true  # Sử dụng chung mạng ảo với container database đang chạy ngầm

 

 

5. Hiểu lầm thường gặp (Cấu trúc Blocking với Thread-per-request)
Hiểu lầm thường gặp: Có thể nhúng các thư viện chặn luồng (Blocking IO) truyền thống như Spring Data JPA/Hibernate để trực tiếp truy cập cơ sở dữ liệu hoặc sử dụng Thread.sleep() bên trong các Filter tự viết của Spring Cloud Gateway.
Sự thật: Spring Cloud Gateway được xây dựng trên nền tảng Spring WebFlux và máy chủ web không đồng bộ Netty (Non-blocking IO). Cơ chế của Netty sử dụng rất ít luồng (Event Loop Threads) để xử lý đồng thời hàng nghìn request. Nếu bạn thực hiện một thao tác chặn luồng (blocking) bên trong Filter, luồng xử lý đó sẽ bị khóa cứng, làm treo Gateway hoàn toàn và sập toàn bộ hệ thống. Do đó, các bộ lọc Filter tùy biến trong Gateway bắt buộc phải tuân thủ triết lý Reactive (Non-blocking).
 

6. Tài liệu tham khảo
Hướng dẫn cấu hình Route trong Spring Cloud Gateway:

Spring Cloud Gateway Routes - Developer Guide

Danh sách các Predicate Factories có sẵn:

Route Predicate Factories - Spring Docs