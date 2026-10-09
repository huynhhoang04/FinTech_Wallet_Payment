Bài đọc: Volume và network trong Docker Compose
 

1. Vấn đề thực tế (Tiềm ẩn mất dữ liệu và IP bị thay đổi)
Hãy tưởng tượng bạn đang chạy thử nghiệm hệ thống QuickBite trên local bằng Docker Compose. 

Nỗi đau mất sạch dữ liệu: Bạn khởi chạy database bằng docker compose up -d, tạo tài khoản, đăng ký món ăn demo rất mất thời gian. Cuối buổi chiều, trước khi tắt máy ra về, bạn gõ lệnh dọn dẹp hệ thống: docker compose down. Sáng hôm sau, bạn bật lại hệ thống bằng docker compose up -d. Bạn bàng hoàng nhận ra toàn bộ tài khoản và món ăn đã biến mất không dấu vết, database trở lại trạng thái trống rỗng như lúc mới cài.
Nỗi đau IP biến động: Dịch vụ user-service cần gọi tới database. Nếu bạn điền cứng địa chỉ IP của container database (ví dụ: 172.19.0.2), kết nối sẽ lỗi ngay lập tức khi database restart và được cấp IP mới (ví dụ: 172.19.0.3).
 

Hai vấn đề nghiêm trọng này sẽ được giải quyết triệt để thông qua hai cơ chế cốt lõi của Docker Compose: Volumes (Lưu trữ bền vững) và Networks (Mạng ảo nội bộ & DNS).

 

Kế thừa từ Bài học 3: Ở Bài học 3, khi xây dựng cụm Database chạy độc lập và liên kết với dịch vụ backend, chúng ta đã khai báo thuộc tính volumes và networks như một giải pháp thực nghiệm để hệ thống hoạt động. Trong bài học này (Bài học 5), chúng ta sẽ đi sâu phân tích cơ chế hoạt động chi tiết bên dưới, so sánh các phương pháp cấu hình khác nhau và làm rõ bản chất kỹ thuật của chúng.

2. Kiến thức cốt lõi (Docker Volumes & Docker Networks trong Compose)
2.1. Docker Volumes trong Compose (Lưu trữ dữ liệu bền vững)
Mặc định, hệ thống file bên trong container là tạm thời (Ephemeral). Mọi dữ liệu ghi vào đó sẽ bị xóa sạch khi container bị hủy bỏ.

Để lưu trữ dữ liệu bền vững (Persistence), chúng ta sử dụng cơ chế chia sẻ ổ đĩa của Docker. Trong Docker Compose, có hai hình thức lưu trữ dữ liệu phổ biến nhất:

A. Named Volumes (Volume định danh do Docker tự quản lý)

Cú pháp khai báo: Chúng ta khai báo tên volume trong khối volumes: toàn cục ở cuối file Compose, sau đó mount volume đó vào thư mục bên trong container.
Ví dụ:
services:

  quickbite-db:

    image: postgres:15-alpine

    volumes:

      - db-data:/var/lib/postgresql/data  # Ánh xạ Named Volume vào container

 

volumes:

  db-data:  # Khai báo Named Volume toàn cục ở cuối file

 

Bản chất hoạt động: Docker Engine tự động tạo và quản lý một thư mục ẩn chuyên dụng trên ổ cứng máy host (ví dụ: /var/lib/docker/volumes/[tên_volume]/_data trên Linux). Lập trình viên không cần chỉ định đường dẫn thư mục cụ thể trên máy host (nên còn gọi là "not mounted" tới một thư mục máy host cụ thể một cách thủ công).
Đặc điểm: Hiệu năng đọc ghi cực cao, an toàn vì tránh được việc can thiệp nhầm hoặc xóa nhầm từ người dùng máy host, hoàn toàn độc lập với cấu trúc hệ điều hành máy host. Phù hợp nhất cho việc lưu trữ dữ liệu động phát sinh trong quá trình chạy (như file database PostgreSQL).
B. Bind Mounts (Liên kết trực tiếp thư mục/tệp từ máy host)

Cú pháp khai báo: Chỉ định đường dẫn tương đối (bắt đầu bằng ./) hoặc tuyệt đối của thư mục/tệp trên máy host vật lý sang thư mục bên trong container, không khai báo ở khối volumes: ở cuối file.
Ví dụ:
services:

  quickbite-db:

    image: postgres:15-alpine

    volumes:

      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql  # Mount trực tiếp tệp cấu hình

 

