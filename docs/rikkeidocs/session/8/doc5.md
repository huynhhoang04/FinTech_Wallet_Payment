Bài đọc: Kịch bản Thực hành Tổng hợp với user-service
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Thực hành thành thạo quy trình build, gắn tag phiên bản Docker image của dịch vụ user-service tại máy phát triển local.
Đăng nhập và đẩy thành công image lên GitHub Container Registry (ghcr.io) cá nhân.
Biên soạn và kiểm thử hoàn thiện tệp cấu hình .github/workflows/ci.yml để kiểm tra container tự động trong luồng CI/CD với thời gian chạy tối ưu.
 

2. Bối cảnh thực hành
Bài thực hành tổng hợp này giúp học viên củng cố các kỹ năng tương tác với GitHub Container Registry đã học ở các bài học trước:

Đóng gói và gắn tag Docker image thủ công tại máy phát triển cá nhân để nắm vững cú pháp lệnh.
Đẩy image lên Registry cá nhân bằng Access Token (PAT).
Cấu hình luồng CI/CD để tự động kéo image đó về và khởi chạy verify trên môi trường Runner.
 
Lưu ý quan trọng cho học viên:
Kịch bản build/push từ local và pull/verify trên CI/CD chỉ phục vụ mục đích học tập để chia nhỏ độ phức tạp của hệ thống. Trong môi trường doanh nghiệp thực tế, để đảm bảo tính an toàn thông tin và tính nhất quán của mã nguồn, toàn bộ quy trình từ build, push đến deploy bắt buộc phải chạy hoàn toàn tự động trên máy chủ CI/CD, không có sự can thiệp thủ công từ máy local của lập trình viên.

 

3. Kịch bản thực hành chi tiết (Step-by-step)
Học viên thực hiện đầy đủ các bước sau trực tiếp trên dự án user-service đã có sẵn trong workspace.

Bước 1: Viết và kiểm tra Dockerfile tối ưu tại Local

Tạo tệp tin Dockerfile tại thư mục gốc của dự án user-service (user-service/Dockerfile) sử dụng cấu trúc Multi-stage build đã học ở Lesson 02:
# Stage 1: Build stage

FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Sao chép toàn bộ mã nguồn vào container

COPY . .

RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

 

# Stage 2: Runtime stage

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

 

Chạy thử câu lệnh build image tại local terminal để đảm bảo Dockerfile hoạt động không có lỗi:
docker build -t user-service:1.0.0 .

 

Bước 2: Khởi tạo Access Token và đăng nhập Registry

Truy cập giao diện Web của GitHub cá nhân -> Settings -> Developer settings -> Personal access tokens -> Tokens (classic). 
Tạo một Personal Access Token mới đặt tên là registry-token, tích chọn scopes: write:packages và read:packages. Sao chép mã token bắt đầu bằng ghp_.
Sử dụng terminal tại máy local, chạy lệnh đăng nhập vào Registry của GitHub:
docker login registry.gitlab.com -u <gitlab_username>

 

Khi hệ thống yêu cầu nhập Password, dán mã Access Token vừa sao chép vào và nhấn Enter.

 

Bước 3: Gắn tag Registry và đẩy Image lên GitHub

Thực hiện gắn tag image khớp chính xác với đường dẫn Registry của dự án (thay thế <repository_namespace> bằng username hoặc group của bạn trên GitLab):
docker tag user-service:1.0.0 registry.gitlab.com/<namespace>/user-service:1.0.0

 

Thực hiện đẩy image lên kho lưu trữ trực tuyến:
docker push registry.gitlab.com/<namespace>/user-service:1.0.0

 

Truy cập vào repository trên Github -> chuyển qua tab Packages để xác nhận image đã hiển thị tag 1.0.0.
 

Bước 4: Cấu hình và chạy thử Pipeline CI/CD

Tạo cấu trúc thư mục .github/workflows/ và cập nhật nội dung file ci.yml tại thư mục gốc của dự án user-service để kéo image về và chạy verify tự động:
name: Tích hợp Docker Registry

 

on:

  push:

    branches:

      - main

 

jobs:

  test_image:

    runs-on: [self-hosted, quickbite]

    permissions:

      packages: read

      contents: read

    steps:

      - name: Kiểm tra Container

        run: |

          # Đăng nhập tự động sử dụng thông tin định sẵn của Runner

          docker login ghcr.io -u ${{ github.actor }} -p ${{ secrets.GITHUB_TOKEN }}

         

          # Kéo image 1.0.0 đã được học viên push từ local về

          # Chuyển đổi tên kho chứa thành chữ thường

          IMAGE_TAG=$(echo "ghcr.io/${{ github.repository }}:1.0.0" | tr '[:upper:]' '[:lower:]')

          docker pull $IMAGE_TAG

         

          # Chạy container để kiểm tra verify ứng dụng

          docker run -d --name verify_app -p 8081:8081 $IMAGE_TAG

         

          # Chờ ứng dụng Spring Boot khởi động

          sleep 5

         

          # Kiểm tra danh sách container hoạt động

          docker ps

         

          # Dọn dẹp môi trường Runner sau khi kiểm tra xong

          docker rm -f verify_app || true

 

Thực hiện push file cấu hình lên Github để kích hoạt pipeline tự động.
Theo dõi log console của job trên tab Actions của GitHub, kiểm tra xem job có hoàn thành thành công và xác nhận tổng thời gian chạy (runtime) cực kỳ nhanh (thường dưới 30 giây).
 

5. Lưu ý lỗi và hiểu lầm thường gặp
Pipeline báo lỗi không tìm thấy image (Image not found): Xảy ra khi sinh viên push cấu hình file ci.yml lên GitHub kích hoạt workflow trước khi chạy các lệnh build và push image từ local lên Registry. Do Registry trống rỗng, lệnh docker pull của Runner sẽ thất bại ngay lập tức.
 Khắc phục: Bắt buộc phải hoàn tất việc build và push image từ local thành công lên Registry trước khi đẩy file workflow CI/CD.
Lỗi denied: unauthenticated khi chạy workflow: Xảy ra do học viên quên bổ sung phần phân quyền permissions: packages: read cho luồng làm việc khiến token mặc định không được truy cập Registry.
 Khắc phục: Kiểm tra file YAML và bổ sung permission.
Ứng dụng bị sập âm thầm (Silent Crash): Lệnh docker run chạy thành công và tạo container, nhưng ứng dụng Spring Boot bên trong bị crash ngay lập tức do thiếu biến cấu hình hoặc xung đột cổng. Lệnh docker ps chạy sau đó sẽ không hiển thị container hoạt động, nhưng script shell có thể không báo đỏ do lệnh kết thúc bằng docker ps thành công.
 Khắc phục: Kiểm tra log của container cục bộ tại local bằng lệnh docker logs <container_name> để debug lỗi trước khi đẩy lên registry.
 

6. Tài liệu tham khảo
Lệnh chạy Container của Docker CLI:

docker run command reference | Docker Documentation

Tài liệu hướng dẫn Pipeline Troubleshooting của GitLab:

Using workflow run logs | GitHub Documentation