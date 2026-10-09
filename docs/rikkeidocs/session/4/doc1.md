Bài đọc: Dockerfile và cách đóng gói ứng dụng Spring Boot thực tế
 

1. Vấn đề thực tế (Mọi sự thật nằm trong lệnh docker run hay Dockerfile)
Hãy tưởng tượng bạn đang tiếp quản dự án QuickBite ở môi trường phát triển local. Hệ thống lúc này có các thành phần cốt lõi:

quickbite-db (Database): Container chạy PostgreSQL 15 để lưu trữ dữ liệu.
user-service (Dịch vụ người dùng): Ứng dụng Spring Boot chạy Java 17 cần kết nối tới database.
restaurant-service (Dịch vụ nhà hàng): Ứng dụng Spring Boot chạy Java 21 cần kết nối tới database.
 

Để dựng môi trường chạy thử nghiệm toàn bộ hệ thống này, bạn phải mở terminal lên và gõ tuần tự các câu lệnh sau:

# 1. Khởi chạy Database

docker run -d --name quickbite-db -e POSTGRES_PASSWORD=secret postgres:15-alpine

 

# 2. Bạn phải tìm địa chỉ IP nội bộ của container database vừa chạy

docker inspect quickbite-db

# Ví dụ tìm được địa chỉ IP động: 172.17.0.2

 

# 3. Khởi chạy User Service, truyền cứng địa chỉ IP của DB và mount thư mục JAR từ host

docker run -d --name user-service -p 8081:8081 -v /path/to/user-service/build/libs:/app -w /app -e DB_HOST=172.17.0.2 -e DB_PORT=5432 -e DB_NAME=quickbite_user_db -e DB_USERNAME=quickbite_user -e DB_PASSWORD=quickbite_user eclipse-temurin:17-jre-alpine java -jar user-service.jar

 

# 4. Khởi chạy Restaurant Service, cũng truyền cứng địa chỉ IP của DB và mount thư mục JAR từ host

docker run -d --name restaurant-service -p 8082:8082 -v /path/to/restaurant-service/build/libs:/app -w /app -e DB_HOST=172.17.0.2 -e DB_PORT=5432 -e DB_NAME=quickbite_restaurant_db -e DB_USERNAME=quickbite_restaurant -e DB_PASSWORD=quickbite_restaurant eclipse-temurin:21-jre-alpine java -jar restaurant-service.jar

Vấn đề đặt ra: 
Làm sao lập trình viên khác trong đội ngũ phát triển (hoặc hệ thống CI/CD server) biết được user-service chạy Java 17 còn restaurant-service chạy Java 21?
Làm sao họ biết ứng dụng lắng nghe ở cổng (port) nào bên trong?
Lệnh khởi chạy thủ công này quá dài dòng, dễ gõ sai sót tham số cổng, đường dẫn volume, hoặc nhầm lẫn tên biến môi trường.
 

Nếu chúng ta chỉ lưu giữ các thông tin này trong tài liệu hướng dẫn (README) viết bằng chữ, hoặc tệ hơn là truyền tai nhau, "sự thật" về cách vận hành ứng dụng sẽ bị phân tán. Chỉ cần gõ sai một ký tự, hệ thống sẽ lỗi ngay lập tức.

 

Giải pháp: Dockerfile - Nguồn sự thật duy nhất (Source of Truth)

Thay vì phân tán cấu hình chạy ở lịch sử terminal hay các file ghi chú, chúng ta gom toàn bộ đặc tả môi trường chạy (Hệ điều hành nền, Java Runtime, file JAR cần sao chép, cổng chạy, lệnh khởi động) vào một tệp tin cấu hình duy nhất đặt tên là Dockerfile nằm ngay trong mã nguồn dịch vụ. 

Bất kỳ ai, bất kỳ server nào khi có mã nguồn và tệp Dockerfile này đều có thể tự động đóng gói ra một Docker Image hoạt động nhất quán, chính xác 100% giống như nhau.

