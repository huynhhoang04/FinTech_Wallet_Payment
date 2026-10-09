Bài đọc: Giao tiếp liên dịch vụ qua Docker Network trong kiến trúc phân tán
 
1. Mục tiêu bài học
Sau khi hoàn thành bài học này, bạn sẽ có khả năng:

Giải thích được cơ chế phân giải tên miền tự động (Service Discovery) qua DNS server nội bộ của Docker Network.
Cấu hình cấu trúc mã nguồn giao tiếp đồng bộ REST API giữa các Spring Boot container bằng Spring Cloud OpenFeign.
Đóng cổng mạng của các service nội bộ để bảo mật hệ thống, chỉ cho phép giao tiếp nội bộ trong mạng Compose.
 

2. Vấn đề thực tế (Kết nối mạng giữa các service trên Staging/Production)
Khi chạy hệ thống QuickBite ở Lesson 2, cả hai dịch vụ user-service và restaurant-service đều hoạt động độc lập và chỉ kết nối tới cơ sở dữ liệu. Tuy nhiên, trong thực tế, các dịch vụ này cần gọi lẫn nhau để kiểm tra thông tin và xử lý nghiệp vụ:

Ví dụ: Khi khách hàng đặt đơn hàng hoặc thực hiện giao dịch, hệ thống cần gửi request kiểm tra trạng thái hoạt động của nhà hàng hoặc xác thực thông tin tài khoản người dùng.

 

Để hai dịch vụ container có thể giao tiếp với nhau qua giao thức HTTP, chúng ta cần giải quyết hai vấn đề:

Quản lý địa chỉ IP động: Khi một container khởi động lại hoặc được cập nhật code, Docker Engine sẽ thu hồi IP cũ và cấp cho nó một địa chỉ IP mới. Nếu viết cứng IP nội bộ của container này vào cấu hình của container kia, kết nối sẽ bị gián đoạn mỗi khi restart hệ thống.
Bảo mật cổng mạng (Ports Exposing): Không nên mở toàn bộ các cổng mạng (8081, 8082) ra ngoài máy host vật lý. Việc mở cổng tự do sẽ tăng rủi ro bảo mật (tin tặc có thể quét cổng và gọi trực tiếp tới API nội bộ). Chúng ta chỉ nên expose các cổng này trong mạng ảo nội bộ để các container gọi nhau, và đóng hoàn toàn truy cập từ internet.
 

 

 

3. Kiến thức cốt lõi (Docker Networks & OpenFeign) 
3.1. Cơ chế Service Discovery (Phát hiện dịch vụ) qua DNS nội bộ
Mạng ảo bridge nội bộ của Docker Compose hỗ trợ tính năng tự động phân giải tên container (đã phân tích kỹ ở Session 4 Lesson 5).

Khi các container cùng tham gia vào một mạng ảo chung (như mạng ngoài quickbite-net), chúng có thể giao tiếp trực tiếp qua Tên container/dịch vụ (ví dụ: http://restaurant-service:8082) thay vì dùng địa chỉ IP.

Trình phân giải DNS tích hợp của Docker sẽ tự động định tuyến các gói tin mạng đến đúng IP hiện tại của container đích.



3.2 Khái niệm Spring Cloud OpenFeign
Trong Spring Boot, thay vì sử dụng RestTemplate viết code thủ công, lập trình viên thường dùng OpenFeign để thực hiện các cuộc gọi REST API. OpenFeign giúp lập trình viên viết REST client dưới dạng các Interface rất trực quan. Cú pháp gọi sẽ sử dụng trực tiếp tên container làm tên host trong URL.

 

4. Hướng dẫn thực hành và kiểm tra
4.1 Khai báo mã giả Feign Client gọi liên dịch vụ
Dưới đây là mã giả (pseudocode) mô tả cách cấu hình Feign Client trong user-service để gọi sang API của restaurant-service lấy trạng thái đóng/mở cửa của nhà hàng:

// RestaurantClient.java

package com.quickbite.user.client;

 

import org.springframework.cloud.openfeign.FeignClient;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

 

// "restaurant-service" là tên container/service trong file docker-compose.yml

@FeignClient(name = "restaurant-service", url = "http://restaurant-service:8082")

public interface RestaurantClient {

 

    @GetMapping("/restaurants/{id}/status")

    boolean checkRestaurantStatus(@PathVariable("id") Long id);

}

 

4.2 Cập nhật docker-compose.yml đóng cổng mạng ra ngoài
Vì các container đã giao tiếp nội bộ trong mạng ảo dùng chung, dịch vụ restaurant-service không cần mở cổng 8082 ra máy host vật lý nữa. Chúng ta sẽ loại bỏ thuộc tính ports của restaurant-service và chỉ sử dụng expose (hoặc để trống vì các cổng nội bộ của container trong cùng mạng đều thông suốt). Chúng ta kế thừa file .env ở bài trước để nội suy cấu hình:

services:

  user-service:

    build:

      context: ./user-service

    container_name: user-service

    # Mở port 8081 ra ngoài host để Client/Browser có thể gọi vào kiểm thử

    ports:

      - "${USER_SERVER_PORT}:${USER_SERVER_PORT}"

    environment:

      - DB_HOST=${DB_HOST}

      - DB_PORT=${DB_PORT}

      - DB_NAME=${USER_DB_NAME}

      - DB_USERNAME=${USER_DB_USERNAME}

      - DB_PASSWORD=${USER_DB_PASSWORD}

      - SERVER_PORT=${USER_SERVER_PORT}

    networks:

      - quickbite-net

 

  restaurant-service:

    build:

      context: ./restaurant-service

    container_name: restaurant-service

    # KHÔNG dùng ports mở ra host nữa, chỉ expose nội bộ trong mạng Compose

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

 

networks:

  quickbite-net:

    external: true  # Kết nối tới mạng ảo chung chứa container database đang chạy ngầm

 

4.3 Thực hiện kiểm chứng phân giải tên miền
Khởi chạy hệ thống Compose:
docker compose up

 

Thực thi lệnh ping từ container user-service sang container restaurant-service bằng chính tên container để kiểm tra DNS hoạt động:
docker compose exec user-service ping -c 3 restaurant-service

 

Kết quả mong đợi: Lệnh ping thực hiện thành công, hiển thị rõ DNS nội bộ đã phân giải tên miền restaurant-service thành IP ảo của nó trong mạng (ví dụ: 172.18.0.4).
 

5. Hiểu lầm thường gặp (locahost trong Container)
Hiểu lầm thường gặp: Khi hai container chạy chung trên một máy chủ vật lý, chúng ta có thể cấu hình URL kết nối là http://localhost:8082 để gọi sang nhau.
Sự thật: Không được. Từ khóa localhost viết bên trong mã nguồn chạy trong container sẽ trỏ thẳng về chính không gian cô lập của container đó, không phải máy host vật lý. Để kết nối, ta bắt buộc phải sử dụng tên của container đích (restaurant-service) làm host trong URL kết nối nhờ cơ chế DNS nội bộ của Docker Network (đã phân tích ở Session 4 Lesson 5).
 

6. Tài liệu tham khảo
Mạng mặc định trong Docker Compose:

Docker Compose default network - Docker Docs

Cách phân giải DNS nội bộ của Docker:

Docker DNS Service Discovery - Docker Docs

 