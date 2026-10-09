Bài đọc: Đồng bộ cấu hình qua Git bằng SSH Key và vận hành cụm Microservices
 

1. Phân biệt hai loại SSH Key trong quy trình triển khai
Trong quy trình triển khai hạ tầng DevOps chuyên nghiệp, bạn sẽ làm việc với hai loại kết nối SSH độc lập. Lập trình viên thường bị nhầm lẫn giữa hai loại khóa này:

SSH Key 1 (Local máy cá nhân -> VPS):
Nơi tạo: Sinh ra trên máy tính cá nhân của bạn.
Nơi đặt Public Key: VPS (/home/deployer/.ssh/authorized_keys).
Mục đích: Xác thực cho phép bạn đăng nhập từ máy tính cá nhân vào quản trị VPS.
SSH Key 2 (VPS -> GitHub/GitLab):
Nơi tạo: Sinh ra trực tiếp trên máy chủ VPS bằng tài khoản deployer.
Nơi đặt Public Key: Cài đặt trong mục SSH and GPG keys trên tài khoản cá nhân GitHub/GitLab của bạn.
Mục đích: Cấp quyền cho VPS truy cập, kéo mã nguồn (Git Clone/Pull) từ các kho chứa Git riêng tư (Private Repository) mà tài khoản của bạn được cấp quyền về máy chủ.


2. Thực hành thiết lập kết nối Git và vận hành Microservices
Bước 1: Sinh khóa SSH Key trên VPS để kết nối GitHub
Tại terminal của VPS (đang đăng nhập bằng user deployer), chạy lệnh tạo khóa mới:

ssh-keygen -t ed25519 -C "vps_to_github_key"

(Nhấn Enter liên tục để lưu mặc định tại /home/deployer/.ssh/id_ed25519 và không cài passphrase).

Bước 2: Lấy khóa công khai và cấu hình SSH Key trên GitHub
Hiển thị nội dung khóa công khai vừa sinh ra trên VPS:

cat ~/.ssh/id_ed25519.pub

Sao chép toàn bộ chuỗi ký tự hiển thị trên màn hình (bắt đầu bằng ssh-ed25519 ...).

Đăng nhập vào trang web GitHub, click vào ảnh đại diện ở góc trên cùng bên phải và chọn Settings.
Ở thanh menu bên trái, tìm và chọn SSH and GPG keys -> Nhấn New SSH key.
Điền thông tin:
Title: Nhập tên gợi nhớ để phân biệt (Ví dụ: quickbite_vps_deployer).
Key type: Giữ nguyên Authentication Key.
Key: Dán toàn bộ nội dung khóa công khai đã sao chép ở trên vào đây.
Nhấn Add SSH key.
Bước 3: Kiểm tra kết nối từ VPS đến GitHub
Quay lại terminal của VPS, chạy lệnh kiểm tra kết nối:

ssh -T git@github.com

Nếu nhận lời chào mừng từ GitHub như dưới đây, kết nối đã thành công:

Hi your_username/quickbite-base! You've successfully authenticated, but GitHub does not provide shell access.

Bước 4: Clone dự án về VPS
Tạo thư mục dự án và clone mã nguồn về:

mkdir -p ~/projects && cd ~/projects

# Thực hiện clone dự án sử dụng giao thức SSH

git clone git@github.com:your_username/quickbite-base.git

(Thay thế your_username/quickbite-base.git bằng link SSH Repository thực tế của bạn).

Bước 5: Cấu hình tệp tin môi trường .env
Di chuyển vào thư mục hạ tầng dự án vừa clone về (chứa tệp tin docker-compose.yml):

cd ~/projects/quickbite-base

Tạo mới và chỉnh sửa file .env bằng trình soạn thảo nano:

nano .env

Khai báo các biến cấu hình phù hợp cho môi trường Production thực tế trên VPS:

# Cấu hình Database

DB_HOST=quickbite-db

DB_PORT=5432

DB_NAME=quickbite

DB_USERNAME=postgres

DB_PASSWORD=MySecurePassword123!

 

# Cấu hình cổng chạy ứng dụng Backend

USER_SERVICE_PORT=8081

RESTAURANT_SERVICE_PORT=8082

ORDER_SERVICE_PORT=8083

NOTIFICATION_SERVICE_PORT=8084

 

# Cấu hình bảo mật JWT Token

JWT_SECRET_KEY=chat

(Nhấn Ctrl + O -> Enter để lưu, và Ctrl + X để thoát).

Bước 6: Khởi chạy cụm Microservices bằng Docker Compose
Tiến hành tải các image cần thiết và khởi chạy toàn bộ các dịch vụ ở chế độ chạy ngầm (detached mode):

docker compose up -d

Bước 7: Kiểm tra và giám sát các dịch vụ
Sau khi khởi chạy, sử dụng các câu lệnh sau để theo dõi sức khỏe hệ thống:

# 1. Kiểm tra danh sách container và trạng thái sức khoẻ (healthy)

docker compose ps

 

# 2. Xem logs hoạt động thời gian thực của các container

docker compose logs -f

 

# 3. Giám sát lượng CPU, RAM tiêu thụ thực tế của từng container trên VPS

docker stats

 

3. Nguyên tắc bảo mật tệp cấu hình .env
[!IMPORTANT] Tệp .env chứa các thông số nhạy cảm và bảo mật cao nhất của dự án chạy thực tế (như mật khẩu cơ sở dữ liệu, khoá bí mật JWT token).

Không bao giờ được đẩy file .env lên Git Repository.
Đảm bảo file .env đã được khai báo bên trong tệp .gitignore ở thư mục gốc của dự án.
Mỗi môi trường (Local, Staging, Production) sẽ tự viết và quản lý một file .env vật lý riêng biệt tại máy chủ đó.
4. Hiểu lầm thường gặp
Hiểu sai: Nghĩ rằng chỉ cần cài đặt công cụ Git trên VPS là có thể chạy lệnh git clone bất kỳ dự án nào từ GitHub về máy chủ mà không cần cấu hình khóa xác thực.
Đính chính: Đối với các kho chứa mã nguồn để ở chế độ riêng tư (Private Repository), máy chủ GitHub sẽ từ chối mọi yêu cầu truy cập ẩn danh. SSH Key cung cấp một kênh xác thực tự động và an toàn cao mà không yêu cầu bạn phải gõ tài khoản/mật khẩu GitHub trực tiếp dưới dạng plain text trên máy chủ VPS.