Bài đọc: Docker image, container và registry
1. Vấn đề thực tế (Làm sao để chuyển giao môi trường mà không bị lệch pha)
Ở Lesson 1, chúng ta đã hiểu lý do vì sao Container (Docker) ra đời để giải quyết bài toán cô lập môi trường chạy (như chạy song song user-service Java 17 và restaurant-service Java 21) mà không làm lãng phí RAM của server như Máy ảo (VM). 

Nhưng một câu hỏi thực tế lớn hơn xuất hiện: Làm thế nào để chúng ta đóng gói toàn bộ môi trường chạy phức tạp đó và chuyển giao nó từ máy cá nhân lên server Staging/Production một cách đồng nhất?

Nếu chúng ta vẫn làm theo cách cũ ở Session 1:

Bạn sửa code user-service ở máy local.
Bạn compile ra file JAR rồi dùng scp đẩy file JAR trần trụi lên server.
Trên server Staging, bạn lại phải tự cài đặt JDK 17, tự cấu hình các biến môi trường múi giờ bằng tay.
Nỗi đau chưa chấm dứt: Quy trình này vẫn để lộ lỗ hổng lớn về lệch pha môi trường (Environment Drift). Chỉ cần phiên bản Java trên server Staging bị cấu hình sai, hoặc múi giờ hệ thống của server lệch so với máy của bạn, ứng dụng sẽ crash ngay lập tức. Câu nói kinh điển "Ơ kìa, code chạy ngon trên máy em mà!" (It works on my machine!) lại tiếp tục vang lên.

Để giải quyết triệt để vấn đề chuyển giao, Docker không chỉ chạy các tiến trình cô lập, mà nó cung cấp một giải pháp đóng gói toàn diện. Chúng ta cần một công cụ để đóng băng (freeze) toàn bộ môi trường và phân phối nó tự động. Đó chính là lúc bạn cần làm quen với bộ ba thực thể: Docker Image (Bản đóng băng), Docker Container (Tiến trình giải nén chạy thực tế), và Docker Registry (Nhà kho phân phối).

2. Kiến thức cốt lõi (Image, Container & Registry)
Hệ sinh thái Docker xoay quanh ba khái niệm cốt lõi đại diện cho ba giai đoạn của vòng đời ứng dụng:



2.1. Docker Image (Bản thiết kế bất biến - Giống như Class trong Java vậy)
 Định nghĩa: Docker Image là một khuôn mẫu đóng gói ở trạng thái chỉ đọc (Read-only). Nó chứa toàn bộ mã nguồn user-service, runtime Java (JRE), các biến môi trường cấu hình mặc định, và hệ điều hành Linux tối giản cần thiết để chạy ứng dụng.
 Tính chất cốt lõi:
Bất biến (Immutable): Một khi Image đã được build thành công, không ai có thể sửa đổi nội dung bên trong nó. Muốn sửa cấu hình mặc định hoặc code, bạn bắt buộc phải build một Image mới.
Cấu trúc phân lớp (Layers): Docker Image được cấu tạo từ nhiều lớp file system xếp chồng lên nhau. 
Ví dụ: Lớp đáy là OS Ubuntu tối giản, lớp tiếp theo là JDK 17, lớp trên cùng là code restaurant-service.jar. 

Cơ chế này giúp tái sử dụng tài nguyên cực kỳ tốt. Nếu bạn build dịch vụ restaurant-service chạy Java 17, Docker sẽ tái sử dụng lại các layer OS và JDK 17 có sẵn ở máy, chỉ tải thêm layer chứa file JAR mới của restaurant-service.
Bản công thức Dockerfile: Để tự mình tạo ra (build) một Docker Image cho dự án cá nhân, lập trình viên sẽ viết các bước chỉ dẫn trong một file văn bản trần tên là Dockerfile. File này định nghĩa các bước như: lấy hệ điều hành nào làm gốc, cài JDK nào, copy file JAR vào đâu và chạy câu lệnh gì khi khởi động. (Chúng ta sẽ được học cách viết Dockerfile chi tiết ở các Session sau).
2.2. Docker Container (Thực thể sống động - Giống như biến trong Heap vậy)
 Định nghĩa: Docker Container là một thực thể sống (Instance) được khởi tạo từ Docker Image. Nó chính là tiến trình ứng dụng Java thực sự đang chạy trên RAM và CPU của máy chủ.
 Cơ chế hoạt động: Khi bạn ra lệnh chạy một container từ Image, Docker Engine sẽ lấy Image đó (trạng thái chỉ đọc) và phủ lên trên cùng một lớp ghi tạm thời gọi là Writable Layer (hoặc Container Layer).
 Tính chất:
