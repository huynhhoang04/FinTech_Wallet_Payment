Bài đọc: Cài đặt Docker và kiểm tra môi trường
1. Vấn đề thực tế (Thiết lập môi trường Docker đồng bộ) 
Ở Lesson 2, chúng ta đã nắm được lý thuyết nền tảng về bộ ba Docker Image, Container và Registry. Nhưng mọi lý thuyết sẽ là vô nghĩa nếu bạn không thể tự mình gõ những dòng lệnh đầu tiên để kiểm tra môi trường.

Để bắt tay vào đóng gói user-service và restaurant-service của hệ thống QuickBite, việc đầu tiên bạn cần làm là cài đặt Docker lên máy tính cá nhân. Lúc này, bạn sẽ đối mặt với các vấn đề:

Làm thế nào để cài đặt Docker đúng chuẩn?
Tại sao giao diện dòng lệnh của Docker trên Windows, macOS và Linux lại giống hệt nhau, nhưng cơ chế hoạt động bên dưới của chúng lại rất khác biệt?
Nếu bạn dùng Windows và cài đặt Docker Desktop sai cách (không bật ảo hóa WSL 2), Docker sẽ chạy cực kỳ chậm, ngốn RAM kinh hoàng và khiến máy tính của bạn bị đơ cứng.


Bài học này sẽ hướng dẫn bạn làm chủ kiến trúc Docker Engine và thiết lập môi trường Sandbox chuẩn xác trên máy tính cá nhân.

2. Kiến thức cốt lõi (Kiến trúc Docker Engine & Quy trình cài đặt)
2.1. Kiến trúc Client - Server của Docker Engine
Docker Engine hoạt động theo mô hình Client - Server (Khách - Chủ) gồm 3 thành phần chính:



Docker CLI (Client): Là công cụ dòng lệnh (executable docker) giúp người dùng tương tác. Khi bạn gõ docker run hay docker build, lệnh này không trực tiếp chạy container mà nó chỉ đóng vai trò gửi yêu cầu đi.
REST API: Cầu nối định nghĩa các chuẩn interface để Docker CLI có thể giao tiếp và gửi chỉ thị cho Server.
Docker Daemon (Server - tên tiến trình là dockerd): Đây mới là "trái tim" thực sự của Docker. Nó là một dịch vụ chạy ngầm trên máy chủ, chịu trách nhiệm lắng nghe yêu cầu từ API và trực tiếp thực hiện việc quản lý, tải Image, khởi tạo Container, chia mạng (Network) và gắn ổ đĩa (Volume).
2.2. Hướng dẫn thiết lập môi trường trên các hệ điều hành
Để có môi trường Sandbox chạy Docker chuẩn chỉnh và đồng bộ với môi trường Server thực tế, chúng ta sẽ cài đặt Docker Engine gốc trực tiếp trên môi trường Linux Ubuntu.

A. Trên Windows (Sử dụng WSL 2 để chạy Ubuntu)

Cơ chế: Chúng ta không sử dụng Windows trực tiếp để cài Docker. Thay vào đó, bạn sẽ cài đặt WSL 2 (Windows Subsystem for Linux) để chạy một hệ điều hành Linux Ubuntu siêu nhẹ ngay trong Windows.

Các bước tối giản để cài đặt Ubuntu trên WSL:

Bước 1: Mở PowerShell dưới quyền Administrator và chạy lệnh

# 1. Cài đặt wsl (Lệnh này có thể yêu cầu restart máy trước khi làm các lệnh tiếp theo)

wsl --install

# 2. Liệt kê các distro hỗ trợ

wsl --list --online

# 3. Chọn phiên bản distro để cài đặt (Ubuntu 22 hoặc 24)

wsl --install -d Ubuntu-24.04

Bước 2: Khởi động lại máy tính nếu hệ thống yêu cầu

Bước 3: Mở Terminal Ubuntu lên, thiết lập username và password cho máy Linux của bạn. Từ lúc này, bạn đã có một Sandbox Linux hoàn chỉnh.