2. Kiến thức cốt lõi (Cấu trúc Dockerfile & Phân biệt Dockerfile & Phân biệt ENTRYPOINT vs CMD) 
 

2.1. Cấu trúc Dockerfile cơ bản cho Spring Boot
Một tệp tin Dockerfile (viết hoa chữ D, không có phần mở rộng đuôi như .txt hay .docker) là một chuỗi các chỉ thị được thực thi tuần tự từ trên xuống dưới để build ra một image:

# 1. Định nghĩa base image (JDK/JRE phù hợp với ứng dụng)

FROM eclipse-temurin:17-jre-alpine

 

# 2. Tạo thư mục làm việc mặc định bên trong container

WORKDIR /app

 

# 3. Sao chép file JAR đã build từ máy host vào container

COPY build/libs/user-service.jar app.jar

 

# 4. Khai báo cổng lắng nghe nội bộ (chỉ mang ý nghĩa tài liệu hóa)

EXPOSE 8081

 

# 5. Lệnh khởi chạy tiến trình chính

ENTRYPOINT ["java", "-jar", "app.jar"]

Chi tiết các chỉ thị cơ bản:

FROM: Điểm bắt đầu của mọi Dockerfile. Chỉ thị này kéo một image nền có sẵn về làm môi trường chạy gốc. Với Java Spring Boot, ta thường dùng JRE (Java Runtime Environment) phiên bản Alpine siêu nhẹ để giảm dung lượng (chỉ khoảng 100MB).
WORKDIR: Thiết lập thư mục làm việc hiện hành bên trong container. Các chỉ thị chạy sau như COPY hay ENTRYPOINT sẽ thực thi tương đối với thư mục này.
COPY: Sao chép các tệp tin từ máy host vật lý vào hệ thống file nội bộ của container. Ở đây ta copy file JAR đã đóng gói từ build Gradle/Maven thành file app.jar ngắn gọn.
EXPOSE: Khai báo cổng mà container sẽ lắng nghe khi chạy. Nó không tự mở cổng ra máy host, nhưng giúp các kỹ sư DevOps khác đọc vào biết ngay service này chạy cổng nào.
ENTRYPOINT / CMD: Định nghĩa lệnh sẽ thực thi khi container bắt đầu khởi chạy.
2.2. Phân biệt ENTRYPOINT vs CMD (Tiến trình Foreground vs Background)
Cả ENTRYPOINT và CMD đều có thể dùng để chạy ứng dụng Spring Boot. Tuy nhiên, sự khác biệt thực tế của chúng rất quan trọng trong môi trường vận hành thực tế:

Tiêu chí so sánh

ENTRYPOINT (Khuyên dùng cho tiến trình chính)

CMD (Dùng cho tham số hoặc lệnh bổ trợ)

Bản chất chạy

Chạy ở chế độ Foreground (Tiến trình chính PID 1).

Thường chạy ở chế độ Background qua trình shell (nếu viết dạng thường).

Cách tắt container

Nhận trực tiếp tín hiệu SIGTERM từ lệnh docker stop để shutdown an toàn (Graceful Shutdown).

Không nhận được tín hiệu dừng hệ thống trực tiếp, dẫn đến bị tắt cưỡng bức (SIGKILL) sau 10 giây.

Khả năng ghi đè

Rất khó bị ghi đè ngẫu nhiên khi chạy lệnh docker run.

Rất dễ bị ghi đè hoàn toàn nếu ta truyền thêm bất kỳ tham số nào lúc chạy.

Giải thích chi tiết về Tín hiệu dừng hệ thống (SIGTERM vs SIGKILL)

Khi bạn chạy lệnh docker stop [container_name], Docker Engine sẽ gửi tín hiệu tắt nhẹ nhàng mang tên SIGTERM tới tiến trình mang mã định danh PID 1 (tiến trình gốc) của container.