Mọi thao tác tạo file, ghi log, ghi cấu hình khi container đang chạy đều ghi vào lớp Writable Layer này.
Nếu bạn xóa Container, lớp Writable Layer này sẽ bị hủy bỏ hoàn toàn. Image gốc nằm bên dưới vẫn toàn vẹn và sạch sẽ.
Từ một Image duy nhất, bạn có thể khởi chạy hàng chục Container user-service chạy song song trên các cổng mạng khác nhau.
2.3. Docker Registry (Nhà kho chứa hàng)
 Định nghĩa: Là kho lưu trữ tập trung dùng để quản lý và chia sẻ các Docker Image.
 Các loại Registry:
Public Registry: Kho công cộng như Docker Hub (mặc định), nơi chứa hàng triệu image chính thức của các công nghệ (PostgreSQL, OpenJDK, Nginx, v.v.).
Private Registry: Kho lưu trữ riêng tư của doanh nghiệp (như GitLab Container Registry, AWS ECR) để lưu trữ bảo mật mã nguồn đóng của dự án QuickBite.
3. Sơ đồ vòng đời phát hành ứng dụng khép kín 
Quy trình phát hành ứng dụng chuẩn DevOps bằng Docker diễn ra như sau:



Các lệnh thực hành minh họa vòng đời mẫu:

Để tải một bản phân phối Java JRE chính thức từ Docker Hub về máy cá nhân, bạn mở Terminal và chạy:

# 1. Tải (pull) image JRE 17 gọn nhẹ (dùng alpine linux) từ Docker Hub

docker pull eclipse-temurin:17-jre-alpine

 

# 2. Xem danh sách các Image đang lưu ở bộ nhớ máy local

docker images

# Output mong đợi: Hiển thị dòng eclipse-temurin với tag 17-jre-alpine, kích thước chỉ khoảng ~100MB

 

# 3. Khởi chạy thử một container kiểm tra phiên bản Java bên trong

docker run --rm eclipse-temurin:17-jre-alpine java -version

# Output mong đợi: Hiển thị phiên bản java "17.x.x" thành công và tự động xóa container khi chạy xong (--rm)

4. Lưu ý quan trọng và hiểu lầm thường gặp
 Hiểu lầm thường gặp: Khi đang chạy container user-service, nếu tôi chui vào trong container sửa đổi một file cấu hình hoặc tạo thêm một thư mục, thì Docker Image sinh ra container đó cũng tự động được cập nhật theo.

 Sự thật: Docker Image là bất biến (Immutable). Mọi sửa đổi của bạn chỉ tồn tại trên lớp Writable Layer của riêng container đó. Khi container bị xóa (docker rm), toàn bộ file sửa đổi sẽ biến mất mãi mãi. Để lưu giữ các thay đổi đó, bạn bắt buộc phải sửa file cấu hình ở ngoài máy host rồi build lại một Image mới.

5. Tài liệu tham khảo
Kiến trúc và các khái niệm nền tảng của Docker:
Docker Architecture and Concepts - Docker Docs

Cách hoạt động của hệ thống file phân lớp và Writable Layer:
How Images and Containers work with storage drivers - Docker Docs

Hướng dẫn sử dụng Docker Registry và Docker Hub:
Docker Registry Official Guide - Docker Docs

 