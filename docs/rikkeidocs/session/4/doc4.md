Bài đọc: Biến môi trường và cấu hình port
1. Vấn đề thực tế (Nguy cơ lộ các khóa quan trọng trên git)
Hãy tưởng tượng bạn đang viết tệp docker-compose.yml để triển khai hệ thống QuickBite. Bạn điền trực tiếp mật khẩu quản trị cơ sở dữ liệu vào file cấu hình:

services:

  quickbite-db:

    image: postgres:15-alpine

    environment:

      POSTGRES_PASSWORD: "Super-Secret-DB-Password-123456"

Bạn commit file này và push lên kho lưu trữ mã nguồn chung của công ty (GitLab/GitHub). 

Hiểm họa xảy ra: 
   Sáng hôm sau, Tech Lead và đội an ninh thông tin (Security Team) phát hiện ra thông tin nhạy cảm bị công khai. Bất kỳ ai có quyền xem code đều đọc được mật khẩu database. Đây là lỗ hổng bảo mật nghiêm trọng trong DevOps gọi là Hardcoded Credentials Leak.
   Hơn thế nữa, khi bạn mang file Compose này sang deploy ở môi trường khác (như Staging hay Production), bạn lại phải sửa trực tiếp file docker-compose.yml để thay đổi mật khẩu và cổng kết nối của từng môi trường. Việc này vi phạm hoàn toàn nguyên lý "Build một lần, chạy mọi nơi" (Build once, run anywhere).
Để giải quyết triệt để vấn đề bảo mật và tính linh hoạt cấu hình, chúng ta cần phân tách mã nguồn và thông tin cấu hình nhạy cảm thông qua Biến môi trường và file .env.

 

2. Kiến thức cốt lõi (ports vs expose, biến môi trường & tệp .env)
2.1. Cấu hình Port (ports)
Ở Bài học 1, chúng ta đã khai báo thuộc tính ports: - "5432:5432" trong file docker-compose.yml của quickbite-db mà chưa giải thích chi tiết. Bản chất của thuộc tính này tương đương hoàn toàn với tham số -p (Publish Port) khi sử dụng lệnh docker run ở Bài học 1.

Cú pháp ánh xạ cổng trong file Compose:

ports:

  - "HOST_PORT:CONTAINER_PORT"

So sánh trực quan với Docker CLI:
Lệnh Docker Run: docker run -p 5432:5432 ...
Khai báo Compose:
ports:

  - "5432:5432"

Cơ chế hoạt động: Docker Engine lắng nghe cổng HOST_PORT trên máy host vật lý và tự động định tuyến (NAT) toàn bộ traffic vào cổng CONTAINER_PORT bên trong container. Nhờ đó, có thể kết nối đến database từ bên ngoài thông qua: postgresql://localhost:5432.
2.2. Biến môi trường (environment)
Thay vì viết cứng cấu hình trong file application.properties (application.yml), Spring Boot cho phép nạp đè cấu hình thông qua các biến môi trường. Trong file Compose, ta truyền các biến này qua từ khóa environment:

services:

  quickbite-user:

    build: ./user-service

    ports:

      - "8081:8081"

    environment:

      - DB_HOST=quickbite-db

      - DB_PORT=5432

      - DB_NAME=quickbite_user_db

      - DB_USERNAME=quickbite_user

      - DB_PASSWORD=quickbite_user

      - SERVER_PORT=8081

      - JWT_SECRET_KEY=daylachuoimahoabimatrikkei0987654321dacbietracroi

      - JWT_EXPIRED_ACCESS=864000

      - JWT_EXPIRED_REFRESH=7

    networks:

      - quickbite-net

 

networks:

  quickbite-net:

    external: true

 

2.3. Tách biệt cấu hình bảo mật bằng file .env
Để tránh lộ thông tin nhạy cảm trên Git, Docker Compose tự động tìm kiếm một file ẩn tên là .env nằm cùng thư mục với file docker-compose.yml. 

 

A. Cú pháp trong file .env (Đặt cùng cấp với file docker-compose.yml):

# Database Common Configuration

DB_HOST=quickbite-db

DB_PORT=5432

 

# User Service Specific Configuration

USER_DB_NAME=quickbite_user_db

USER_DB_USERNAME=quickbite_user

USER_DB_PASSWORD=quickbite_user

USER_SERVER_PORT=8081

USER_JWT_SECRET_KEY=daylachuoimahoabimatrikkei0987654321dacbietracroi

USER_JWT_EXPIRED_ACCESS=864000

USER_JWT_EXPIRED_REFRESH=7

 

B. Cơ chế nội suy biến (Interpolation) trong docker-compose.yml:

   Chúng ta sử dụng cú pháp ${TÊN_BIẾN} để gọi giá trị từ file .env nạp vào file Compose:

services:

  quickbite-user:

    build: ./user-service

    ports:

      - "${USER_SERVER_PORT}:${USER_SERVER_PORT}"

    environment:

      - DB_HOST=${DB_HOST}

      - DB_PORT=${DB_PORT}

      - DB_NAME=${USER_DB_NAME}

      - DB_USERNAME=${USER_DB_USERNAME}

      - DB_PASSWORD=${USER_DB_PASSWORD}

      - SERVER_PORT=${USER_SERVER_PORT}

      - JWT_SECRET_KEY=${USER_JWT_SECRET_KEY}

      - JWT_EXPIRED_ACCESS=${USER_JWT_EXPIRED_ACCESS}

      - JWT_EXPIRED_REFRESH=${USER_JWT_EXPIRED_REFRESH}

    networks:

      - quickbite-net

 

networks:

  quickbite-net:

    external: true

 

C. Quy tắc bảo mật quan trọng:

File .env chứa mật khẩu thực tế tuyệt đối không được commit lên Git (bắt buộc phải khai báo trong file .gitignore).
Thay vào đó, bạn chỉ push file mẫu đặt tên là .env.example (chứa các key trống không có mật khẩu thật, ví dụ: DB_PASSWORD=) để làm tài liệu hướng dẫn cho các lập trình viên khác tự điền mật khẩu của riêng họ khi clone code về chạy.
 

3. Thực hành: Sử dụng file .env để cấu hình hệ thống Docker compose
Hãy thực hành cấu hình bảo mật hệ thống QuickBite local cho dịch vụ backend user-service:

A. Tạo file .env nằm ở thư mục dự án /quickbite-project/(cùng cấp với file docker-compose.yml chính):

# Cấu hình Database

# Database Common Configuration

   DB_HOST=quickbite-db

   DB_PORT=5432

 

   # User Service Specific Configuration

   USER_DB_NAME=quickbite_user_db

   USER_DB_USERNAME=quickbite_user

   USER_DB_PASSWORD=quickbite_user

   USER_SERVER_PORT=8081

   USER_JWT_SECRET_KEY=daylachuoimahoabimatrikkei0987654321dacbietracroi

   USER_JWT_EXPIRED_ACCESS=864000

   USER_JWT_EXPIRED_REFRESH=7

 

B. Tạo file .gitignore để khóa file .env lại không cho đẩy lên Git:

.env

/build/

 

C. Viết file docker-compose.yml sử dụng các biến nội suy:

services:

  quickbite-user:

    build:

      context: ./user-service

    ports:

      - "${USER_SERVER_PORT}:${USER_SERVER_PORT}"

    environment:

      - DB_HOST=${DB_HOST}

      - DB_PORT=${DB_PORT}

      - DB_NAME=${USER_DB_NAME}

      - DB_USERNAME=${USER_DB_USERNAME}

      - DB_PASSWORD=${USER_DB_PASSWORD}

      - SERVER_PORT=${USER_SERVER_PORT}

      - JWT_SECRET_KEY=${USER_JWT_SECRET_KEY}

      - JWT_EXPIRED_ACCESS=${USER_JWT_EXPIRED_ACCESS}

      - JWT_EXPIRED_REFRESH=${USER_JWT_EXPIRED_REFRESH}

    networks:

      - quickbite-net

 

networks:

  quickbite-net:

    external: true  # Sử dụng chung mạng ảo với container database đang chạy ngầm

 

D. Chạy lệnh kiểm tra tính hợp lệ và hiển thị cấu hình sau khi nội suy biến:

docker compose config

 

E. Kết quả mong đợi: 

Lệnh config sẽ đọc file .env, thay thế toàn bộ ký tự ${...} thành giá trị thực tế và in ra cấu hình hoàn chỉnh. Bạn sẽ thấy rõ mật khẩu và các cổng đã được điền chính xác.

F. Tiến hành khởi chạy cụm dịch vụ:

docker compose up -d

 

4. Hiểu lầm thường gặp (ports vs expose trong compose)
Hiểu sai: Từ khóa ports và expose đều dùng để mở cổng dịch vụ của container ra ngoài máy host vật lý.
Đính chính: Hoàn toàn khác nhau.
ports: Ánh xạ cổng ra ngoài máy host vật lý, cho phép bất kỳ ai từ bên ngoài (hoặc trình duyệt máy host) kết nối vào container.
expose: Chỉ mang tính chất khai báo cổng hoạt động nội bộ. Các container khác kết nối chung mạng Docker có thể gọi vào cổng này, nhưng máy host vật lý hoàn toàn không thể kết nối trực tiếp được vào cổng này.
  Lời khuyên bảo mật: Đối với database PostgreSQL nội bộ (quickbite-db), chúng ta chỉ cần dùng expose: - "5432" (hoặc không cần khai báo nếu các container dùng chung mạng tùy biến), tuyệt đối không dùng ports để tránh nguy cơ hacker quét và tấn công trực tiếp vào database của bạn từ bên ngoài internet.
 

5. Tài liệu tham khảo
Cách sử dụng biến môi trường trong Docker Compose:

Environment variables in Docker Compose - Docker Docs

Đặc tả cấu hình thuộc tính Ports và Expose:

Compose file ports reference - Docker Docs

 