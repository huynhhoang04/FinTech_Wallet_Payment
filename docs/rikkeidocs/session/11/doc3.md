Bài đọc: Triển khai Nginx làm Reverse Proxy chuyển tiếp yêu cầu đến API Gateway
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Cấu hình thành công chỉ thị proxy_pass trong tệp cấu hình con /etc/nginx/conf.d/quickbite.conf để chuyển hướng traffic từ cổng 80 vào cổng 8080 của API Gateway.
Thiết lập các HTTP Headers (Host, X-Real-IP, X-Forwarded-For, X-Forwarded-Proto) để bảo toàn thông tin của request ban đầu.
Áp dụng cơ chế nạp nóng cấu hình (Zero-Downtime Hot Reload) để cập nhật cấu hình Nginx mà không làm gián đoạn dịch vụ của khách hàng.
 

2. Vấn đề thực tế
Trong kiến trúc của dự án QuickBite, cổng vào trung tâm điều phối toàn bộ các microservices là dịch vụ API Gateway (api-gateway). Trên môi trường Production, container api-gateway chạy trong Docker và map cổng ra ngoài host:

ports:

  - "8080:8080"

Lúc này, API Gateway lắng nghe trực tiếp trên cổng 8080 của máy chủ VPS. Chúng ta không muốn người dùng khi đặt đồ ăn phải gõ thêm cổng :8080 trên đường dẫn URL (nhìn rất thiếu chuyên nghiệp). Hơn thế nữa, việc mở cổng 8080 trực tiếp ra Internet là một nguy cơ bảo mật.

Chúng ta cần đặt Nginx ở cổng 80 (cổng HTTP mặc định của thế giới Web), tiếp nhận toàn bộ các request gửi tới và chuyển tiếp (proxy) chúng vào cổng 8080 của API Gateway trên localhost (127.0.0.1). Việc này vừa giúp che giấu cổng thực tế, vừa giúp hệ thống có một cổng vào chuẩn hóa duy nhất.

 

3. Kiến thức cốt lõi
3.1. Chỉ thị proxy_pass
Chỉ thị proxy_pass là cốt lõi của tính năng Reverse Proxy trong Nginx. Nó hướng dẫn Nginx chuyển tiếp request nhận được ở location hiện tại tới một máy chủ đích khác (Upstream Server).

Cú pháp:
location /api {

    proxy_pass http://127.0.0.1:8080;

}

Cơ chế hoạt động: Khi Client gửi request tới http://<vps_ip>/api/v1/users, Nginx khớp với location /api, sau đó thay thế phần host bằng http://127.0.0.1:8080 và gửi tiếp request tới API Gateway.
3.2. Bảo toàn thông tin Request qua HTTP Headers
Khi Nginx đứng làm trung gian nhận request rồi gửi lại một request mới tới API Gateway, địa chỉ IP của client gửi tới API Gateway lúc này sẽ bị đổi thành địa chỉ IP nội bộ của Nginx (127.0.0.1). Điều này khiến ứng dụng Spring Boot phía sau không thể nhận diện được IP thực tế của khách hàng (ví dụ: phục vụ ghi log, chặn spam IP, hoặc định vị địa lý).

Để giải quyết vấn đề này, Nginx cung cấp chỉ thị proxy_set_header để đính kèm thêm các tiêu đề HTTP (Headers) trước khi chuyển tiếp request:

proxy_set_header Host $host: Bảo toàn tên miền ban đầu của request.
proxy_set_header X-Real-IP $remote_addr: Lưu IP thực tế của Client kết nối tới Nginx.
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for: Ghi vết chuỗi IP mà request đã đi qua (hữu ích khi đi qua nhiều proxy).
proxy_set_header X-Forwarded-Proto $scheme: Lưu giao thức ban đầu (HTTP hay HTTPS).
3.3. Zero-Downtime Hot Reload
Nginx nổi tiếng với khả năng cập nhật cấu hình mà không làm gián đoạn bất kỳ request nào đang xử lý.

Lệnh thực thi: sudo systemctl reload nginx (hoặc nginx -s reload).
Cơ chế: Tiến trình cha (Master process) sẽ đọc file cấu hình mới. Nếu cấu hình hợp lệ, nó sẽ khởi tạo các tiến trình con mới (Worker processes) chạy với cấu hình mới để tiếp nhận các request mới. Đồng thời, nó ra lệnh cho các Worker cũ xử lý nốt các request dở dang rồi tự hủy một cách nhẹ nhàng.
4. Demo và thực hành (Cấu hình Proxy sang API Gateway trên VPS)
Học viên thực hiện cấu hình Reverse Proxy cho dự án QuickBite trên VPS theo các bước sau:

4.1. Biên soạn tệp cấu hình con /etc/nginx/conf.d/quickbite.conf
Đăng nhập vào VPS bằng Bitvise SSH Client, mở Terminal và tạo file cấu hình con:
sudo nano /etc/nginx/conf.d/quickbite.conf

Nhập nội dung cấu hình sau (chuyển tiếp toàn bộ request bắt đầu bằng /api vào API Gateway đang chạy ở cổng 8080 của máy host):
server {

    listen 80;

    server_name _;

 

    # Ghi log riêng cho QuickBite

    access_log /var/log/nginx/quickbite_access.log;

    error_log /var/log/nginx/quickbite_error.log;

 

    # Cấu hình chuyển tiếp request tới API Gateway

    location /api {

        proxy_pass http://127.0.0.1:8080;

 

        # Đính kèm HTTP Headers để bảo toàn thông tin request gốc

        proxy_set_header Host $host;

        proxy_set_header X-Real-IP $remote_addr;

        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        proxy_set_header X-Forwarded-Proto $scheme;

    }

}

Nhấn Ctrl + O, Enter để lưu file và Ctrl + X để thoát nano.
4.2. Kiểm tra cú pháp và kích hoạt cấu hình
Luôn chạy lệnh kiểm tra cú pháp trước khi chạy reload:
sudo nginx -t

Đảm bảo kết quả trả về: nginx: configuration file /etc/nginx/nginx.conf test is successful.
Reload dịch vụ Nginx để áp dụng cấu hình mới:
sudo systemctl reload nginx

4.3. Kiểm chứng kết nối từ Client local
Mở Postman hoặc terminal máy cá nhân của bạn.
Gửi request HTTP GET tới IP VPS công khai ở cổng 80 (không cần gõ cổng 8080 nữa):
curl http://<vps_public_ip>/api/v1/restaurants

Kết quả mong đợi: Nhận về mã trạng thái 200 OK cùng dữ liệu JSON danh sách nhà hàng từ dịch vụ backend.
Trên VPS, kiểm tra file access log của Nginx để xem log request vừa thực hiện:
tail -n 10 /var/log/nginx/quickbite_access.log