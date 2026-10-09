Bài đọc: Docker Compose và khái niệm hệ thống nhiều container
 

1. Vấn đề thực tế (nỗi đau quản lý thủ công bằng lệnh docker)
Hãy tưởng tượng bạn đang tiếp quản dự án QuickBite ở môi trường phát triển local. Lúc này, bạn đã giải quyết được vấn đề đóng gói nhờ tệp tin Dockerfile ở bài học trước. Bạn đã có file Dockerfile cho cả user-service và restaurant-service.

 

Tuy nhiên, để khởi chạy toàn bộ hệ thống gồm 3 thành phần (Database, User Service, Restaurant Service), bạn vẫn phải gõ tuần tự một loạt các lệnh phức tạp sau:

# 1. Khởi chạy Database

docker run -d --name quickbite-db -e POSTGRES_PASSWORD=secret postgres:15-alpine

 

# 2. Tìm địa chỉ IP động của container database vừa chạy

docker inspect quickbite-db

# Ví dụ tìm được IP: 172.17.0.2

 

# 3. Khởi chạy User Service bằng image đã build từ bài học trước

docker run -d --name user-service -p 8081:8081 -e DB_HOST=172.17.0.2 -e DB_PORT=5432 -e DB_NAME=quickbite_user_db -e DB_USERNAME=quickbite_user -e DB_PASSWORD=quickbite_user quickbite-user-service:v1

 

# 4. Khởi chạy Restaurant Service bằng image đã build từ bài học trước

docker run -d --name restaurant-service -p 8082:8082 -e DB_HOST=172.17.0.2 -e DB_PORT=5432 -e DB_NAME=quickbite_restaurant_db -e DB_USERNAME=quickbite_restaurant -e DB_PASSWORD=quickbite_restaurant quickbite-restaurant-service:v1

Nỗi đau bắt đầu: 
Quy trình nhiều bước thủ công: Mỗi lần khởi động cụm, bạn phải chạy tuần tự các lệnh run dài dòng.
Tra cứu IP động phiền phức: Vẫn phải gõ lệnh inspect lấy IP động của database để điền vào chuỗi kết nối của 2 service Java.
IP biến động (IP Drift): Chỉ cần container database khởi động lại và nhận IP khác (ví dụ: 172.17.0.3), cả hai backend sẽ mất kết nối và crash lập tức. Bạn lại phải lặp lại chu kỳ gõ lệnh run backend với IP mới.
Để giải phóng lập trình viên khỏi gánh nặng điều phối và chạy thủ công đống container này, Docker cung cấp một công cụ quản lý cấu hình tập trung vô cùng mạnh mẽ: Docker Compose.

 

2. Kiến thức cốt lõi (Hệ thống nhiều Container & Docker Compose)
2.1. Khái niệm hệ thống nhiều container (Multi-container System)
Trong kiến trúc phần mềm hiện đại, đặc biệt là Microservices, ứng dụng không còn là một khối lớn duy nhất (Monolith) chạy trong một môi trường đơn lẻ. Thay vào đó, nó được chia nhỏ thành nhiều thành phần độc lập (dịch vụ), mỗi thành phần đảm nhận một nhiệm vụ chuyên biệt.

Một Hệ thống nhiều container (Multi-container System) là một mô hình thiết kế và vận hành trong đó mỗi dịch vụ của hệ thống được đóng gói và chạy trong một container độc lập. Ví dụ, trong hệ thống QuickBite:

Container Database (quickbite-db): Chạy PostgreSQL 15 để lưu trữ dữ liệu chung.
Container Service 1 (user-service): Chạy Java 17 để xử lý thông tin người dùng.
Container Service 2 (restaurant-service): Chạy Java 21 để quản lý thông tin nhà hàng và thực đơn.
Container Web Gateway / Reverse Proxy (ở các Session sau): Nhận request từ Client và phân phối đến các service tương ứng.
Tại sao chúng ta phải chia nhỏ hệ thống thành nhiều container độc lập thay vì gom tất cả vào một container duy nhất?

Sự khác biệt về môi trường công nghệ (Technology Diversity): user-service dùng Java 17, restaurant-service dùng Java 21, còn Database viết bằng C và cần hệ điều hành tối ưu riêng. Việc cài đặt chung tất cả công nghệ này vào một container duy nhất sẽ làm kích thước image phình to khổng lồ và cực kỳ khó quản lý xung đột thư viện.
Khả năng mở rộng độc lập (Independent Scaling): Vào các giờ cao điểm, lượng người dùng tìm kiếm món ăn tăng vọt, chúng ta chỉ cần nhân bản (scale up) thêm các container restaurant-service để gánh tải mà không cần phải nhân bản database hay user-service, giúp tối ưu hóa tài nguyên phần cứng.
Mức độ cô lập lỗi (Fault Isolation): Nếu container restaurant-service gặp sự cố tràn bộ nhớ và crash, container user-service vẫn hoạt động bình thường, giúp hệ thống không bị sập toàn bộ.
Tuy nhiên, việc chia nhỏ này cũng đặt ra thách thức lớn: Làm thế nào để phối hợp, thiết lập mạng liên kết và quản lý vòng đời của hàng chục container này một cách đồng bộ? Đó chính là lý do Docker Compose ra đời.

