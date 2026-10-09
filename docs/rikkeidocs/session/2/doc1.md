Bài đọc: Khái niệm container và so sánh Docker với máy ảo
1. Vấn đề thực tế (Xung đột Runtime giữa các microservices)
Hãy tưởng tượng bạn đang quản lý môi trường chạy thử (Staging) cho hệ thống QuickBite trên một VPS. Lúc này, hệ thống đã phát triển lên 2 dịch vụ độc lập:

user-service (Dịch vụ quản lý người dùng): Được phát triển từ trước, đang chạy ổn định trên Java 17 (do vướng một số thư viện Spring Security và JWT cũ chưa tương thích với bản mới).
restaurant-service (Dịch vụ đặt món): Được phát triển sau, sử dụng các tính năng mới của Java 21 (như Virtual Threads để tối ưu hiệu năng).
Nhiệm vụ của bạn là phải triển khai đồng thời cả 2 dịch vụ này lên cùng một máy chủ VPS. Bạn sẽ lập tức đối mặt với hai lựa chọn đầy "đau đớn":
Phương án 1: Triển khai trực tiếp trên hệ điều hành máy chủ (Native Run)
Bạn phải cài đặt cùng lúc JDK 17 và JDK 21 trên cùng một server.
Bạn sẽ phải cấu hình thủ công các biến môi trường JAVA_HOME riêng biệt cho từng tiến trình, gọi dịch vụ bằng các đường dẫn tuyệt đối phức tạp. Chỉ cần một đợt cập nhật hệ điều hành hoặc cấu hình nhầm biến môi trường, một trong hai dịch vụ sẽ bị lỗi crash phiên bản Class (UnsupportedClassVersionError).
Phương án 2: Sử dụng Máy ảo (Virtual Machine - VM)
Để cô lập hoàn toàn môi trường, bạn tạo ra 2 máy ảo riêng biệt (VM 1 chạy JDK 17 để chạy user-service, VM 2 chạy JDK 21 để chạy restaurant-service).
Việc này giải quyết được xung đột Java, nhưng cực kỳ lãng phí: Bạn phải mất thêm khoảng 2GB RAM chỉ để chạy 2 Hệ điều hành khách (Guest OS) nền. Máy chủ VPS của bạn sẽ nhanh chóng cạn kiệt tài nguyên.
 

Công nghệ Container (Docker) ra đời để giải quyết triệt để bài toán này: Cho phép cô lập hoàn toàn môi trường runtime Java của từng dịch vụ mà không hề gây lãng phí tài nguyên hệ thống.

 

2. Hình ảnh ẩn dụ dễ hiểu: Biệt thự đơn lập vs Căn hộ chung cư
Để dễ hình dung nhất về sự khác biệt giữa Máy ảo (VM) và Container (Docker), hãy so sánh chúng với hai mô hình nhà ở thực tế:



2.1. Máy ảo (VM) giống như "Biệt thự đơn lập"
Mỗi biệt thự được xây dựng trên một nền móng riêng biệt, có hệ thống điện nước, tường bao và mái nhà độc lập (tương ứng với Guest OS).
Ứng dụng: Bạn có thể thiết kế nhà thứ nhất (user-service) theo phong cách cổ điển (JDK 17) và nhà thứ hai (restaurant-service) theo phong cách hiện đại (JDK 21) mà không sợ ảnh hưởng đến nhau.
Nhược điểm: Chi phí xây dựng cực kỳ đắt đỏ, tốn diện tích đất (tốn RAM và dung lượng ổ cứng cho Guest OS).
2.2. Container (Docker) giống như "Căn hộ chung cư"
Các căn hộ nằm chung trong một tòa nhà, chia sẻ chung nền móng, khung cột chịu lực và đường ống nước chính của tòa nhà (tương ứng với Shared Kernel - dùng chung nhân Host OS).
Ứng dụng: Bên trong căn hộ của user-service, bạn trang trí nội thất kiểu cổ điển (chứa JDK 17), căn hộ của restaurant-service trang trí kiểu hiện đại (chứa JDK 21). Môi trường runtime bên trong mỗi căn hộ hoàn toàn độc lập và không hề xung đột với nhau.
Ưu điểm: Xây dựng cực nhanh, chi phí rất rẻ, tiết kiệm diện tích tối đa (tiêu hao CPU/RAM tối giản vì không cần xây móng riêng).
3. Kiến trúc cốt lõi (Máy ảo vs Container)
3.1. Bảng so sánh kỹ thuật trực diện
Tiêu chí

Máy ảo (Virtual Machine - VM)

Container (Docker Container)

Cơ chế ảo hóa

Ảo hóa ở tầng phần cứng (Phần mềm Hypervisor tạo ra CPU ảo, RAM ảo).

