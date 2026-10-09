Bài đọc: Khái niệm Reverse Proxy và vai trò của Nginx trong kiến trúc Microservices
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Phân biệt được nguyên lý hoạt động của Forward Proxy so với Reverse Proxy.
Giải thích được vai trò của Nginx làm cổng kết nối duy nhất (Single Entry Point) bảo vệ hệ thống QuickBite trên production.
Mô tả được sơ đồ luồng di chuyển của dữ liệu (Traffic Flow) đi từ client ngoài Internet qua Nginx Reverse Proxy trước khi đập vào API Gateway.
2. Vấn đề thực tế
Hiện tại, cụm container microservices của dự án QuickBite (như user-service, restaurant-service, api-gateway) đã chạy độc lập và an toàn bên trong VPS. Để chạy thử ở local hoặc test nhanh, chúng ta thường map trực tiếp cổng các container ra ngoài host (ví dụ: 8080:8080 cho API Gateway, 8081:8081 cho User Service...).

Tuy nhiên, khi đưa hệ thống lên môi trường Production thực tế, cách làm này bộc lộ các điểm yếu nghiêm trọng:

Tấn công diện rộng (Attack Surface): Việc mở toang quá nhiều cổng mạng ra Internet tạo điều kiện cho hacker thực hiện quét cổng, khai thác trực tiếp lỗ hổng bảo mật của từng service.
Quản trị phức tạp: Client (Web/Mobile) phải ghi nhớ và kết nối trực tiếp đến nhiều cổng khác nhau, gây khó khăn cho việc cấu hình bảo mật SSL/HTTPS và chính sách CORS.
Không thể che giấu hạ tầng: Hacker dễ dàng biết được hệ thống đang có các service nào chạy trên các cổng nào để lên kịch bản tấn công.
Do đó, chúng ta cần một thành phần duy nhất đứng ở "mặt tiền" (cổng 80/443), đại diện cho toàn bộ hệ thống để tiếp nhận request và bẻ hướng định tuyến vào bên trong. Thành phần đó chính là Nginx Reverse Proxy.

3. Kiến thức cốt lõi
3.1. Forward Proxy vs Reverse Proxy
Để tránh nhầm lẫn, chúng ta phân biệt rõ hai khái niệm proxy phổ biến:

Tiêu chí

Forward Proxy

Reverse Proxy (Nginx)

Vị trí đứng

Đứng trước Client (đại diện cho Client)

Đứng trước Server (đại diện cho Server)

Mục đích

Giúp Client đi ra Internet ẩn danh, vượt tường lửa (Ví dụ: VPN, Proxy công ty chặn web đen).

Đón nhận request từ Internet gửi vào các server nội bộ ẩn phía sau.

Nhận biết

Client biết rõ mình đang qua Proxy. Server đích chỉ thấy IP của Proxy chứ không thấy IP thật của Client.

Client hoàn toàn không biết cấu trúc server phía sau, chỉ nghĩ đang tương tác trực tiếp với Reverse Proxy.


3.2 Vai trò của Nginx làm Reverse Proxy trong Microservices
Nginx (Engine X) là một phần mềm mã nguồn mở rất nhẹ, hiệu năng cực kỳ cao nhờ kiến trúc hướng sự kiện (Event-driven). Khi làm Reverse Proxy cho hệ thống QuickBite, Nginx mang lại các lợi ích vượt trội:

Single Entry Point (Điểm vào duy nhất): Tường lửa VPS chỉ cần mở duy nhất cổng 80 (HTTP) và 443 (HTTPS) trỏ vào Nginx. Toàn bộ các cổng microservices khác đều có thể được chặn lại hoặc cô lập an toàn ở localhost.
Ẩn giấu hạ tầng (Security Hardening): Client bên ngoài chỉ giao tiếp với Nginx, hoàn toàn không biết địa chỉ mạng nội bộ, cổng chạy, hay ngôn ngữ phát triển của các service phía sau.
SSL Termination: Nginx đảm nhận việc giải mã HTTPS (cổng 443) và chuyển tiếp HTTP thường vào mạng nội bộ. Việc này giúp giảm tải tính toán mã hóa cho các container Spring Boot.
Tải tĩnh (Static Content Caching): Nginx có thể trả trực tiếp các file HTML/CSS/JS của trang quản trị (Admin Dashboard) hay hình ảnh sản phẩm cực kỳ nhanh mà không cần gọi đến Spring Boot.
4. Demo và thực hành (Sơ đồ Traffic Flow trên VPS)
Trong kịch bản triển khai thực tế của Session 11, luồng di chuyển dữ liệu (Traffic Flow) từ Client ở ngoài Internet đi vào hệ thống QuickBite được thực hiện tuần tự như sau:



Client gửi yêu cầu: Client dùng Postman gửi request lấy danh sách món ăn: http://<vps_public_ip>/api/v1/restaurants. Request này đập vào cổng 80 của VPS và được tiếp nhận bởi Nginx.
Nginx bẻ hướng định tuyến: Nginx đọc cấu hình, phát hiện tiền tố /api. Nó lập tức chuyển hướng request này tới cổng của API Gateway đang lắng nghe trên localhost: http://127.0.0.1:8080/api/v1/restaurants.
API Gateway điều phối: API Gateway tiếp nhận request từ Nginx, giải mã và thực hiện định tuyến nội bộ đến restaurant-service ở cổng 8082 để lấy dữ liệu trả về cho Nginx, rồi Nginx phản hồi lại cho Client.