2.2. Docker Compose là gì?
Docker Compose là một công cụ dùng để định nghĩa và chạy các ứng dụng Docker đa container (Multi-container Docker Applications). 

Thay vì quản lý các container bằng các câu lệnh động CLI đơn lẻ (Imperative Style - Phong cách Mệnh lệnh), Docker Compose chuyển sang phong cách Declarative (Mô tả trạng thái): Bạn chỉ cần viết tất cả cấu hình mong muốn (tên container, port, network, volume, biến môi trường) vào một tệp tin cấu hình duy nhất có tên là docker-compose.yml (dạng ngôn ngữ YAML). Docker Compose sẽ đọc tệp tin này và tự động dựng, quản lý toàn bộ hệ thống giúp bạn.



2.3. Quy trình 3 bước làm việc tiêu chuẩn với Docker Compose
Quy trình phát triển và vận hành ứng dụng bằng Docker Compose được tóm tắt qua 3 bước cốt lõi:

Đóng gói (Define): Viết Dockerfile cho từng service của bạn để định nghĩa môi trường chạy tĩnh (ví dụ: Spring Boot code + JDK).
Khai báo (Declare): Viết tệp tin docker-compose.yml khai báo các service, các port cần expose, volume lưu dữ liệu, mạng kết nối và thứ tự ưu tiên khởi chạy.
Vận hành (Run): Chỉ cần chạy một câu lệnh duy nhất: docker compose up. Docker Compose sẽ tự động kéo các image cần thiết, build các image nội bộ, tạo mạng và khởi chạy toàn bộ các dịch vụ đồng thời.
3. Thực hành cơ bản: Kiểm tra Docker compose và Up thử container
3.1. Kiểm tra phiên bản Docker Compose

Hiện nay, Docker Compose v2 đã được tích hợp trực tiếp làm plugin của Docker CLI. Do đó, bạn không còn phải dùng lệnh docker-compose (có dấu gạch ngang của v1 cũ) nữa mà chuyển sang sử dụng trực tiếp lệnh:

docker compose version

Kết quả mong đợi: Terminal trả về phiên bản Docker Compose đang hoạt động (ví dụ: Docker Compose version v2.20.2 hoặc mới hơn).
Xử lý sự cố (Nếu gặp lỗi "docker: 'compose' is not a docker command" hoặc báo thiếu command):
Điều này có nghĩa là bạn mới chỉ cài đặt lõi Docker Engine cơ bản mà quên chưa cài đặt các plugin mở rộng đi kèm. Hãy mở terminal trên WSL 2 / Linux Ubuntu và chạy lệnh sau để bổ sung đầy đủ:

sudo apt-get update

sudo apt-get install -y docker-buildx-plugin docker-compose-plugin

 

3.2. Viết và khởi chạy tệp Compose tối giản
Hãy tạo một thư mục trống và thực hành viết file compose đầu tiên để kiểm tra hoạt động:

 

A. Tạo file docker-compose.yml:

version: '3.8'

 

services:

   java-tester:

      image: eclipse-temurin:17-jre-alpine

      command: java -version

B. Khởi chạy file Compose bằng lệnh:

docker compose up

C. Kết quả mong đợi: 

Docker Compose sẽ tự động kéo image eclipse-temurin:17-jre-alpine (nếu chưa có ở local), khởi chạy một container, in thông tin phiên bản Java 17 ra console rồi tự động dừng lại.

D. Dọn dẹp tài nguyên vừa tạo:

docker compose down

4. Hiểu lầm thường gặp (Docker compose vs Kubernetes) 
Hiểu lầm thường gặp: Docker Compose có thể dùng để deploy và scale ứng dụng Microservices lớn trên môi trường Production thực tế với hàng trăm máy chủ vật lý.
Sự thật: Docker Compose chỉ được thiết kế cho môi trường phát triển local (Development), Staging hoặc triển khai Production quy mô nhỏ chạy trên một máy chủ duy nhất (Single Host). 
Docker Compose không thể tự động giám sát sức khỏe container và khởi động lại trên máy chủ khác khi máy chủ vật lý bị sập phần cứng (lỗi Node).
Khi hệ thống mở rộng lên hàng trăm server, cần tính năng tự động co giãn (Auto Scaling), tự phục hồi (Self-healing) và định tuyến giao thông phức tạp, chúng ta phải chuyển sang sử dụng các bộ điều phối (Orchestrators) như Docker Swarm hoặc Kubernetes (K8s).
6. Tài liệu tham khảo
Tổng quan về Docker Compose từ trang chủ:
Docker Compose Overview - Docker Docs

Hướng dẫn cài đặt Docker Compose trên các môi trường:
Install Docker Compose - Docker Docs