Ảo hóa ở tầng hệ điều hành (Chạy trực tiếp trên OS máy chủ thông qua Docker Engine).

Hệ điều hành

Mỗi VM mang theo một Guest OS đầy đủ nặng hàng GB.

Không có Guest OS, dùng chung nhân (Kernel) của Host OS.

Môi trường Java

Mỗi VM bắt buộc phải cấu hình và chạy JDK riêng trên hệ điều hành khách đó.

Mỗi Container đóng gói kèm một phiên bản JDK riêng biệt (chỉ chạy trong container đó).

Tốc độ boot

Chậm (hàng phút) vì phải chờ Guest OS khởi động lại từ đầu.

Gần như tức thì (mili-giây) vì chỉ khởi động tiến trình dịch vụ.

Sử dụng tài nguyên

Khóa cứng tài nguyên tĩnh (Cấp 2GB RAM là mất luôn 2GB RAM cho máy chủ).

Tiêu hao tài nguyên động theo nhu cầu thực tế của dịch vụ.

3.2. Sơ đồ kiến trúc phần cứng chi tiết
Dưới đây là sơ đồ so sánh trực diện kiến trúc phần cứng của Máy ảo và Docker Container:



Điểm mấu chốt cần nhớ:

Trong mô hình Container, lớp Guest OS và Hypervisor nặng nề đã được thay thế hoàn toàn bằng Docker Engine gọn nhẹ. Các container chạy trực tiếp trên nhân hệ điều hành của Host OS giúp đạt hiệu năng tiệm cận dịch vụ chạy trực tiếp (Native).

3.3. Bản chất kỹ thuật của Container: Namespaces & Cgroups
Bạn cần lưu ý một điểm mấu chốt: Container thực chất chỉ là một tiến trình (Process) Linux bình thường, nhưng được nhân Linux bao bọc bởi 2 công cụ cô lập:

Namespaces (Phân vùng cô lập):
Giống như việc lắp cửa khóa và tường ngăn giữa các căn hộ chung cư.
Namespaces tạo ra một chiếc "kính thực tế ảo" khiến tiến trình container tin rằng nó đang chạy độc lập trên một hệ điều hành riêng. 
Ví dụ: Nhờ NET Namespace, container chứa user-service và container chứa restaurant-service đều có thể tự do cấu hình lắng nghe tại cổng nội bộ 8080 mà không hề gây xung đột cổng mạng trên máy host.

Cgroups (Control Groups - Giới hạn tài nguyên):
Giống như công tơ điện và đồng hồ nước giới hạn mức tiêu thụ tối đa của từng căn hộ.
Cgroups giới hạn lượng CPU, RAM tối đa mà container được phép dùng, tránh trường hợp một dịch vụ bị treo làm ảnh hưởng đến dịch vụ còn lại.
4. Hiểu lầm thường gặp & Rủi ro bảo mật
Hiểu lầm: Docker Container an toàn bảo mật tuyệt đối hơn Máy ảo (VM).
Sự thật: Vì dùng chung nhân hệ điều hành máy chủ (Shared Kernel), nếu một lỗ hổng bảo mật nghiêm trọng xuất hiện trong nhân Linux (Kernel Vulnerability), hacker nằm trong container có thể khai thác để "vượt ngục" (Container Escape) chiếm quyền điều khiển toàn bộ máy chủ vật lý. Trong khi đó, VM có lớp Guest OS riêng biệt nên việc tấn công xuyên qua Hypervisor để sang máy chủ host là cực kỳ khó khăn.
 

Lời khuyên thực chiến:

Trong môi trường Staging/Dev, hãy dùng Docker thoải mái để tiết kiệm RAM. Tuy nhiên trên Production, người ta thường thuê các máy ảo (VM) lớn từ Cloud (AWS EC2, Google Compute Engine) để đảm bảo độ cô lập bảo mật phần cứng, sau đó chạy các Docker Container bên trong các VM đó để quản lý dịch vụ linh hoạt.

5. Tài liệu tham khảo thêm
Để kiểm chứng các thông tin kỹ thuật được nêu trong bài học, học viên có thể tham khảo trực tiếp các nguồn tài liệu uy tín dưới đây:

Khái niệm và kiến trúc Container chính thức từ Docker:
What is a Container? - Docker Official

So sánh chi tiết Container với Máy ảo từ Microsoft:
Containers vs. Virtual Machines - Microsoft Learn

Tài liệu hướng dẫn (Manual Pages) của Linux về cơ chế Namespaces:
Linux Namespaces man-pages - man7.org

Tài liệu hướng dẫn của Linux về cơ chế Cgroups:
Linux Control Groups cgroups man-pages - man7.org