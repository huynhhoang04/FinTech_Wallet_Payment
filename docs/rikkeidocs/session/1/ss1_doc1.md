Bài đọc: Tổng quan DevOps và hạn chế của triển khai thủ công
1. Triển khai phần mềm là gì và sự "ngây thơ" của lập trình viên
Trước nay, khi học lập trình, các bạn chỉ mới học về phát triển (Development) - tức là viết code, mở IDE, nhấn nút Run hoặc Debug, rồi mở trình duyệt gõ localhost:8080. Mọi thứ thật lung linh và hoàn hảo trong chiếc "hộp cát" (sandbox) cá nhân của bạn.

Nhưng phải thật tỉnh táo: Không ai trả tiền cho một ứng dụng chỉ chạy được trên localhost của bạn.

Triển khai phần mềm (Deployment) là quá trình đưa ứng dụng từ máy tính của bạn đến máy chủ thực tế (Production Server) để người dùng cuối có thể truy cập, sử dụng và đem lại doanh thu cho doanh nghiệp. Một lập trình viên giỏi không chỉ biết viết code chạy được, mà phải hiểu cách đưa sản phẩm đó ra thế giới thực. Nếu học viên nghĩ code chạy mượt trên máy cá nhân đã là xong việc, thì bạn vẫn còn cực kỳ "ngây thơ" và "khờ khạo" về quy trình làm phần mềm thực tế.



2. Vấn đề thực tế: kịch bản “ăn hành” của Nền tảng Giao đồ ăn (Food Delivery Platform)
Trong xuyên suốt môn học này, chúng ta sẽ liên hệ trực tiếp đến một Nền tảng Giao đồ ăn giả định, sau đây sẽ gọi là hệ thống QuickBite.

Giả sử như QuickBite có 4 dịch vụ độc lập (theo mô hình microservices): user-service, restaurant-service, user-service, và notification-service. Để chạy thử trên môi trường thực tế, chúng ta được cấp một số máy chủ ảo (VPS) cấu hình cực kỳ tối giản (1 vCPU, 1GB RAM hoặc 500MB RAM) nhằm tiết kiệm chi phí. Bản thân những con máy ảo đơn lẻ này yếu đến nỗi không thể tự build được JAR cho chính nó.



Nếu bạn chọn con đường triển khai thủ công - tự mình gõ lệnh, tự copy file, tự cài đặt hệ thống - bạn sẽ lập tức rơi vào những kịch bản "dở khóc dở cười" dưới đây:

Kịch bản 1: Vòng lặp build-deploy vô tận chỉ vì... một dấu phẩy

Bạn phát hiện một lỗi chính tả nhỏ trên API hoặc chỉ muốn sửa đổi một dấu phẩy trong dòng log của user-service bởi vì bạn là người cầu toàn, nên muốn mọi thứ hoàn hảo:

Quy trình thủ công bắt đầu:

1. Chạy lệnh đóng gói tại máy local (./gradlew clean bootJar) - chờ 2 phút.

2. Dùng lệnh scp đẩy file JAR (dung lượng ~50MB) lên server thông qua đường truyền internet công cộng - chờ 3 phút.

3. SSH vào server, gõ lệnh reload và restart dịch vụ - mất 2 phút.

Tổng cộng bạn tốn 7 phút cuộc đời chỉ để sửa một lỗi chính tả! Nếu phải sửa 10 lần một ngày, bạn chắc chắn sẽ điên đầu mất.



Kịch bản 2: "Râu ông này cắm cằm bà kia" (Environment Drift)

Khi triển khai thủ công, bạn phải tự cấu hình các file application.yml hoặc nạp biến môi trường bằng tay.

Một ngày đẹp trời, bạn deploy user-service lên server chạy thật (Production) nhưng sơ ý lấy nhầm file cấu hình của máy cá nhân. 

Hậu quả: Nhẹ thì là bạn phải sửa lại file cấu hình rồi lặp lại vòng lặp của kịch bản 1, nặng thì có thể là toàn bộ hệ thống bị chuyển hướng về môi trường giả lập cho Development, ví dụ như dữ liệu bị lưu vào database của Development, chứ không phải database Production thật.
Kịch bản 3: Lệch pha môi trường chạy (Java Runtime Mismatch)

Ở máy cá nhân, bạn cài Java 21 để viết code Spring Boot. Khi lên server Linux, bạn cài đặt Java bằng lệnh mặc định của hệ điều hành (apt install default-jdk) mà không kiểm tra kỹ. Server thực tế chỉ được cài Java 17 hoặc thậm chí Java 11.

Hậu quả: Khi khởi chạy file JAR trên server, hệ thống ném ra một lỗi kinh điển: java.lang.UnsupportedClassVersionError: Has been compiled by a more recent version of the Java Runtime.
Kịch bản 4: Hỗn loạn phiên bản đóng gói (File Naming Chaos)

Không có quy trình quản lý tự động, bạn tự copy file lên server và bắt đầu đặt tên file một cách vô tội vạ để backup:

