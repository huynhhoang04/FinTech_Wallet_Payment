Bài đọc: Cấu trúc tệp cấu hình Nginx và các khối khai báo chính (Configuration Blocks)
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Mô tả được cơ chế hoạt động của tệp cấu hình chính nginx.conf và cách Nginx tự động nạp các tệp cấu hình con từ thư mục /etc/nginx/conf.d/.
Sử dụng thành thạo các khối khai báo server, location và các chỉ thị phổ biến bên trong để thiết lập máy chủ ảo.
Áp dụng các quy tắc so khớp đường dẫn URI (Prefix, Exact, Regular Expression) trong khối location.
2. Vấn đề thực tế
Khi bắt tay vào cấu hình Nginx, nhiều lập trình viên có thói quen chỉnh sửa trực tiếp tệp tin cấu hình gốc /etc/nginx/nginx.conf. Việc này cực kỳ nguy hiểm trong môi trường Production vì:

Dễ làm hỏng cấu hình toàn cục của hệ thống (như số lượng luồng xử lý, giới hạn file mở, logs hệ thống).
Khó quản lý khi hệ thống chạy nhiều dự án khác nhau (mọi cấu hình chồng chéo trong một file khổng lồ).
Gây gián đoạn toàn bộ dịch vụ nếu gõ sai một ký tự nhỏ trong file gốc.
Chuẩn vận hành thực tế là: Không bao giờ chỉnh sửa trực tiếp nginx.conf gốc. Tệp gốc này đã được thiết kế sẵn để tự động nạp (import) mọi tệp tin có đuôi .conf trong thư mục /etc/nginx/conf.d/ qua dòng lệnh:

include /etc/nginx/conf.d/*.conf;

Do đó, chúng ta chỉ cần tạo mới các file cấu hình độc lập (như quickbite.conf) đặt vào thư mục /etc/nginx/conf.d/. Để làm được điều này, chúng ta cần nắm vững cú pháp viết các block server và location độc lập.

3. Kiến thức cốt lõi
3.1. Cơ chế nạp cấu hình và phân cấp trong Nginx
Cấu trúc cấu hình Nginx được thiết kế theo hình cây phân cấp nghiêm ngặt:

Main Context: Tệp /etc/nginx/nginx.conf gốc. Chứa các thiết lập hệ thống toàn cục (worker_processes, pid).
Khối events: Nằm trong Main Context, định cấu hình hiệu năng mạng (worker_connections).
Khối http: Bao bọc toàn bộ cấu hình web. Bên trong khối http của file gốc sẽ có dòng include /etc/nginx/conf.d/*.conf;.
Khối server: Nằm bên trong file cấu hình con (như /etc/nginx/conf.d/quickbite.conf). Đại diện cho một Virtual Host (máy chủ ảo).
Khối location: Nằm trong server, dùng để định cấu hình chi tiết cho từng URI cụ thể.
[!NOTE] Do các file cấu hình con trong /etc/nginx/conf.d/ được tự động import thẳng vào bên trong khối http của file gốc, bạn không được phép khai báo các block http { ... } hay events { ... } trong các file con này.

3.2. Khối khai báo server (Virtual Host)
Khối server xác định cách thức Nginx tiếp nhận request dựa trên Port và Domain. Các chỉ thị cốt lõi bao gồm:

listen: Cổng lắng nghe (ví dụ: listen 80; cho HTTP, listen 443 ssl; cho HTTPS).
server_name: Tên miền hoặc địa chỉ IP mà khối server này phục vụ (ví dụ: api.quickbite.com hoặc _ để khớp với mọi request đổ vào IP VPS).
access_log & error_log: Đường dẫn ghi nhật ký truy cập và lỗi riêng cho server ảo này.
3.3. Khối khai báo location và Quy tắc so khớp đường dẫn (URI Matching)
Khối location bẻ hướng xử lý dữ liệu dựa trên đường dẫn URI. Cú pháp cơ bản:

location [modifier] uri {

    # Chỉ thị xử lý

}

Các modifier (ký tự bổ trợ) quyết định độ ưu tiên so khớp đường dẫn:

Khớp chính xác (=) - Độ ưu tiên cao nhất:
Cú pháp: location = /favicon.ico
Chỉ khớp khi URI giống hệt 100%. Tốc độ xử lý nhanh nhất.
Khớp tiền tố ưu tiên (^~) - Độ ưu tiên thứ hai:
Cú pháp: location ^~ /assets/
Nếu khớp tiền tố này, Nginx dừng tìm kiếm và áp dụng luôn cấu hình, không kiểm tra các Regex location khác.
Khớp Regex phân biệt hoa thường (~) hoặc không phân biệt hoa thường (~*):
Regex phân biệt hoa thường (~): location ~ \.(jpg|jpeg|png|gif)$ (Chỉ khớp .jpg, không khớp .JPG).
Regex KHÔNG phân biệt hoa thường (~*): location ~* \.json$ (Khớp cả .json và .JSON).
Dùng để xử lý các tệp tin tĩnh hoặc các mẫu đường dẫn động.
Khớp tiền tố mặc định (Không dùng ký tự modifier) - Độ ưu tiên thấp nhất:
Cú pháp: location /api
Khớp với bất kỳ đường dẫn nào bắt đầu bằng /api (ví dụ: /api/v1/users, /api/test).
PHẦN 4. DEMO VÀ THỰC HÀNH (THIẾT LẬP FILE CONFIG CON CHO DỰ ÁN QUICKBITE)
Chúng ta thực hiện tạo một file cấu hình custom hoàn toàn mới nằm trong thư mục /etc/nginx/conf.d/ để phục vụ riêng dự án QuickBite.

(Các bước cấu hình giả định trên VPS):

Tạo file cấu hình con có tên quickbite.conf trong thư mục /etc/nginx/conf.d/:
sudo nano /etc/nginx/conf.d/quickbite.conf

Soạn thảo nội dung file chỉ chứa khối server độc lập (tuyệt đối không bao bọc bởi http):
server {

   # Lắng nghe ở cổng 80 (HTTP tiêu chuẩn)

   listen 80;

 

   # Nhận diện mọi request truy cập qua IP của VPS

   server_name _;

 

   # Phân tách file log riêng để dễ theo dõi và debug

   access_log /var/log/nginx/quickbite_access.log;

   error_log /var/log/nginx/quickbite_error.log;

 

   # Location khớp tiền tố mặc định cho trang chủ

   location / {

      root /usr/share/nginx/html;

      index index.html index.htm;

   }

 

   # Location khớp chính xác trang lỗi

   error_page 500 502 503 504 /50x.html;

   location = /50x.html {

      root /usr/share/nginx/html;

   }

}

Lưu file và kiểm tra cú pháp cấu hình Nginx xem có bị lỗi dấu chấm phẩy ; hay đặt sai khối không:
sudo nginx -t

Kết quả mong đợi: Hệ thống báo syntax is ok và test is successful.