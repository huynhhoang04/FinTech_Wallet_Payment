Bài đọc: Quy trình Build Docker image trong pipeline CI/CD
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được quy trình tự động hóa đóng gói Docker image trong pipeline CI/CD từ file JAR được biên dịch sẵn.
Áp dụng cơ chế truyền tải artifacts giữa các stage trong GitLab CI.
Phân biệt được mô hình Docker-in-Docker (DinD) và Docker-outside-of-Docker (DooD) chia sẻ Docker socket.
Cấu hình pipeline CI/CD tối giản giao tiếp trực tiếp với Docker Daemon của máy host.
 

2. Vấn đề thực tế (Từ thủ công sang tự động)
Trong các session trước (State 0), lập trình viên thực hiện quy trình đóng gói hoàn toàn thủ công ở máy phát triển cá nhân:

Chạy lệnh ./gradlew bootJar để biên dịch mã nguồn Java ra file JAR.
Viết Dockerfile đơn tầng (Single-stage Dockerfile) để sao chép file JAR từ thư mục local vào container base.
Chạy lệnh docker build để đóng gói thành Docker image hoàn thiện.
Hạn chế của quy trình thủ công:

Dễ phát sinh sai sót do thao tác của con người.
Môi trường build ở local không nhất quán (lệch phiên bản Java, hệ điều hành khác biệt).
Không thể tích hợp vào luồng tích hợp liên tục (CI/CD) của dự án.
 

Để giải quyết vấn đề này, chúng ta cần tự động hóa toàn bộ quy trình trên trong pipeline CI/CD của GitHub Actions (State 1: CI/CD Build với 2 Jobs) sử dụng hệ thống Self-hosted Runner đã cấu hình ở các bài học trước.

 

3. Kiến thức cốt lõi
3.1. Quy trình Pipeline CI/CD 2 Jobs
Để tự động hóa đóng gói, pipeline CI/CD được chia thành 2 công việc (jobs) chạy tuần tự thông qua từ khóa needs:

Job 1 (build_jar): Biên dịch mã nguồn Java thành file JAR thương mại sử dụng môi trường JDK thích hợp và lưu trữ kết quả đầu ra dưới dạng artifacts thông qua Action actions/upload-artifact.
Job 2 (build_image): Runner tải file JAR artifacts về thư mục workspace thông qua Action actions/download-artifact, và thực thi lệnh docker build dựa trên Dockerfile đơn tầng để đóng gói thành image.
 



 

3.2. Giải pháp chạy Docker trong CI/CD: DinD vs DooD (Docker Socket Sharing)
Để chạy các lệnh như docker build bên trong môi trường CI/CD, chúng ta có hai mô hình chính:

Docker-in-Docker (DinD): Chạy một Docker Daemon phụ độc lập bên trong container thực thi. Thường yêu cầu quyền privileged cực kỳ cao.
Đặc điểm: Thường gặp ở các hệ thống yêu cầu môi trường cô lập tuyệt đối, tuy nhiên tiêu tốn nhiều tài nguyên khởi tạo.
Nhược điểm: Tốn thời gian khởi tạo daemon phụ, không tận dụng được cache image của máy host.
Docker-outside-of-Docker (DooD) / Chia sẻ Docker Socket (Lựa chọn của giáo trình):
Đặc điểm: Mount trực tiếp Docker socket vật lý của máy host (/var/run/docker.sock) vào container Runner thông qua cấu hình volumes của Docker Compose (đã thiết lập khi dựng Self-hosted Runner ở Session 07).
Lợi ích vượt trội: Các lệnh Docker trong tiến trình CI/CD sẽ được chuyển tiếp và thực thi trực tiếp trên Docker daemon của chính máy host. Loại bỏ sự phức tạp, đồng thời tận dụng trực tiếp bộ nhớ cache image và layer có sẵn trên máy host để tăng tốc độ build tối đa.
 