Bản chất hoạt động: Docker trực tiếp ánh xạ (gắn kết) thư mục hoặc file cụ thể từ ổ đĩa máy host vào container. Mọi sự thay đổi ở máy host sẽ phản ánh lập tức vào container và ngược lại.
Đặc điểm: Thích hợp cho việc nạp file cấu hình tĩnh (như file khởi tạo .sql, cấu hình Nginx .conf) hoặc phát triển code hot-reload (mount thư mục code gốc từ host vào container). Điểm yếu là phụ thuộc chặt chẽ vào cấu trúc đường dẫn thư mục và hệ điều hành của máy host (Windows vs Linux).
 

2.2. Docker Networks trong Compose & Service Discovery
Mặc định, khi bạn khởi chạy một tệp Compose, Docker Compose sẽ tự động làm 3 việc:

Tạo ra một mạng bridge riêng biệt dành cho dự án (thường đặt tên là [tên_thư_mục_dự_án]_default).
Tự động đưa tất cả các container (services) được khai báo trong file Compose tham gia vào mạng chung này.
Kích hoạt dịch vụ DNS nội bộ của Docker.
 

Cơ chế Service Discovery (Phát hiện dịch vụ)

Nhờ DNS nội bộ hoạt động trong mạng chung, các container có thể gọi nhau trực tiếp bằng Tên dịch vụ (Service Name) được khai báo trong file Compose (như quickbite-db, quickbite-user) thay vì sử dụng địa chỉ IP nội bộ không ổn định.



 

Phân biệt cấu hình mạng ảo: Tạo mới (name + driver) vs Tham chiếu mạng ngoài (external: true)

Khi định nghĩa mạng ảo (networks) trong tệp docker-compose.yml, có hai cách tiếp cận chính tùy thuộc vào vòng đời của mạng:

A. Khởi tạo và định danh mạng bridge ảo mới (Tạo mới):

networks:

  quickbite-net:

    name: quickbite-net  # Đặt tên cố định cho mạng ảo bridge

    driver: bridge

 

Bản chất: Khi chạy lệnh docker compose up, Docker Compose sẽ tự động tạo ra một mạng bridge ảo mới trên máy host có tên vật lý chính xác là quickbite-net.
Vòng đời: Mạng này được quản lý và sở hữu trực tiếp bởi cụm Compose hiện tại. Khi bạn chạy lệnh docker compose down, mạng ảo này sẽ bị xóa bỏ hoàn toàn cùng các container.
Ứng dụng: Thường khai báo tại cụm dịch vụ đóng vai trò là "chủ thể hạ tầng nền tảng" (ở đây là dự án quản lý database chung quickbite-database).
 

B. Tham chiếu mạng ảo có sẵn (external: true):

 

networks:

  quickbite-net:

    external: true

 

   * Bản chất: Khi khởi chạy, Docker Compose sẽ không tự tạo mới mạng bridge, mà đi tìm kiếm và kết nối các container vào một mạng ảo có tên quickbite-net đã được tạo sẵn từ trước (được tạo thủ công hoặc bởi cụm Compose khác).
   * Vòng đời: Hoàn toàn độc lập với tệp Compose hiện tại. Khi bạn chạy docker compose down, các container sẽ bị ngắt kết nối nhưng mạng quickbite-net vẫn tồn tại nguyên vẹn trên hệ thống.
   * Ứng dụng: Khai báo tại các ứng dụng độc lập (như dịch vụ backend quickbite-project) cần kết nối giao tiếp với cơ sở dữ liệu dùng chung. Nếu mạng này chưa được tạo từ trước, lệnh khởi động Compose sẽ báo lỗi và dừng lại.
 

3. Thực hành: Phân chia hạ tầng và kiểm chứng bền vững dữ liệu
Hãy hoàn thiện thiết lập cho hệ thống QuickBite bằng cách phân bổ tệp cấu hình cho hai phần độc lập: Database (Stateful) và User Service (Stateless), sử dụng mạng ảo chung quickbite-net và Named Volume để bảo vệ dữ liệu.

 

3.1. Chuẩn bị cấu trúc thư mục và các file cấu hình
Hãy đảm bảo bạn đã tạo đúng cấu trúc thư mục như sau ở môi trường phát triển:

Thư mục làm việc/

├── quickbite-database/        # Dự án quản lý cơ sở dữ liệu chung

│   ├── docker-compose.yml     # Khởi chạy PostgreSQL

│   └── init-db.sql            # Khởi tạo các DB và User con

│

└── quickbite-project/         # Dự án mã nguồn microservices

    ├── docker-compose.yml     # Khởi chạy user-service dùng biến môi trường

    ├── .env                    # Tệp cấu hình bảo mật chứa biến môi trường cục bộ

    └── user-service/

        ├── Dockerfile

        └── build/libs/user-service.jar

A. File cấu hình Database (quickbite-database/docker-compose.yml)

