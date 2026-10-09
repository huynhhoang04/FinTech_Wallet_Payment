Bài đọc: Thiết lập Tường lửa UFW và Cài đặt Docker trên Ubuntu Server
 

1. Cài đặt Docker Engine và Docker Compose chuẩn Enterprise
Để cài đặt Docker trên Ubuntu Server, chúng ta tránh sử dụng gói cài đặt mặc định của Ubuntu (docker.io) vì nó thường là phiên bản cũ, thiếu tính năng mới và không nhận được các bản vá bảo mật nhanh nhất từ Docker. Thay vào đó, ta sẽ cấu hình tích hợp kho lưu trữ (Repository) chính thức của Docker để tải phiên bản chuẩn doanh nghiệp mới nhất.

Bước 1: Gỡ bỏ các phiên bản Docker cũ (nếu có)
Đăng nhập vào VPS với tài khoản deployer và chạy lệnh:

for pkg in docker.io docker-doc docker-compose podman-docker containerd runc; do sudo apt-get remove $pkg; done

Bước 2: Thiết lập Docker Repository và nạp khóa GPG
# Cập nhật danh sách gói và cài đặt các gói hỗ trợ tải qua HTTPS

sudo apt-get update

sudo apt-get install ca-certificates curl gnupg -y

 

# Tạo thư mục lưu khoá bảo mật GPG

sudo install -m 0755 -d /etc/apt/keyrings

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

sudo chmod a+r /etc/apt/keyrings/docker.gpg

 

# Thêm Docker Repository chính thức vào nguồn cài đặt hệ thống

echo \

  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \

  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \

  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

Bước 3: Cài đặt Docker Engine và Docker Compose Plugin
sudo apt-get update

sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y

Bước 4: Phân quyền thực thi Docker không cần sudo
Mặc định, Docker Daemon liên kết và giao tiếp thông qua một Unix socket thuộc sở hữu của người dùng root. Do đó, người dùng thông thường muốn chạy các lệnh docker như docker ps, docker build phải gõ tiền tố sudo (nếu không sẽ báo lỗi Permission denied).

Để khắc phục, chúng ta đưa tài khoản deployer vào nhóm bảo mật docker của hệ thống:

# 1. Tạo nhóm docker (thường đã tự động sinh ra khi cài đặt Docker)

sudo groupadd docker

# 2. Thêm người dùng deployer vào nhóm docker

sudo usermod -aG docker deployer

# 3. Kích hoạt thay đổi quyền nhóm ngay lập tức cho session terminal hiện tại

newgrp docker

Kiểm tra lại xem Docker đã chạy ổn định chưa bằng lệnh không dùng sudo:

docker version

 

2. Thiết lập Tường lửa UFW (Uncomplicated Firewall) bảo vệ VPS
Tường lửa UFW (Uncomplicated Firewall) là một công cụ quản lý tường lửa thân thiện được tích hợp sẵn trên Ubuntu. UFW giúp kiểm soát chặt chẽ toàn bộ lưu lượng mạng đi vào (incoming) và đi ra (outgoing) của VPS.

Quy trình cấu hình tường lửa an toàn:
Bước 1: Thiết lập các luật chặn mặc định
sudo ufw default deny incoming # Chặn tất cả kết nối đi vào VPS mặc định

sudo ufw default allow outgoing # Cho phép VPS tự do kết nối ra ngoài Internet (để tải package, git clone...)

Bước 2: Khai báo mở các cổng dịch vụ thiết yếu
Chúng ta chỉ cho phép các kết nối đi vào từ bên ngoài thông qua các cổng dịch vụ tiêu chuẩn:

Cổng 22/tcp: Cổng SSH để quản trị viên truy cập.
Cổng 80/tcp: Cổng HTTP thông thường cho ứng dụng web.
Cổng 443/tcp: Cổng HTTPS bảo mật.
sudo ufw allow 22/tcp

sudo ufw allow 80/tcp

sudo ufw allow 443/tcp

Bước 3: Kích hoạt tường lửa
[!IMPORTANT] Bạn bắt buộc phải thực hiện mở cổng SSH bằng lệnh sudo ufw allow 22/tcp trước khi chạy lệnh kích hoạt. Nếu kích hoạt UFW khi chưa cho phép cổng 22, bạn sẽ lập tức bị ngắt kết nối SSH hiện tại và không thể truy cập lại máy chủ.

Sau khi đã nạp đầy đủ các luật trên, chạy lệnh kích hoạt:

sudo ufw enable

(Hệ thống sẽ cảnh báo lệnh này có thể làm gián đoạn kết nối SSH hiện tại của bạn, hãy nhập y để xác nhận).

Bước 4: Kiểm tra trạng thái tường lửa
sudo ufw status verbose

Kết quả mong đợi:

Status: active

Logging: on (low)

Default: deny (incoming), allow (outgoing), disabled (routed)

New profiles: skip

 

To Action From

-- ------ ----

22/tcp ALLOW IN Anywhere

80/tcp ALLOW IN Anywhere

443/tcp ALLOW IN Anywhere

 

3. Hiểu lầm thường gặp về Tường lửa UFW và Docker Network
Hiểu sai: Nghĩ rằng tường lửa UFW chặn cổng database 5432 (PostgreSQL) ở ngoài Internet sẽ làm cho ứng dụng Spring Boot chạy trong container Docker không thể kết nối tới cơ sở dữ liệu PostgreSQL (cũng chạy trong một container khác trên cùng VPS).
Đính chính: Các container Docker giao tiếp với nhau qua card mạng ảo riêng biệt (Bridge Network) do Docker Engine quản lý nội bộ. Tường lửa UFW cài đặt trên hệ điều hành Host chỉ lọc lưu lượng mạng đi qua các card mạng vật lý kết nối ra Internet. Do đó, giao tiếp nội bộ giữa các container trong mạng Docker Network hoàn toàn không bị ảnh hưởng bởi UFW. Cấu hình này giúp database được giấu kín hoàn toàn khỏi Internet bên ngoài mà các dịch vụ backend vẫn kết nối bình thường.