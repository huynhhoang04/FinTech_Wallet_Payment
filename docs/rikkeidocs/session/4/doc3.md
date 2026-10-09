Bài đọc: Cấu trúc file docker-compose.yml (services, image, build)
1. Vấn đề thực tế (Quên build lại image)
Hãy tưởng tượng bạn đang sửa đổi một tính năng trong mã nguồn của user-service. Sau khi sửa code xong, bạn chạy Gradle để compile ra file JAR mới tại build/libs/user-service.jar. 

 

Để cập nhật tính năng mới này chạy trong container trên máy local, bạn phải thực hiện thủ công 3 bước:

# 1. Tự chạy lệnh build để đóng gói image mới từ Dockerfile

docker build -t quickbite-user:v2 .

 

# 2. Xóa container cũ đang chạy bản code cũ

docker stop quickbite-user && docker rm quickbite-user

 

# 3. Chạy container mới từ image vừa build

docker run -d --name quickbite-user -p 8081:8081 quickbite-user:v2

 

Vấn đề rủi ro: 
Quy trình này quá rườm rà. Lập trình viên rất dễ quên chạy bước 1 (quên build lại image mới) mà trực tiếp restart lại container.
Kết quả là container khởi chạy thành công nhưng vẫn chạy trên phiên bản code cũ nằm trong image cũ. Bạn sẽ mất hàng giờ để debug mệt mỏi chỉ vì câu hỏi: "Tại sao em đã sửa code trên máy rồi mà log lỗi của container vẫn hiển thị dòng lỗi cũ?"
 

Để tự động hóa hoàn toàn chuỗi thao tác: "Build code -> Đóng gói Image -> Tạo Container mới", Docker Compose cung cấp cơ chế tự build image thông qua từ khóa build.

 

2. Kiến thức cốt lõi (Cấu trúc docker-compose.yml & Dockerfile cơ bản)
2.1. Cấu trúc cơ bản của docker-compose.yml
Tệp cấu hình của Docker Compose được viết bằng định dạng YAML (phân biệt các khối dữ liệu bằng thụt lề khoảng trắng, tuyệt đối không dùng phím Tab). Các từ khóa nền tảng bao gồm:

 services: Khối dữ liệu chứa định nghĩa của tất cả các container trong cụm dịch vụ.
 image: Tên Docker Image được lấy từ Docker Hub (ví dụ: postgres:15-alpine).
2.2. Khai báo thuộc tính build trong Compose
Thay vì chỉ định cứng một image có sẵn trên mạng thông qua key image, chúng ta sử dụng thuộc tính build trỏ tới Dockerfile mà chúng ta đã định nghĩa trước đó (lấy từ bài trước):

context: Thư mục chứa mã nguồn của dịch vụ (nơi Docker tìm kiếm file Dockerfile và file JAR).
dockerfile: Tên file Dockerfile (mặc định là Dockerfile, có thể bỏ qua nếu bạn đặt tên file đúng chuẩn).
Ví dụ cấu trúc khai báo dịch vụ tự build trong docker-compose.yml:

services:

  quickbite-user:

    build:

      context: ./user-service

      dockerfile: Dockerfile

    ports:

      - "8081:8081"

3. Thực hành: Compose với Dockerfile và build
3.1. Docker compose cho Database
Trong hệ thống Microservices QuickBite, các dịch vụ sẽ phát triển dần theo thời gian. Để chuẩn bị môi trường lưu trữ lâu dài và nhất quán cho toàn bộ khóa học, chúng ta sẽ thiết lập một container PostgreSQL dùng chung duy nhất chạy độc lập, và chúng ta sẽ sử dụng Docker compose để các bạn làm quen các lệnh, ngay cả khi file Docker compose này chỉ dùng để Up 1 container duy nhất.

 

A. Tại sao tách biệt Database chạy độc lập thay vì Compose chung?