user-service-0.0.1.jar, user-service-final.jar, user-service-final-v2.jar, user-service-fixed-bug.jar...

Sau một tuần, khi hệ thống gặp lỗi nghiêm trọng và cần quay lại (Rollback) phiên bản cũ ổn định, cả đội nhìn vào thư mục server và hoàn toàn bất lực: Không ai biết file nào thực sự đang chạy, file nào tương ứng với commit nào trên Git, và file nào an toàn để khôi phục.

Kịch bản 5: "Mù" thông tin khi có sự cố (Log Blindness)

Vì triển khai thủ công và không có hệ thống ghi log tập trung, bạn phải SSH vào server, lò dò gõ lệnh tìm file log. Bạn mở ra một file log thô nặng vài GB, chữ chạy hoa mắt và không biết tìm từ khóa nào để chẩn đoán lỗi. Trong lúc bạn đang "mù thông tin", hệ thống tiếp tục chết và doanh nghiệp đang mất tiền theo từng giây.

3. Kiến thức cốt lõi
Triển khai thủ công bộc lộ rõ những hạn chế chí mạng về Tốc độ, Độ ổn định, và Bảo mật. Để giải quyết triệt để, chúng ta cần phân rã quy trình truyền thống và tiếp cận tư duy DevOps.

3.1. Quy trình bàn giao truyền thống (Traditional Software Delivery Flow)
Quy trình truyền thống gồm các bước thao tác bằng tay tuần tự:



Build Artifact (Đóng gói): Lập trình viên tự chạy Gradle hoặc Maven ở máy local để đóng gói mã nguồn thành file JAR/WAR.
Deliver (Mang đi): Chuyển file đóng gói qua mạng internet bằng các giao thức thủ công như SCP hoặc FTP lên máy chủ.
Configure (Cấu hình): Thiết lập thủ công các thông số kết nối Database, cổng mạng và biến môi trường ngay trên hệ điều hành của máy chủ.
Execute (Khởi chạy): Khởi chạy tiến trình Java và quản lý việc chạy ngầm hoặc tự động khởi động lại bằng Systemd hoặc các script tự viết.
Trong mô hình này, tồn tại một "Bức tường ngăn cách" (Wall of Confusion) giữa đội Phát triển (Dev) và Vận hành (Ops). Dev chỉ tập trung viết code rồi "ném" file JAR qua bức tường cho Ops chạy. Khi hệ thống lỗi, Dev đổ cho Ops cấu hình server sai, Ops đổ cho Dev viết code lỗi. Quy trình bàn giao đứt gãy và thủ công này chính là nguyên nhân gây ra sự chậm trễ và bất ổn định.

3.2. DevOps không chỉ là một công việc
DevOps (kết hợp của Development và Operations) sinh ra để phá bỏ bức tường ngăn cách đó. Tuy nhiên, bạn cần hiểu đúng: DevOps không đơn thuần là một vị trí công việc, một phòng ban mới hay một checklist nhiệm vụ được giao.

Bản chất của DevOps đối với một lập trình viên là sự am hiểu tổng quan về hệ thống thực tế và sự hiểu biết sâu sắc về chính ngôn ngữ lập trình bạn sử dụng (Java):

Am hiểu hệ thống thực tế: Bạn phải hiểu rõ ứng dụng của mình "sống" như thế nào ngoài đời thực. Dữ liệu đi qua API Gateway đến các microservices ra sao? Các container giao tiếp với nhau bằng network gì? Hệ thống sẽ ghi nhận log và giám sát tài nguyên bằng công cụ nào khi gặp sự cố?
Hiểu biết sâu sắc về ngôn ngữ phát triển (Java/Spring Boot): Đối với lập trình viên Java, DevOps đòi hỏi bạn phải làm chủ được cách Java vận hành ở môi trường thực tế:
Hiểu rõ cơ chế quản lý bộ nhớ của JVM (Java Virtual Machine): Heap size, Metaspace hoạt động thế nào trên môi trường container và các VPS cấu hình yếu (ví dụ: cách cấu hình RAM cho JVM để tránh bị Linux Kernel giết tiến trình vì lỗi Out of Memory - OOM Killer).
Hiểu rõ Spring Boot đọc và ghi đè cấu hình thế nào (application.yml nạp cấu hình động từ biến môi trường của hệ điều hành).
Sản phẩm cốt lõi sinh ra từ DevOps là đường ống tự động (Automated Pipeline): 
Git Commit & Push (Kích hoạt): Điểm bắt đầu kích hoạt toàn bộ Pipeline.
Build & Unit Test (Kiểm soát chất lượng): Tự động biên dịch, tự động chạy test.
Package & Deliver (Đóng gói & Phân phối): Nếu test thành công, sẽ được đóng gói JAR và chuyển lên server.
Execute Server Script (Triển khai & Khởi chạy): Tự động chạy các script server để khởi động ứng dụng mới (phiên bản mới của ứng dụng).
 