Bước 4:  Sau khi đăng nhập vào Ubuntu trong WSL, bạn tiến hành chạy các lệnh cài đặt Docker Engine giống hệt như trên máy chủ Linux ở mục C.

 

VỀ VIỆC SỬ DỤNG DOCKER DESKTOP:

> Có thể bạn sẽ biết đến công cụ Docker Desktop (một phần mềm có giao diện đồ họa GUI hỗ trợ cài đặt nhanh cho Windows và macOS). 

> Tuy nhiên, Docker Desktop hoàn toàn không phù hợp với định hướng của học phần này. 

> Môn học này hướng đến mục tiêu DevOps thực chiến - giúp bạn làm chủ CLI, tự động hóa hạ tầng và chuẩn bị năng lực quản lý server Production thực tế, chứ không phải "up container lên cho vui" bằng vài cú click chuột. Việc sử dụng Docker Desktop sẽ che giấu đi các cơ chế socket và daemon bên dưới, đồng thời tiêu tốn một lượng tài nguyên RAM cực kỳ lớn một cách vô ích. Do đó, bạn bắt buộc phải cài đặt Docker Engine trực tiếp trong shell Linux (WSL hoặc VM).

 

B. Trên macOS (Sử dụng Docker Desktop)

Cơ chế: macOS không hỗ trợ WSL. Lập trình viên dùng macOS bắt buộc phải tự cài đặt một Máy ảo (Virtual Machine - VM) chạy hệ điều hành Ubuntu Server (khuyên dùng các công cụ gọn nhẹ như UTM cho chip Apple Silicon, Multipass, hoặc VirtualBox).

Cách thực hiện: Sinh viên tự tìm hiểu cách cấu hình phần mềm máy ảo để khởi chạy thành công một VM Ubuntu Server. Sau khi cài đặt và SSH được vào VM Ubuntu đó, mọi thao tác cài đặt và chạy Docker hoàn toàn giống hệt như một máy Linux thực tế (làm theo mục C).

 

C. Trên Linux Ubuntu (Sử dụng Docker Engine gốc)

 Lưu ý thực chiến: Trên server chạy thật (Production/Staging), chúng ta không cài Docker Desktop vì nó có giao diện đồ họa nặng nề. Chúng ta chỉ cài đặt bản phân phối Docker Engine gốc dạng CLI để tiết kiệm tối đa RAM và CPU.
 Lệnh cài đặt nhanh qua Repository chính thức:
# Cập nhật và cài đặt thư viện hỗ trợ HTTPS

sudo apt-get update && apt-get upgrade

sudo apt-get install -y ca-certificates curl gnupg

 

# Thêm GPG key chính thức của Docker

sudo install -m 0755 -d /etc/apt/keyrings

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

 

# Cấu hình apt repository

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

 

# Cài đặt Docker Engine

sudo apt-get update

sudo apt-get install -y docker-ce docker-ce-cli containerd.io

 

D. Cấu hình sau cài đặt (Post-installation steps):

Mẹo thực chiến: Chạy Docker không cần gõ sudo liên tục

Mặc định, Docker Daemon liên kết với một Unix socket thuộc sở hữu của user root. Do đó, nếu bạn chạy các lệnh docker bằng user thường, hệ thống sẽ báo lỗi phân quyền và bắt buộc bạn phải gõ sudo phía trước. Để giải phóng bản thân khỏi việc này, hãy phân quyền cho user của bạn vào nhóm docker:

# Tạo nhóm docker (thường đã tự động được tạo khi cài đặt)

sudo groupadd docker

 

# Thêm user hiện hành ($USER) vào nhóm docker

sudo usermod -aG docker $USER

 

# Nạp lại cấu hình nhóm ngay lập tức mà không cần logout/reboot máy chủ

newgrp docker

(Từ lúc này, có thể chạy thoải mái các lệnh docker version, docker ps... mà không cần thêm sudo nữa).

Lưu ý quan trọng cho WSL 2 (Windows): Kích hoạt Systemd