Với ENTRYPOINT viết dạng mảng (Exec Form - ENTRYPOINT ["java", "-jar", "app.jar"]): Ứng dụng Java của bạn sẽ được gán trực tiếp làm tiến trình gốc PID 1. Khi có tín hiệu dừng, Spring Boot sẽ nhận được trực tiếp SIGTERM, từ từ đóng các cổng kết nối database, hoàn thành nốt các request dở dang rồi mới tắt. Đây gọi là Graceful Shutdown.
Với CMD viết dạng chuỗi (Shell Form - CMD java -jar app.jar): Docker sẽ chạy lệnh này bằng cách dựng một tiến trình shell làm trung gian trước: /bin/sh -c "java -jar app.jar". Lúc này, trình shell /bin/sh chiếm quyền PID 1, còn Java chỉ là tiến trình con (PID 2). Khi tắt container, trình shell nhận SIGTERM nhưng không chuyển tiếp tín hiệu này xuống tiến trình Java. Spring Boot hoàn toàn không biết hệ thống đang tắt. Sau 10 giây chờ đợi không phản hồi, Docker Engine sẽ phát lệnh cưỡng bức SIGKILL để tiêu diệt tiến trình Java ngay lập tức, có thể gây mất mát dữ liệu hoặc lỗi trạng thái kết nối.
3. Thực hành: Tự đóng gói và khởi chạy user service qua Dockerfile
Hãy tự đóng gói dịch vụ user-service thủ công bằng Docker CLI để kiểm chứng:

A. Chuẩn bị file JAR: 

Đảm bảo bạn đã build file JAR của ứng dụng Spring Boot local (ở đây ví dụ là user-service):

# Di chuyển vào thư mục dự án user-service

cd user-service

# Thực hiện build ứng dụng bằng Gradle

./gradlew bootJar

B. Tạo tệp tin Dockerfile:

Tạo một file mới tên là Dockerfile nằm ngay tại thư mục gốc của project /user-service/ với nội dung sau:

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/user-service.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

C. Thực hiện build Image:

Gõ lệnh sau trong terminal để Docker đọc Dockerfile và tiến hành đóng gói (lưu ý dấu chấm `.` ở cuối đại diện cho Current Directory (Thư mục hiện tại) làm Build Context):

docker build -t quickbite-user-service:v1 .

D. Kiểm tra Image vừa build:

docker images

Kết quả mong đợi: Xuất hiện image quickbite-user-service với tag v1 trong danh sách, dung lượng cực kỳ gọn nhẹ (~100-150MB).
E. Khởi chạy Container từ Image tự build:

docker build -t quickbite-user-service:v1 .

F. Kiểm tra nhật ký log để xác nhận Spring Boot chạy thành công:

docker logs -f user-service

4. Hiểu lầm thường gặp (Expose Port vs Publish Port)
Hiểu lầm: Khai báo chỉ thị EXPOSE 8081 trong Dockerfile có nghĩa là Docker sẽ tự động mở cổng 8081 của container ra ngoài máy host vật lý để truy cập được từ trình duyệt.
Sự thật: Hoàn toàn không.
EXPOSE 8081 chỉ mang tính chất khai báo thông tin (Tài liệu hóa) cho con người và các hệ thống khác biết cổng chạy mặc định. Nó hoàn toàn không tạo ra bất kỳ một luật định tuyến cổng nào xuống máy host.
Để mở cổng truy cập được từ máy host, bạn bắt buộc phải dùng tham số -p (Publish Port) khi chạy lệnh khởi tạo container: 
docker run -p 8081:8081 ...

Nếu bạn viết EXPOSE 8081 trong Dockerfile nhưng lúc chạy lại gõ lệnh docker run -d --name test quickbite-user-service:v1 (thiếu tham số -p), bạn sẽ không bao giờ kết nối được tới ứng dụng từ máy host vật lý.