DevOps là sự dịch chuyển văn hóa và tư duy: Lập trình viên không chỉ biết viết code chạy được trên localhost, mà phải chịu trách nhiệm và am hiểu cách dòng code đó được đóng gói, phân phối, vận hành, và giám sát trên máy chủ thực tế.

4. Thực hành: Trải nghiệm Triển khai Thủ công trên Server
Để hiểu DevOps quý giá thế nào, trước tiên bạn phải tự mình trải nghiệm cảm giác "ăn hành" khi triển khai thủ công dịch vụ user-service trên server Ubuntu.

 

Lưu ý về Môi trường thực hành (Giả định mặc định):

Ở buổi học đầu tiên này, mục tiêu lớn nhất của chúng ta là thấu hiểu luồng di chuyển của file và nỗi đau của các thao tác thủ công, chứ không phải cấu hình hạ tầng. 

Hãy giả định mặc định rằng Staging Server (Linux) đã được đội vận hành (Ops) dựng sẵn, cài sẵn Java và cấp cho bạn thông tin kết nối SSH. Bạn tạm thời sử dụng các lệnh scp, ssh như một "hộp đen" có sẵn.

Toàn bộ quy trình tự mua VPS, thiết lập Linux, cấu hình SSH Key và Firewall từ con số 0 sẽ được hướng dẫn rất chi tiết tại Session 10 (Triển khai hệ thống lên VPS).

4.1 Cấu hình Systemd dịch vụ trên Server
Trong thực tế phát triển phần mềm, Linux (như Ubuntu/Debian) là hệ điều hành máy chủ tiêu chuẩn của DevOps do tính gọn nhẹ và bảo mật. Tuy nhiên, nếu doanh nghiệp của bạn bắt buộc phải sử dụng Windows Server, quy trình khởi chạy tiến trình sẽ rất khác:

Trên Windows Server: Bạn không có Systemd. Thay vào đó, bạn sẽ phải dùng công cụ bên thứ ba như NSSM (Non-Sucking Service Manager) để "bọc" lệnh java -jar thành một Windows Service chạy ngầm, hoặc cấu hình chạy qua Task Scheduler.
Trên Linux Server (Hệ điều hành chuẩn của khóa học): Chúng ta sử dụng Systemd - bộ quản lý hệ thống mặc định của Linux để làm ví dụ cho bài này. Ta sẽ tạo một file cấu hình dịch vụ tại đường dẫn /etc/systemd/system/quickbite-user.service trên server để quản lý tiến trình chạy ngầm của user-service:
 

[Unit]

Description=QuickBite Order Service

After=syslog.target network.target

 

[Service]

User=quickbite

Type=simple

WorkingDirectory=/opt/quickbite

ExecStart=/usr/bin/java -jar /opt/quickbite/user-service-0.0.1.jar

Restart=always

RestartSec=10

 

[Install]

WantedBy=multi-user.target

4.2 Các bước cập nhật thủ công từ Local lên Server
Bước 1: Đóng gói ứng dụng tại máy Local

Di chuyển vào thư mục code và build file JAR:

cd c:/Users/Nathan/java_backend_devops/user-service

./gradlew clean bootJar

Kết quả: File user-service-0.0.1.jar được tạo tại build/libs/.

Bước 2: Truyền file JAR lên Server qua lệnh SCP

# Đẩy file JAR lên thư mục tạm /tmp trên server (IP giả định: 10.0.1.15)

scp build/libs/user-service-0.0.1.jar user@10.0.1.15:/tmp/

Bước 3: SSH vào Server để di chuyển file và cấp quyền

# Kết nối tới server

ssh user@10.0.1.15

# Di chuyển file từ thư mục tạm sang thư mục ứng dụng chính thức

sudo mv /tmp/user-service-0.0.1.jar /opt/quickbite/

# Cấp quyền sở hữu cho user chạy dịch vụ

sudo chown quickbite:quickbite /opt/quickbite/user-service-0.0.1.jar

 

Bước 4: Khởi động lại dịch vụ bằng Systemd

# Reload cấu hình Systemd

sudo systemctl daemon-reload

# Restart dịch vụ để tải file JAR mới

sudo systemctl restart quickbite-order

# Kiểm tra trạng thái hoạt động

sudo systemctl status quickbite-order

Nếu màn hình hiển thị trạng thái active (running), ứng dụng đã chạy. Tuy nhiên, nếu bạn sửa đổi tiếp code, bạn sẽ phải lặp lại toàn bộ 4 bước gõ tay này từ đầu!

5. Tổng kết
Triển khai thủ công là nguồn cơn của sự chậm trễ, sai lệch môi trường (Environment Drift) và rủi ro lỗi con người cực kỳ lớn.
Systemd chỉ giúp quản lý tiến trình cục bộ trên một server (tự restart khi crash) chứ không giải quyết bài toán tự động đưa code từ máy lập trình viên lên môi trường thực tế.
Hướng đi tiếp theo Chúng ta sẽ nghiên cứu CI/CD và Docker để giải thoát bản thân khỏi đống lệnh gõ tay nhàm chán này, tự động hóa hoàn toàn quy trình đóng gói và triển khai.
 