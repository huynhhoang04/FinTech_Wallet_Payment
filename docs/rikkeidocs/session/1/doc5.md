Bài đọc: Hệ điều hành Linux và vai trò trong triển khai hệ thống
 

Có thể học viên sẽ thắc mắc: "Tại sao đến bài học thứ 5 chúng ta mới học về Linux?"

 

Ở 4 bài học trước, chúng ta tập trung định hình tư duy và bức tranh tổng quan ở tầng kiến trúc (Hiểu về nỗi đau của deploy thủ công, khái niệm CI/CD, phân chia 3 môi trường và mô hình Microservices). Đây là những lý thuyết hệ thống giúp bạn có góc nhìn vĩ mô.

Môi trường học tập xuyên suốt: Từ Session 2, chúng ta sẽ chính thức bắt đầu thực hành kỹ thuật với Docker (Đóng gói ứng dụng). Docker hoạt động dựa trên cơ chế chia sẻ chung nhân hệ điều hành (Shared Kernel) và nhân đó bắt buộc là nhân Linux. Do đó, Linux sẽ là môi trường làm việc, cài đặt và triển khai chính thức của bạn trong suốt phần còn lại của môn học này. Đây là thời điểm bắt buộc bạn phải làm quen với dòng lệnh Linux.

 

1. Kiến thức cốt lõi (Các lệnh Linux cơ bản và thực chiến)
Hệ điều hành Linux trên server không có giao diện đồ họa (GUI). Bạn sẽ giao tiếp với nó 100% qua cửa sổ dòng lệnh (CLI). Hãy coi mọi thư mục và lệnh chạy dưới dạng các file thực thi (executables). 

Vì đây là môn thực chiến, trọng tâm là học viên cần tự gõ lệnh và tự tìm hiểu thêm để biến nó thành kỹ năng phản xạ.

1.1. Khối lệnh khởi tạo hệ thống (Initial Setup Commands)
Khi vừa nhận một máy chủ Linux mới tinh từ các nhà cung cấp cloud (như AWS EC2, VPS), các lệnh đầu tiên bạn bắt buộc phải chạy bao gồm:

sudo apt-get update && sudo apt-get upgrade -y: Cập nhật danh sách gói phần mềm và nâng cấp hệ thống lên phiên bản mới nhất để vá lỗi bảo mật.
sudo apt-get install -y openjdk-17-jdk: Cài đặt Java Development Kit (JDK 17) để chạy ứng dụng Spring Boot của chúng ta (dĩ nhiên rồi).
Mẹo tự động hóa thực chiến:

Thay vì mỗi lần tạo server mới lại gõ tay từng dòng lệnh, bạn nên tạo sẵn một file script cài đặt ban đầu có tên initial-script.sh chứa toàn bộ các lệnh trên:

#!/bin/bash

sudo apt-get update && sudo apt-get upgrade -y

sudo apt-get install -y openjdk-17-jdk curl git

 Sau đó cấp quyền thực thi bằng lệnh chmod +x initial-script.sh và chạy lệnh ./initial-script.sh để hệ thống tự động thiết lập từ A-Z, tiết kiệm cực kỳ nhiều thời gian.

1.2 Thao tác thư mục và file cơ bản
pwd (Print Working Directory): Hiển thị đường dẫn tuyệt đối của thư mục hiện hành.
ls -la (List): Liệt kê tất cả các file và thư mục con (bao gồm cả file ẩn bắt đầu bằng dấu chấm và hiển thị chi tiết quyền hạn).
cd [đường_dẫn] (Change Directory): Di chuyển thư mục (cd .. lùi về thư mục cha, cd ~ về thư mục Home).
mkdir -p [tên_thư_mục]: Tạo thư mục mới (cờ -p tạo luôn các thư mục cha nếu chưa tồn tại).
cat [tên_file]: Đọc nhanh nội dung file.
nano [tên_file] hoặc vi [tên_file]: Trình soạn thảo văn bản trực tiếp trên Terminal để sửa cấu hình.
cp (Copy), mv (Move/Rename), rm (Remove - Xóa file, rm -rf để xóa thư mục).
1.3 Làm việc với Biến môi trường (Environment Variables)
Như đã học ở bài 3, biến môi trường là chìa khóa để triển khai "Build once, run anywhere".

