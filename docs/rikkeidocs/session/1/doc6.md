Bài đọc: Quản lý quyền và các lệnh mạng
1. Vấn đề thực tế (Sự cố khởi chạy và kết nối mạng)
Khi bạn chuyển giao file user-service-0.0.1.jar lên máy chủ Linux, hai sự cố phổ biến nhất thường khiến lập trình viên mất hàng giờ để loay hoay:

1. Lỗi phân quyền (Permission Denied):*Bạn gõ lệnh chạy ứng dụng nhưng Linux báo lỗi chặn quyền truy cập. Đó là do file JAR hoặc thư mục chứa nó chưa được phân quyền đọc/thực thi đúng cách cho tài khoản chạy dịch vụ.
2. Lỗi mạng và xung đột cổng mạng (Port Conflict):
 Ứng dụng Spring Boot của bạn không thể kết nối tới cơ sở dữ liệu PostgreSQL ở một VPS khác. Làm sao bạn kiểm tra xem đường truyền vật lý có thông hay cổng kết nối database có bị chặn bởi Firewall?
 Ứng dụng Spring Boot bị crash lúc khởi động với lỗi: Web server failed to start. Port 8080 was already in use. Làm sao để bạn biết tiến trình nào đang chiếm dụng cổng 8080 để tắt nó đi?
Bài học này cung cấp các "executable" (lệnh thực thi) cốt lõi để bạn nhanh chóng chẩn đoán và tự xử lý các sự cố trên.

2. Kiến thức cốt lõi (Các lệnh bảo mật & Mạng trên Linux) 
2.1. Quản lý phân quyền và người dùng
Linux được thiết kế bảo mật tối đa, phân chia quyền hạn rất chặt chẽ. Để chạy ứng dụng Java Spring Boot (user-service) một cách an toàn, chúng ta cần tạo một nhóm và người dùng riêng biệt trên hệ thống:

Kiểm tra và liệt kê User/Group trên hệ thống:
getent passwd (hoặc cat /etc/passwd): Liệt kê toàn bộ danh sách tài khoản người dùng đang có trên hệ thống Linux. Bạn có thể kết hợp thêm bộ lọc để tìm kiếm nhanh: getent passwd | grep quickbite.
getent group (hoặc cat /etc/group): Liệt kê toàn bộ danh sách các nhóm người dùng đang có trên hệ thống.
id [tên_user]: Kiểm tra thông tin định danh chi tiết của một người dùng bao gồm UID (User ID), GID (Group ID) và danh sách tất cả các nhóm mà user này đang trực thuộc. Ví dụ: id quickbite.
groups [tên_user]: Liệt kê nhanh danh sách các nhóm mà một người dùng cụ thể đang tham gia.
Tạo nhóm và người dùng hệ thống chuyên dụng (System User & Group):
sudo groupadd quickbite: Tạo một nhóm (group) mới có tên là quickbite để quản lý phân quyền nhóm.
sudo useradd -r -g quickbite -s /bin/false quickbite: Tạo một người dùng hệ thống (system user) mới có tên là quickbite và gán trực tiếp vào nhóm quickbite.
-r: Đánh dấu đây là một *system account(tài khoản hệ thống) có UID thấp, không có thư mục home và dùng riêng để chạy các background service.
-s /bin/false: Vô hiệu hóa shell đăng nhập. Điều này có nghĩa là không ai có thể đăng nhập trực tiếp hoặc dùng SSH để đăng nhập bằng tài khoản quickbite. Đây là quy tắc bảo mật bắt buộc ("best practice") trong DevOps để phòng ngừa việc hacker lợi dụng chiếm quyền shell nếu ứng dụng Spring Boot bị tấn công.
sudo usermod -aG quickbite [tên_user_của_bạn]: Thêm user thường (ví dụ: user bạn dùng để deploy ứng dụng) vào nhóm quickbite để có quyền ghi và đọc file trong thư mục của ứng dụng mà không cần liên tục dùng lệnh sudo.
Các lệnh quản trị quyền cơ bản:
sudo (Superuser Do): Chạy dòng lệnh dưới quyền quản trị tối cao (root). Chỉ dùng khi thực sự cần thiết (cài đặt phần mềm, tạo user, cấu hình hệ thống).
chown -R [user]:[group] [thư_mục_hoặc_file]: Thay đổi chủ sở hữu của file/thư mục.
Nguyên tắc bảo mật:Không bao giờ chạy file JAR của Spring Boot bằng quyền root. Chúng ta luôn đổi chủ sở hữu file JAR về user và group quickbite.
Ví dụ: sudo chown -R quickbite:quickbite /opt/quickbite (đổi chủ sở hữu toàn bộ thư mục /opt/quickbite chứa project sang user và group quickbite).
chmod [quyền] [tên_file]: Thay đổi quyền đọc (r), ghi (w), và thực thi (x) của file hoặc thư mục.
Ví dụ: chmod +x gradlew (cấp quyền thực thi cho script build Gradle).