services:

  quickbite-db:

    image: postgres:15-alpine

    container_name: quickbite-db

    ports:

      - "5432:5432"

    environment:

      POSTGRES_USER: postgres

      POSTGRES_PASSWORD: postgres

    volumes:

      - quickbite-db-volume:/var/lib/postgresql/data

      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql

    networks:

      - quickbite-net

    restart: always

 

volumes:

  quickbite-db-volume: # Named Volume được quản lý bởi Docker để lưu trữ dữ liệu Postgres

 

networks:

  quickbite-net:

    name: quickbite-net  # Đặt tên cố định cho mạng bridge dùng chung

    driver: bridge

 

B. File cấu hình Environment (quickbite-project/.env)

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

 

C. File cấu hình Backend Service (quickbite-project/docker-compose.yml)

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

    external: true  # Khai báo sử dụng mạng ảo bên ngoài được tạo ở Bài 1

 

3.2. Quy trình kiểm chứng độ bền vững dữ liệu và giao tiếp mạng
A. Khởi chạy cụm Database: Di chuyển vào thư mục /quickbite-database/ và chạy:

docker compose up -d

 

B. Khởi chạy Backend Service: Di chuyển vào thư mục /quickbite-project/ và chạy:

docker compose up -d --build

 

C. Kiểm tra xem volume và network đã được tạo thành công chưa:

docker volume ls

# Kết quả mong đợi: Hiển thị volume tên quickbite-database_quickbite-db-volume

 

docker network ls

# Kết quả mong đợi: Hiển thị mạng bridge mang tên quickbite-net

 

D. Tạo dữ liệu thử nghiệm trong Database: Truy cập trực tiếp vào container database và tạo một bảng thử nghiệm:

docker compose exec quickbite-db psql -U postgres -d quickbite_user_db -c "CREATE TABLE test_volume (id serial PRIMARY KEY, note VARCHAR(50)); INSERT INTO test_volume (note) VALUES ('Du lieu van con an toan!');"

 

E. Thực hiện xóa cụm dịch vụ để giả lập sự cố/nâng cấp:

Tắt và xóa container backend: Di chuyển sang thư mục /quickbite-project/ và chạy:
docker compose down

Tắt và xóa container database: Di chuyển sang thư mục /quickbite-database/ và chạy:
docker compose down

 

 

(Tại thời điểm này, cả 2 container backend và database đã bị xóa hoàn toàn khỏi hệ thống, giải phóng bộ nhớ RAM và CPU).

 

F. Khởi chạy lại Database để xác minh dữ liệu:

Di chuyển vào /quickbite-database/ và khởi chạy lại container:

docker compose up -d

Truy cập lại database để kiểm tra bảng test_volume vừa tạo:

docker compose exec quickbite-db psql -U postgres -d quickbite_user_db -c "SELECT * FROM test_volume;"

Kết quả mong đợi: Console trả ra bảng dữ liệu chứa dòng chữ 'Du lieu van con an toan!'. Dữ liệu hoàn toàn không bị mất đi nhờ cơ chế Named Volume.
 

G. Khởi chạy lại Backend Service:

Di chuyển vào /quickbite-project/ và chạy:

docker compose up -d

Dịch vụ backend sẽ khởi động lại, kết nối lại mượt mà tới database đang chạy nhờ mạng ảo dùng chung quickbite-net có sẵn.

 

4. Hiểu lầm thường gặp (Mạng mặc định của Docker Compose)
Hiểu sai: Mạng mặc định tự động tạo ra khi chạy Docker Compose hoạt động giống hệt mạng mặc định (default bridge) của Docker CLI khi chạy lệnh docker run đơn lẻ.
Đính chính: Hoàn toàn khác nhau về bản chất.
Mạng mặc định của Docker CLI (default bridge) không hỗ trợ tính năng tự động phân giải tên container (DNS nội bộ). Container chỉ có thể kết nối với nhau qua IP.
Mạng mặc định do Docker Compose tự động tạo ra cho dự án thực chất là một mạng User-defined Bridge Network.
Nó tương đương với lệnh tạo network thủ công:

# Bước 1: Tự tạo một mạng bridge tùy biến bằng tay

docker network create --driver bridge quickbite-net

 

# Bước 2: Khởi chạy container database và ném vào mạng ảo đó

docker run -d --name quickbite-db --network quickbite-net -e POSTGRES_PASSWORD=secret_password postgres:15-alpine

Mạng này tự động bật sẵn dịch vụ DNS nội bộ, cho phép các dịch vụ gọi nhau bằng tên service một cách hoàn toàn tự động và ổn định. Do đó, bạn không cần phải tự tạo mạng thủ công như ở các bài học trước.
 

5. Tài liệu tham khảo
Quản lý mạng trong Docker Compose:
Networking in Docker Compose - Docker Docs

Quản lý dữ liệu bằng Volume trong Docker Compose:
Volumes in Docker Compose - Docker Docs

 