Kiểm tra biến môi trường: Dùng lệnh echo $[TÊN_BIẾN].
Ví dụ: echo $PATH để hiển thị danh sách các thư mục chứa các executable (file thực thi) mà hệ thống có thể chạy trực tiếp từ bất cứ đâu.

Cấu hình biến môi trường tạm thời: Chỉ có hiệu lực trong phiên làm việc (session) terminal hiện hành:
export QUICKBITE_DB_USER=staging_admin

 

 (Nếu tắt terminal này đi mở lại, biến trên sẽ bị mất).

Cấu hình biến môi trường lâu dài (Bash Profile / Bashrc): 
Mỗi user trên Linux có các file ẩn nằm ở thư mục Home để nạp cấu hình mỗi khi đăng nhập: .bashrc hoặc .bash_profile. Để lưu biến môi trường vĩnh viễn, bạn làm như sau:

 # Ghi đè dòng khai báo biến vào cuối file .bashrc của user hiện tại

  echo "export QUICKBITE_DB_USER=staging_admin" >> ~/.bashrc

 

  # Yêu cầu terminal nạp lại cấu hình ngay lập tức mà không cần logout

  source ~/.bashrc

1.4 Quản lý tiến trình (Process) và các Executable đặc trưng
Ứng dụng Spring Boot chạy bằng lệnh java -jar sẽ tạo ra một tiến trình:

ps aux | grep java: Tìm tiến trình Java đang chạy và lấy ID của nó (PID).

kill -9 [PID]: Dừng khẩn cấp tiến trình có ID tương ứng.

 

Trong DevOps, bạn sẽ thường xuyên tương tác trực tiếp với các executable hệ thống quan trọng sau:

Systemd (Quản lý dịch vụ): Chạy thông qua lệnh systemctl để điều khiển chạy ngầm ứng dụng.
sudo systemctl start [service]: Chạy dịch vụ.
sudo systemctl stop [service]: Dừng dịch vụ.
sudo systemctl restart [service]: Khởi động lại dịch vụ.
sudo systemctl status [service]: Xem chi tiết trạng thái dịch vụ.
sudo systemctl enable [service]: Cho phép dịch vụ tự khởi động cùng hệ điều hành khi reboot.
sudo systemctl disable [service]: Tắt tính năng tự khởi động cùng OS.
Nginx (Reverse Proxy & Web Server): Executable điều khiển máy chủ Nginx.
sudo nginx -t: (Cực kỳ quan trọng) Kiểm tra cú pháp của file cấu hình Nginx xem có bị lỗi dấu chấm phẩy hay sai cấu trúc hay không trước khi áp dụng.
  - sudo nginx -s reload: Reload cấu hình Nginx mà không cần restart máy chủ, không làm gián đoạn các kết nối hiện tại của khách hàng.
Docker & Docker Compose: Đây là các executable chạy container (docker run, docker-compose up). Vì chúng là trọng tâm của các Session tiếp theo nên tạm thời bạn chỉ cần biết đây là các công cụ để đóng gói ứng dụng, chúng ta chưa cần gõ lệnh chi tiết ở bài học này.
2. Demo thực hành tổng hợp
Giả sử bạn vừa truy cập vào một VPS Linux mới được cài đặt để chuẩn bị chạy dịch vụ user-service:

# 1. Tạo script khởi tạo tự động cài đặt JDK và nginx

echo -e "#!/bin/bash\nsudo apt-get update\nsudo apt-get install -y openjdk-17-jdk nginx" > initial.sh

chmod +x initial.sh

./initial.sh

 

# 2. Tạo thư mục cấu hình và export cấu hình database lâu dài

mkdir -p /opt/quickbite/config

echo "export QUICKBITE_DB_URL=jdbc:postgresql://localhost:5432/quickbite_user" >> ~/.bashrc

source ~/.bashrc

 

# 3. Kiểm tra xem biến đã được nạp thành công chưa

echo $QUICKBITE_DB_URL

# Output mong đợi: jdbc:postgresql://localhost:5432/quickbite_user

 

# 4. Kiểm tra cấu hình của máy chủ Nginx xem có bị lỗi cú pháp trước khi hoạt động

sudo nginx -t

 