2.2. Chẩn đoán và kiểm tra mạng (Network Diagnostics)
ip addr (hoặc ifconfig): Kiểm tra địa chỉ IP hiện tại của máy chủ Linux.
ping [IP_Server]: Kiểm tra xem máy chủ hiện tại có kết nối vật lý thông suốt tới máy chủ database hay không.
curl -I [URL]: Gửi một HTTP request nhanh đến URL mục tiêu để kiểm tra xem một service web khác có đang hoạt động hay không mà không cần mở trình duyệt.
ss -tulpn (hoặc netstat -tulpn): Liệt kê tất cả các cổng mạng đang mở trên server và chỉ rõ PID (Process ID) nào đang chiếm giữ cổng đó.
Ví dụ lọc cổng 8080: sudo ss -tulpn | grep :8080

3. Demo chuẩn đoán lỗi thực tế
Tình huống 1: Ứng dụng báo trùng cổng 8080 khi khởi chạy user-service

# 1. Tìm tiến trình đang chiếm dụng cổng 8080

sudo ss -tulpn | grep :8080

# Output hiển thị PID đang dùng là 14522:

# tcp  LISTEN  0  100  *:8080  *: users:(("java",pid=14522,fd=15))

 

# 2. Tắt tiến trình cũ để giải phóng cổng 8080

sudo kill -9 14522

 

Tình huống 2: Cấp quyền chạy file script build Gradle ở local/server Linux

# Cố gắng chạy gradle để build

./gradlew bootJar

# Lỗi: bash: ./gradlew: Permission denied

# Cấp quyền thực thi (eXecute) cho file script

chmod +x gradlew

# Chạy lại lệnh build thành công

./gradlew bootJar

 

Tình huống 3: Tạo người dùng quickbite và thiết lập thư mục chạy user-service

# 1. Tạo thư mục chứa ứng dụng user-service

sudo mkdir -p /opt/quickbite/user-service

 

# 2. Tạo nhóm và người dùng hệ thống quickbite (chỉ chạy một lần đầu tiên)

sudo groupadd quickbite

sudo useradd -r -g quickbite -s /bin/false quickbite

 

# 3. Phân quyền sở hữu thư mục cho user và group quickbite

sudo chown -R quickbite:quickbite /opt/quickbite

 

# 4. Thiết lập quyền truy cập cho thư mục dự án

sudo chmod -R 750 /opt/quickbite

Giải thích quyền 750:

- Chủ sở hữu (quickbite user): Có quyền Đọc, Ghi, Thực thi (7 -> rwx)

- Nhóm sở hữu (quickbite group): Có quyền Đọc và Thực thi (5 -> r-x)

- Những người dùng khác (others): Hoàn toàn không có quyền truy cập (0 -> ---)

Việc này giúp bảo mật tối đa file JAR và các file chứa mật khẩu cấu hình.