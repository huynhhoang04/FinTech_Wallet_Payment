Bài đọc: Các lệnh Docker cơ bản trong vòng đời container
1. Vấn đề cốt lõi (Quản lý dịch vụ bằng Docker)
Tech Lead yêu cầu bạn phải cô lập database PostgreSQL để bảo mật, nhưng user-service thì lại đang chạy ở trên máy Host

Để chạy dịch vụ cơ sở dữ liệu PostgreSQL cho ứng dụng user-service của QuickBite trong container, chúng ta không chỉ cần tải image về mà phải biết cách kiểm soát trạng thái hoạt động của nó:

Tránh khóa Terminal: Mặc định, lệnh docker run sẽ chạy container ở chế độ tiền cảnh (foreground), gắn toàn bộ luồng log ra màn hình Terminal hiện tại. Nếu bạn tắt Terminal hoặc gõ Ctrl+C, container sẽ dừng hoạt động ngay lập tức.
Cho phép truy cập: Container chạy trong mạng cô lập. Nếu không có cơ chế "mở cổng", ứng dụng Spring Boot user-service từ máy host sẽ không thể kết nối tới cơ sở dữ liệu này.
 

Bài học này đi thẳng vào các câu lệnh thực chiến để giải quyết các vấn đề trên.

2. Kiến thức cốt lõi
2.1. Bộ bốn tham số sống còn khi chạy Container
Khi thực hiện lệnh docker run, bạn cần làm chủ 4 tham số sau:

-d (Detached mode): Chạy container dưới nền (background). Giải phóng Terminal để bạn tiếp tục nhập các câu lệnh khác. Container sẽ chạy độc lập với trạng thái đóng/mở của cửa sổ Terminal.
-p host_port:container_port (Port Mapping): Ánh xạ cổng mạng của máy host vào cổng mạng nội bộ của container. Ví dụ: -p 5432:5432 giúp chuyển hướng mọi yêu cầu truy cập cổng 5432 của máy host vào cổng 5432 của PostgreSQL trong container.
-e KEY=VALUE (Environment Variable): Truyền biến môi trường vào bên trong container. Các tiến trình (như Database Postgres) sẽ đọc các biến này để cấu hình thông số hoạt động khi khởi chạy (ví dụ: -e POSTGRES_PASSWORD=secret để đặt mật khẩu quản trị).
 --name [tên_định_danh]: Đặt tên tường minh cho container (ví dụ: quickbite-db) để dễ dàng quản lý thay vì để Docker tự sinh tên ngẫu nhiên.
 

2.2. Vòng đời của Container
Tiến trình của container trải qua 4 trạng thái chính:



 Created (Đã tạo): Container được khởi tạo từ Image, các lớp cấu hình đã sẵn sàng nhưng tiến trình chính bên trong chưa thực sự chạy.
 Running (Đang hoạt động): Tiến trình chính của ứng dụng bên trong container được kích hoạt (ví dụ: PostgreSQL đang lắng nghe cổng mạng). Container tiêu thụ tài nguyên RAM/CPU của máy host.
 Stopped (Exited): Tiến trình bên trong container đã tắt. Container giải phóng hoàn toàn CPU và RAM, nhưng dữ liệu ghi tạm thời và file cấu hình vẫn được lưu trên ổ đĩa của máy host.
 Destroyed: Container bị xóa bỏ hoàn toàn khỏi hệ thống, giải phóng bộ nhớ ổ đĩa.
 

3. Thực hành cốt lõi
Hãy mở Terminal và thực hiện lần lượt các lệnh quản lý container quickbite-db (dùng image PostgreSQL tối giản alpine):

 

# 1. Khởi chạy container PostgreSQL chạy ngầm, mở cổng 5432, đặt tên rõ ràng

docker run -d --name quickbite-db -p 5432:5432 -e POSTGRES_PASSWORD=secret postgres:15-alpine

# 2. Liệt kê các container đang hoạt động để kiểm tra trạng thái

docker ps

# Output hiển thị rõ trạng thái Up và thông số Port mapping.

# 3. Dừng container database

docker stop quickbite-db

# 4. Liệt kê tất cả các container (bao gồm cả container đã dừng)

docker ps -a

# Status lúc này hiển thị Exited (0).

# 5. Khởi động lại container cũ đã dừng mà không cần tạo mới

docker start quickbite-db

# 6. Xóa bỏ hoàn toàn container khỏi hệ thống

# Lưu ý: Phải stop container trước khi rm, hoặc dùng cờ -f để xóa cưỡng chế

docker stop quickbite-db

docker rm quickbite-db

4. Hiểu lầm thường gặp
 Hiểu sai: Lệnh docker stop sẽ xóa sạch dữ liệu và container.
 Đính chính: Lệnh docker stop chỉ tạm dừng tiến trình hệ thống giống như việc bạn tắt một ứng dụng trên máy. Toàn bộ dữ liệu ghi trên lớp Writable Layer của container (ví dụ: các bảng dữ liệu database đã tạo) vẫn nằm an toàn trên ổ đĩa cho đến khi bạn chạy lệnh docker rm.
5. Tài liệu tham khảo
Docker Run Reference - Docker Docs

Docker Container CLI commands - Docker Docs