Mặc định, một số bản phân phối Ubuntu trên WSL 2 không tự động kích hoạt bộ quản lý dịch vụ systemd (dẫn đến việc các lệnh dịch vụ như systemctl start docker hay cấu hình dịch vụ Spring Boot chạy ngầm ở Session 1 bị lỗi). Hãy kích hoạt nó bằng các bước sau:

Trong terminal của Ubuntu (WSL), tạo hoặc mở file cấu hình wsl bằng lệnh:

sudo nano /etc/wsl.conf

Thêm các dòng cấu hình sau vào file:

[boot]

systemd=true

Nhấn Ctrl+O để lưu, Enter để xác nhận, và Ctrl+X để thoát trình soạn thảo nano.
Mở cửa sổ PowerShell trên Windows của bạn và chạy lệnh tắt hẳn WSL để nạp lại cấu hình:

wsl --shutdown

   

Mở lại terminal Ubuntu. Dịch vụ Docker daemon sẽ tự động được khởi chạy ngầm thông qua systemd mà không cần bạn phải kích hoạt thủ công.
3. Thực hành: Kiểm tra môi trường & Container đầu tiên
Sau khi cài đặt xong, bạn mở Terminal lên và chạy chuỗi lệnh chẩn đoán sau:

Lệnh 1: Kiểm tra kết nối Client - Daemon

docker version

Kết quả mong đợi: In ra thông tin phiên bản của cả Client (Dòng lệnh) và Server (Docker Daemon). Điều này chứng tỏ Client đã giao tiếp thành công với Daemon qua REST API.

Lệnh 2: Kiểm tra tài nguyên hệ thống Docker

docker info

Kết quả mong đợi: Hiển thị chi tiết số lượng container đang chạy, số lượng image đang có ở local, Driver lưu trữ (Storage Driver), và tài nguyên CPU/RAM mà Docker đang được phép sử dụng từ máy host.

Lệnh 3: Khởi chạy container đầu tiên

docker run hello-world

 Luồng chạy nội bộ của lệnh:

Docker Client gửi yêu cầu chạy image hello-world tới Docker Daemon.
Docker Daemon kiểm tra ở bộ nhớ local xem đã có image hello-world chưa.
Vì là máy mới tinh chưa có image, Daemon tự động kết nối lên Docker Hub để tải (pull) image hello-world về máy.
Daemon giải nén image và khởi chạy container. Container in ra dòng chữ chào mừng và tự động dừng lại.
 

4. Hiểu lầm thường gặp & Lưu ý bảo mật 
 Hiểu lầm: Tôi gõ lệnh chạy container trong cửa sổ Terminal. Khi tôi tắt cửa sổ Terminal đó đi hoặc tắt máy Terminal, container đang chạy ngầm cũng sẽ bị dừng lại theo.
 Sự thật: Hoàn toàn sai. Docker CLI chỉ là một "khách gửi thư". Khi bạn chạy container (nhất là ở chế độ chạy ngầm -d), yêu cầu đã được gửi đến Docker Daemon (Server) chạy độc lập dưới nền của hệ thống. Container sẽ tiếp tục chạy bình thường cho dù bạn tắt Terminal hay tắt kết nối SSH, trừ khi bạn chủ động tắt tiến trình Docker Daemon hoặc tắt máy tính vật lý.
5. Tài liệu tham khảo
Học viên có thể tham khảo trực tiếp các liên kết chính thức sau từ Docker Docs:

Kiến trúc chi tiết của Docker Engine:
Docker Engine Architecture - Docker Docs

Hướng dẫn cài đặt Docker Desktop trên Windows (WSL 2):
Docker Desktop for Windows Install Guide - Docker Docs

Hướng dẫn cài đặt Docker Desktop trên macOS:
Docker Desktop for Mac Install Guide - Docker Docs

Hướng dẫn cài đặt Docker Engine gốc trên Ubuntu Server:
Install Docker Engine on Ubuntu - Docker Docs