Thay vì khai báo chung Database (quickbite-db) và Application (user-service) vào cùng một file docker-compose.yml, việc tách biệt database ra chạy độc lập mang lại 4 lợi ích lớn:

Tối ưu tốc độ phát triển (Development Loop): Database khởi chạy rất nặng và chậm. Khi code Java thay đổi, dev cần rebuild và restart container backend liên tục. Tách biệt DB giúp backend có thể tắt/mở thoải mái mà không làm gián đoạn hoặc phải khởi động lại DB.
Tiết kiệm RAM/CPU: Hệ thống QuickBite gồm 4 microservices. Nếu mỗi service tự up 1 Postgres container riêng, máy local sẽ chạy 4 Postgres song song gây ngốn tài nguyên. Dựng 1 container Postgres chung chứa 4 database con là tối ưu nhất.
An toàn dữ liệu (Stateless vs Stateful): Backend là Stateless (không lưu trạng thái) nên xóa/dựng rất dễ dàng. Database là Stateful (giữ dữ liệu). Tách biệt giúp tránh việc sơ ý xóa sạch dữ liệu DB khi dọn dẹp backend bằng lệnh docker compose down -v.
Chuẩn Production: Trong thực tế, database luôn chạy độc lập trên các dịch vụ Managed Database (như AWS RDS, Cloud SQL) chứ không bao giờ chạy chung pod/host với container ứng dụng.
 

B. File cấu hình và Script Khởi tạo Cơ sở dữ liệu tự động

Để tự động tạo đầy đủ 4 database biệt lập kèm tài khoản và phân quyền tương ứng cho từng dịch vụ khi database khởi chạy lần đầu, PostgreSQL hỗ trợ cơ chế quét và chạy tự động các script nằm trong thư mục /docker-entrypoint-initdb.d/ bên trong container.

 

Giả định có thư mục tên là quickbite-database và nằm ngoài thư mục dự án Java, chuẩn bị 2 file cấu hình sau:

File kịch bản khởi tạo SQL (init-db.sql): Tệp này sẽ tạo các user và database biệt lập cho 2 dịch vụ user-service và restaurant-service, các dịch vụ khác cũng triển khai tương tự.

-- 1. Tạo các User riêng cho từng Service

CREATE USER quickbite_user WITH PASSWORD 'quickbite_user';

CREATE USER quickbite_restaurant WITH PASSWORD 'quickbite_restaurant';

-- 2. Tạo các Database tương ứng (Tạo cả hai dạng tên để tương thích mọi Session)

CREATE DATABASE quickbite_user_db OWNER quickbite_user;

CREATE DATABASE quickbite_restaurant_db OWNER quickbite_restaurant;

-- 3. Phân quyền truy cập toàn diện trên từng database cho user tương ứng

GRANT ALL PRIVILEGES ON DATABASE quickbite_user_db TO quickbite_user;

GRANT ALL PRIVILEGES ON DATABASE quickbite_restaurant_db TO quickbite_restaurant;

-- 4. Làm tương tự với các dịch vụ còn lại

File cấu hình Docker Compose cho database (docker-compose.yml): Đặt file này cùng thư mục với init-db.sql. Chúng ta sẽ mount file SQL vào thư mục tự khởi động của container và đặt tên mạng cố định là quickbite-net để các container backend ở các bài học sau có thể kết nối vào mạng này.

services:

  quickbite-db:

    image: postgres:15-alpine

    container_name: quickbite-db

    ports:

      - "5432:5432" # Mapping này không bắt buộc, nhưng map ra ngoài sẽ dễ kết nối bằng các công cụ quản lý DB hơn

    environment:

      POSTGRES_USER: postgres

      POSTGRES_PASSWORD: postgres 

    volumes:

      - db-data:/var/lib/postgresql/data

      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql  # Mount script khởi tạo tự động

    networks:

      - quickbite-net

    restart: always

volumes:

  db-data:

networks:

  quickbite-net:

    name: quickbite-net  # Đặt tên cố định cho mạng ảo chung

    driver: bridge

 

C. Cách khởi chạy và giữ Database chạy ngầm lâu dài

Để chạy database này một lần duy nhất cho toàn bộ môn học, bạn chỉ cần di chuyển vào thư mục quickbite-database và chạy lệnh:

# Khởi chạy database chạy ngầm và giữ trạng thái luôn restart nếu lỗi

docker compose up -d

Từ thời điểm này, database PostgreSQL của bạn đã sẵn sàng chạy ngầm trong máy tính. Bạn có thể tự do phát triển, build lại, tắt/mở các container ứng dụng Spring Boot ở các bài học tiếp theo mà không cần phải chạm vào hay khởi động lại database nữa! Và Container này rất nhẹ chỉ khoảng 300MB đến 500MB, hoàn toàn có thể không bao giờ tắt đi!

 

D. Dọn dẹp Database

docker compose down

Có thể thêm cờ -v để xoá cả volume đi kèm trong Docker compose. 

 

3.2. Docker compose cho các service và tái sử dụng Dockerfile từ bài trước
A. Thiết lập cấu trúc thư mục mẫu:

   quickbite-project/

   ├── docker-compose.yml

   └── user-service/

       ├── … (các file/folder khác)

       ├── Dockerfile

       └── build/

           └── libs/

               └── user-service.jar  (File JAR đã được build sẵn từ Gradle)

   └── restaurant-service/

       └── … (tương tự user-service)

 

B. Viết nội dung file docker-compose.yml tại thư mục gốc:

services:

  quickbite-user:

    build:

      context: ./user-service

      dockerfile: Dockerfile

    ports:

      - "8081:8081"

    networks:

      - quickbite-net

 

  quickbite-restaurant:

    build:

      context: ./restaurant-service

      dockerfile: Dockerfile

    ports:

      - "8082:8082"

    networks:

      - quickbite-net

 

networks:

  quickbite-net:

    external: true  # Sử dụng mạng ảo chung đã được khởi tạo bởi database ở Bài 1

 

 

C. Chạy lệnh build và start cụm dịch vụ đồng thời:

docker compose up --build

Ở ví dụ này có sử dụng --build, nhưng thật ra có thể lược bỏ đi vì đã được build 1 lần từ trước rồi, trong trường hợp đã thay đổi code và đã build jar lại, thì bắt buộc phải thêm cờ này vào để build lại image.
 

D. Kết quả mong đợi: 

Docker Compose tự động truy cập vào thư mục ./user-service, thực thi lệnh build đóng gói file JAR thành image tĩnh, đặt tên image tạm thời, sau đó khởi chạy container database và backend cùng một lúc.

4. Hiểu lầm thường gặp (Kết hợp image và build trong service)
Hiểu lầm: Trong một service của file Compose, chúng ta chỉ được phép khai báo một trong hai từ khóa: hoặc là image, hoặc là build. Nếu khai báo cả hai sẽ gây lỗi cú pháp.
Sự thật: Hoàn toàn có thể khai báo cả hai. 
Ví dụ:
quickbite-user:

  build: ./user-service

  image: quickbite-user-service:v1.0.0

Cơ chế hoạt động: Khi bạn chạy docker compose up --build, Docker Compose sẽ build image từ thư mục ./user-service theo hướng dẫn Dockerfile, nhưng thay vì đặt tên ngẫu nhiên, nó sẽ tự động gán nhãn tên image đó là quickbite-user-service:v1.0.0. Kỹ thuật này rất quan trọng để tag tên image tự build trước khi đẩy (push) lên kho lưu trữ Docker Registry.
 

5. Tài liệu tham khảo
Đặc tả cấu trúc file Docker Compose (Services section):
Compose file services reference - Docker Docs

Chi tiết về cấu hình build trong Compose:
Compose Build definition - Docker Docs