3.3. Dockerfile đơn tầng (Single-stage)
Dockerfile được sử dụng ở bài học này là Dockerfile đơn tầng chuẩn hóa (tương tự như Session 04), thực hiện sao chép file JAR đã có sẵn trong thư mục làm việc vào container:

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

 

4. Demo và thực hành (Cấu hình Pipeline CI/CD với Self-Hosted Runner)
Học viên thực hiện cấu hình tệp tin Workflow và Dockerfile tại thư mục gốc của dự án user-service để tự động hóa quy trình đóng gói.

 

4.1. Biên soạn tệp tin Dockerfile
Tạo file Dockerfile (nếu chưa có) tại thư mục gốc của dự án user-service (user-service/Dockerfile):

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]

 

4.2. Biên soạn tệp cấu hình Workflow .github/workflows/ci.yml
Tạo cấu trúc thư mục .github/workflows/ và cập nhật file ci.yml tại thư mục gốc của dự án user-service:

name: Tự động hoá đóng gói Docker Image

 

on:

  push:

    branches:

      - main

 

env:

  IMAGE_NAME: "user-service"

 

jobs:

  # Job biên dịch code ra file JAR

  build_jar:

    runs-on: [self-hosted, quickbite]

    steps:

      - name: Lấy mã nguồn

        uses: actions/checkout@v5

 

      - name: Thiết lập môi trường JDK 17

        uses: actions/setup-java@v5

        with:

          java-version: '17'

          distribution: 'temurin'

          cache: 'gradle'

 

      - name: Cấp quyền thực thi Gradle

        run: chmod +x ./gradlew

 

      - name: Biên dịch mã nguồn

        run: ./gradlew bootJar

 

      - name: Upload Artifact

        uses: actions/upload-artifact@v4

        with:

          name: app-jar

          path: build/libs/*.jar

          retention-days: 1

 

  # Job build Docker image sử dụng file JAR artifacts từ job trước

  build_image:

    needs: build_jar

    runs-on: [self-hosted, quickbite]

    steps:

      - name: Lấy mã nguồn

        uses: actions/checkout@v5

 

      - name: Tải file JAR Artifact

        uses: actions/download-artifact@v4

        with:

          name: app-jar

          path: build/libs/

 

      - name: Đóng gói Docker Image

        # Biên dịch Docker image từ Dockerfile đơn tầng trực tiếp trên daemon của máy host

        run: docker build -t ${{ env.IMAGE_NAME }}:latest .

 

4.3. Kết quả mong đợi
Khi push cấu hình lên GitHub, Workflow sẽ khởi chạy tuần tự qua 2 Jobs (build_jar -> build_image). Log của job build_image hiển thị quá trình copy file JAR thành công và hoàn tất lệnh build image.



5. Lưu ý, lỗi sai và hiểu lầm thường gặp
Thiếu cấu hình chia sẻ Docker Socket: Học viên khi tự host Runner thường quên cấu hình socket vật lý /var/run/docker.sock trong volumes của dịch vụ Runner (Docker Compose hoặc lệnh đăng ký). Lỗi này khiến job CI/CD báo lỗi:Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?
Khắc phục: Đảm bảo cấu hình volumes của Runner chứa khai báo "/var/run/docker.sock:/var/run/docker.sock".
Hiểu lầm về việc bắt buộc phải dùng `services`: Sinh viên đọc tài liệu trực tuyến của GitLab thấy luôn yêu cầu khai báo services: - docker:dind nên tự ý thêm vào. Với thiết kế sử dụng Local Runner của giáo trình, việc khai báo dind là dư thừa và làm chậm thời gian chạy do phải khởi tạo thêm container phụ.
 

6. Tài liệu tham khảo
Sử dụng Docker build trong GitLab CI/CD:

Storing workflow data as artifacts | GitHub Documentation

Cấu hình chia sẻ Docker Socket cho Runner:

Hosting your own runners | GitHub Documentation