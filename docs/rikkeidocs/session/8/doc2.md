Bài đọc: Tối ưu hóa Dockerfile cho Production (Multi-stage build)
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Phân tích được hạn chế của việc phân chia quy trình build thành nhiều stage độc lập trong workflow CI/CD và tính ưu việt của Multi-stage build.
Ứng dụng kỹ thuật Multi-stage build để thực hiện biên dịch mã nguồn Java và đóng gói sản phẩm hoàn toàn bên trong Dockerfile.
Tinh giản cấu hình workflow CI/CD về mức tối giản nhất.
Đánh giá được tác động của việc thiếu cache dependencies đến thời gian chạy (runtime) của Runner.
 

2. Vấn đề thực tế (Sự phức tạp của Pipeline 2 stage và quy trình Build không khép kín)
Ở bài học trước, chúng ta đã tự động hóa việc build Docker image bằng pipeline CI/CD gồm 2 jobs độc lập (build_jar và build_image). Tuy nhiên, quy trình này bộc lộ một số hạn chế trong thực tế:

Cấu hình workflow phức tạp và tốn tài nguyên: Việc phân tách thành nhiều job yêu cầu cấu hình truyền tải file JAR thông qua Action actions/upload-artifact@v4 ở job trước và actions/download-artifact@v4 ở job sau. Tiến trình này tiêu tốn dung lượng lưu trữ tạm thời trên GitHub và tăng băng thông truyền tải mạng giữa Runner và server.
Quy trình build không khép kín (Self-contained): File Dockerfile đơn tầng không thể tự build độc lập nếu thiếu file JAR được biên dịch sẵn từ trước. Lập trình viên không thể chạy một lệnh docker build duy nhất tại máy local để đóng gói hoàn chỉnh ứng dụng từ mã nguồn thô mà bắt buộc phải chạy lệnh biên dịch ./gradlew bootJar trên môi trường phát triển trước.
Rủi ro sai lệch phiên bản môi trường cục bộ: Nếu lập trình viên build JAR ở máy local bằng một phiên bản JDK khác (ví dụ JDK 21) rồi dùng Dockerfile đơn tầng đóng gói với base image JRE 17, ứng dụng có thể gặp lỗi runtime do không tương thích phiên bản bytecode.
Để chuẩn hóa quy trình build khép kín, độc lập với các công cụ cài đặt ngoài container, chúng ta cần đưa bước biên dịch mã nguồn vào bên trong chính Dockerfile thông qua kỹ thuật Multi-stage build (State 2: Multi-stage Dockerfile).

 

3. Kiến thức cốt lõi
3.1. Khái niệm Multi-stage Build trong Dockerfile
Multi-stage build cho phép sử dụng nhiều câu lệnh FROM trong cùng một tệp tin Dockerfile để chia quy trình đóng gói thành nhiều giai đoạn (stages) có nhiệm vụ riêng biệt:

Stage 1 (Builder stage): Sử dụng một base image JDK đầy đủ (như eclipse-temurin:17-jdk-alpine AS builder), sao chép toàn bộ mã nguồn Java vào và thực thi lệnh biên dịch ./gradlew bootJar (Wrapper sẽ tự động tải phiên bản Gradle tương thích mà không cần cài sẵn Gradle toàn cục).
Stage 2 (Runtime stage): Sử dụng một base image Java Runtime Environment (JRE) siêu gọn nhẹ (như eclipse-temurin:17-jre-alpine). Thực hiện sao chép file JAR thương phẩm từ Stage 1 sang (COPY --from=builder).
Kết quả: Toàn bộ công cụ JDK nặng nề và mã nguồn thô Java sẽ bị bỏ lại ở Stage 1, giúp image cuối cùng có kích thước tối thiểu và có độ bảo mật cao nhất (không lộ mã nguồn thô).


3.2. Tinh giản Workflow CI/CD tương ứng
Khi toàn bộ tiến trình build đã được Dockerfile quản lý, workflow CI/CD không cần thực hiện job biên dịch riêng biệt và truyền tải artifacts qua GitHub server nữa. File .github/workflows/ci.yml được tinh gọn tối đa: Chỉ cần khai báo duy nhất job build_image và chạy lệnh docker build.

 

3.3. Pain Point mới: Vấn đề Runtime trong CI/CD
Mặc dù Multi-stage build chuẩn hóa môi trường cực kỳ tốt, nó lại tạo ra một vấn đề kỹ thuật nghiêm trọng trong CI/CD:

● Do tiến trình biên dịch mã nguồn diễn ra bên trong container builder tạm thời của lệnh docker build, Gradle không thể tái sử dụng cache dependencies từ thư mục .gradle của máy host do không được cấu hình mount volume cache.
● Khi chạy lệnh ./gradlew bootJar bên trong Dockerfile, Gradle bắt buộc phải tải lại toàn bộ dependencies và plugins từ Maven Central.
● Việc này kéo dài thời gian chạy (runtime) của Runner lên tới 5 phút, tiêu tốn băng thông mạng và gây lãng phí tài nguyên vận hành CI/CD cực kỳ lớn.
 

4. Demo và thực hành (Multi-stage Build trong Dockerfile)
Học viên sửa đổi cấu hình của dự án user-service để chuyển quy trình biên dịch vào bên trong Dockerfile và rút gọn file CI/CD.

 

4.1. Biên soạn tệp tin Dockerfile
Cập nhật user-service/Dockerfile thành cấu trúc Multi-stage:

# Stage 1: Giai đoạn biên dịch (Builder stage)

FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

 

# Sao chép toàn bộ mã nguồn vào container (kết hợp .dockerignore để loại bỏ thư mục build/ và .gradle/ local)

COPY . .

 

# Cấp quyền thực thi và tiến hành biên dịch JAR (sử dụng --no-daemon để tránh treo máy trong container)

RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

 

# Stage 2: Giai đoạn chạy ứng dụng (Runtime stage)

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

 

# Sao chép file JAR đã biên dịch từ Stage 1 (builder) sang Stage 2

COPY --from=builder /app/build/libs/*.jar app.jar

 

# Khai báo cổng mạng ứng dụng và lệnh khởi chạy

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

    	

4.2. Biên soạn tệp cấu hình .github/workflows/ci.yml
Rút gọn file cấu hình user-service/.github/workflows/ci.yml chỉ còn 1 job (không cần DinD do dùng Local Runner gắn socket):

name: Build Docker Image Multi-stage

 

on:

  push:

    branches:

      - main

 

jobs:

  build_image:

    runs-on: [self-hosted, quickbite]

    steps:

      - name: Lấy mã nguồn

        uses: actions/checkout@v5

 

      - name: Đóng gói Docker Image

        # Build Docker image trực tiếp, toàn bộ việc compile được thực hiện bên trong Dockerfile

        run: docker build -t user-service:latest .

    	

Không kiểm tra trạng thái trong các bước sau run build:
Việc kiểm tra phải chạy trực tiếp trong khối script để nếu lệnh build sập, job CI/CD sẽ lập tức dừng lại và thông báo Failed.

 

4.3. Kết quả mong đợi
Pipeline chạy thành công chỉ với duy nhất một job build_image. Tuy nhiên, thời gian chạy của job kéo dài khoảng vài phút do tiến trình tải dependencies của Gradle bên trong container diễn ra rất lâu.

 

5. Lưu ý, hiểu lầm thường gặp
Không cấu hình .dockerignore khi sử dụng COPY . .: Khi dùng câu lệnh COPY . ., Docker sẽ sao chép toàn bộ thư mục dự án ở máy local vào container, bao gồm cả các thư mục chứa cache và tệp tin biên dịch tạm thời dưới máy local như build/ và .gradle/. Việc này làm tăng dung lượng build context không cần thiết và có thể gây lỗi hoặc xung đột tệp tin biên dịch.
Khắc phục: Học viên bắt buộc phải tạo tệp tin .dockerignore ở thư mục gốc của dự án và khai báo loại bỏ các thư mục này:
build/

.gradle/

.git/

 

Thời gian build trong CI/CD quá lâu: Học viên lo lắng khi thấy thời gian chạy workflow tăng vọt từ vài chục giây lên 4-5 phút. Đây là hành vi bình thường của cơ chế Multi-stage build khi chạy trong CI/CD. Dù đã chia sẻ Docker socket của máy host để thực thi lệnh build image nhanh hơn, tiến trình chạy Gradle bên trong container builder (FROM eclipse-temurin:17-jdk-alpine AS builder) hoàn toàn tách biệt và không thể truy cập thư mục cache Gradle (~/.gradle) của máy host, dẫn đến việc phải tải lại toàn bộ dependencies từ Maven Central ở mỗi lượt build.
 

6. Tài liệu tham khảo
Hướng dẫn Multi-stage build của Docker:

Use multi-stage builds | Docker Documentation

Quản lý Cache trong Gradle:

Gradle Build Environment Cache | Gradle Documentation