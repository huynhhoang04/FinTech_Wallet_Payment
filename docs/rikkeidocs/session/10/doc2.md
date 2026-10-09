Bài đọc: Tạo User mới và bảo mật VPS bằng cơ chế khóa SSH (SSH Hardening)
 

1. Rủi ro bảo mật mặc định và nguyên lý SSH Key
1.1. Tấn công dò quét mật khẩu (Brute-force)
Khi máy chủ VPS có một địa chỉ IP công khai, hàng nghìn botnet tự động trên Internet sẽ quét cổng mặc định 22 liên tục. Chúng thực hiện hàng triệu lượt dò tìm thông tin đăng nhập tự động dựa trên từ điển tài khoản (như root, admin) kết hợp với mật khẩu phổ biến. Nếu giữ nguyên cấu hình cho phép đăng nhập bằng tài khoản root và xác thực qua mật khẩu thông thường, hệ thống của bạn sớm muộn cũng sẽ bị tấn công chiếm quyền kiểm soát.

1.2. Nguyên tắc đặc quyền tối thiểu (Least Privilege)
Trong DevOps và quản trị hệ thống chuyên nghiệp, chúng ta tuyệt đối không sử dụng tài khoản root để thực hiện các thao tác vận hành thông thường. Thay vào đó, ta sẽ tạo ra một User riêng biệt (ví dụ: deployer) có quyền hạn thông thường, và chỉ khi thực hiện các tác vụ quản trị (như cài đặt phần mềm, cấu hình hệ thống), User này mới sử dụng quyền tối cao qua tiền tố sudo.

1.3. Cơ chế xác thực khóa SSH (SSH Key Authentication)
SSH Key là phương thức xác thực bảo mật dựa trên mã hóa bất đối xứng sử dụng một cặp khóa:

Private Key (Khóa riêng tư): Lưu trữ bí mật và an toàn tại máy tính cá nhân của bạn (Client). Tuyệt đối không chia sẻ khóa này cho bất kỳ ai.
Public Key (Khóa công khai): Được đưa lên lưu trữ trên server VPS trong thư mục của User được cấp quyền (~/.ssh/authorized_keys).
Khi bạn kết nối, VPS sẽ gửi một thử thách được mã hóa bằng Public Key, và máy local của bạn sử dụng Private Key tương ứng để giải mã và trả lời thử thách. Cơ chế này không gửi mật khẩu qua mạng, loại bỏ hoàn toàn nguy cơ bị nghe lén hoặc tấn công Brute-force mật khẩu.



2. Thực hành cấu hình SSH Hardening
Thực hiện các bước sau để thiết lập người dùng mới và cấu hình bảo mật SSH.

Bước 1: Tạo User mới và phân quyền Sudo
Kết nối vào VPS bằng Bitvise Client (quyền root) và mở cửa sổ Terminal. Chạy các lệnh sau:

# 1. Tạo user mới tên là deployer

adduser deployer

# 2. Nhập mật khẩu mới cho user deployer và xác nhận (bỏ qua các thông tin cá nhân bằng cách nhấn Enter)

# 3. Đưa user deployer vào nhóm quản trị sudo

usermod -aG sudo deployer

Bước 2: Sinh cặp khóa SSH Key trên máy Local
Mở PowerShell hoặc Command Prompt của máy local (máy tính cá nhân của bạn) và sinh cặp khóa bảo mật cao sử dụng thuật toán Ed25519 (khuyến nghị thay thế cho RSA cũ):

ssh-keygen -t ed25519 -C "deployer_vps_key"

Hệ thống sẽ hỏi bạn nơi lưu khóa (nhấn Enter để lưu mặc định tại thư mục C:\Users\<Tên_User>\.ssh\id_ed25519).
Hệ thống hỏi mật khẩu bảo vệ khóa (Passphrase) -> Bạn có thể nhấn Enter để bỏ trống hoặc nhập mật khẩu phụ để tăng cường bảo mật.
Bước 3: Đẩy khóa công khai (Public Key) lên VPS
Tại PowerShell máy local, chạy lệnh sau để sao chép khóa công khai (id_ed25519.pub) vào danh sách authorized_keys của user deployer trên VPS:

ssh-copy-id -i ~/.ssh/id_ed25519.pub deployer@<vps_public_ip>

# Ví dụ: ssh-copy-id -i ~/.ssh/id_ed25519.pub deployer@103.82.20.15

Nếu Windows của bạn không có lệnh ssh-copy-id, bạn có thể mở file id_ed25519.pub ở local, copy nội dung của nó, sau đó trên VPS chuyển sang user deployer, tạo thư mục ~/.ssh, lưu nội dung khóa vào file ~/.ssh/authorized_keys và phân quyền chmod 700 ~/.ssh và chmod 600 ~/.ssh/authorized_keys.

Bước 4: Kiểm tra kết nối thử bằng SSH Key
Trước khi khóa mật khẩu, hãy kiểm tra xem có kết nối được bằng SSH Key hay chưa. Mở một tab PowerShell mới ở local và gõ:

ssh deployer@<vps_public_ip>

Nếu hệ thống đăng nhập thẳng vào VPS của bạn mà không yêu cầu nhập mật khẩu (hoặc chỉ yêu cầu nhập Passphrase bảo vệ key nếu bạn có thiết lập ở Bước 2), thì cấu hình đã thành công!

Bước 5: Cấu hình tắt root login và tắt xác thực mật khẩu
Trên terminal VPS (bằng user deployer), mở tệp tin cấu hình dịch vụ SSH bằng trình soạn thảo nano:

sudo nano /etc/ssh/sshd_config

Tìm các dòng cấu hình sau (sử dụng phím mũi tên di chuyển, nếu có dấu # đứng đầu dòng thì hãy xóa dấu # đi để kích hoạt cấu hình) và sửa đổi giá trị thành:

PermitRootLogin no

PasswordAuthentication no

PubkeyAuthentication yes

PermitRootLogin no: Chặn hoàn toàn việc đăng nhập trực tiếp bằng tài khoản root.
PasswordAuthentication no: Chặn đăng nhập bằng mật khẩu thông thường.
PubkeyAuthentication yes: Chỉ cho phép xác thực bằng SSH Key.
Nhấn Ctrl + O -> Enter để lưu file, và Ctrl + X để thoát trình soạn thảo nano.

Bước 6: Khởi động lại dịch vụ SSH daemon
Để cấu hình mới có hiệu lực, chạy lệnh:

sudo systemctl restart ssh

 

3. Cảnh báo bảo mật quan trọng
[!WARNING] Tuyệt đối không được tắt/đóng cửa sổ terminal kết nối root hiện tại trước khi bạn đã kiểm tra và xác nhận đăng nhập thành công bằng SSH Key của user deployer trên một cửa sổ mới. Nếu cấu hình sai và bạn tắt kết nối cũ, bạn sẽ bị khóa quyền truy cập máy chủ vĩnh viễn và buộc phải cài lại hệ điều hành VPS từ đầu.

 

4. Hiểu lầm thường gặp
Hiểu sai: Nghĩ rằng chỉ cần đặt mật khẩu tài khoản cực kỳ phức tạp (dài, nhiều ký tự đặc biệt) là đã đủ an toàn trước tấn công Brute-force mà không cần cấu hình SSH Key.
Đính chính: Kể cả khi mật khẩu của bạn mạnh, các cuộc tấn công Brute-force liên tục gửi hàng nghìn request kết nối vẫn làm cạn kiệt tài nguyên mạng, băng thông và CPU của VPS để xử lý kiểm tra mật khẩu. Khi bạn tắt hoàn toàn PasswordAuthentication, hệ thống SSH Daemon sẽ ngắt kết nối ngay lập tức ở bước bắt tay (handshake) nếu không khớp SSH Key, giúp triệt tiêu gánh nặng tài nguyên cho VPS.