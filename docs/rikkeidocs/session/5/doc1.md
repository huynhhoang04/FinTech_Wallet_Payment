Bài đọc: Kiến trúc đa dịch vụ và thiết kế thực thể trong hệ thống phân tán QuickBite
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, bạn sẽ có khả năng:

● Giải thích được cấu trúc đa dịch vụ (Multi-services Architecture) của nền tảng QuickBite và vai trò của từng thành phần.

● Hiểu bản chất cấu trúc dữ liệu và thực thể của 4 dịch vụ cốt lõi: user-service, restaurant-service, order-service và notification-service.

● Phân tích và thiết kế được giải pháp bảo toàn dữ liệu lịch sử thông qua cơ chế Snapshot (lưu thông tin tên khách hàng, tên cửa hàng, tên món ăn và giá bán trực tiếp tại thời điểm đặt đơn thay vì chỉ liên kết ID).

● Nắm rõ nguyên tắc cô lập dữ liệu (Database-per-service), hiểu lý do không thể sử dụng các quan hệ liên kết trực tiếp ở mức database (như @OneToMany hay @ManyToMany xuyên cơ sở dữ liệu) giữa các dịch vụ.

 

2. Vấn đề thực tế (Thiết lập hệ thống phân tán và lưu trữ lịch sử)
Khi chuyển đổi từ một hệ thống chạy đơn bản (Monolith) sang kiến trúc đa dịch vụ chạy độc lập, chúng ta gặp những thay đổi lớn về cách tổ chức cơ sở dữ liệu:

 

2.1. Ràng buộc dữ liệu trong kiến trúc Database-per-Service:
Trong hệ thống monolithic truyền thống, tất cả các bảng đều nằm chung một cơ sở dữ liệu. Việc lấy thông tin người dùng từ đơn hàng rất đơn giản bằng cách định nghĩa khóa ngoại hoặc liên kết @ManyToOne User trong Hibernate.
Tuy nhiên, khi chuyển sang microservices, user-service quản lý database quickbite_user_db, còn restaurant-service quản lý quickbite_restaurant_db. Do nằm ở các database logic khác nhau (thậm chí có thể chạy trên các server vật lý khác nhau), chúng ta không thể thiết lập quan hệ khóa ngoại vật lý và cũng không thể thực hiện các câu lệnh SQL JOIN trực tiếp hay các liên kết JPA truyền thống. Ví dụ: user-service không thể truy cập thông tin của restaurant-service thông qua mối quan hệ @OneToMany restaurants. Mọi giao tiếp và truy vấn chéo bắt buộc phải thực hiện qua giao diện mạng (REST API) sử dụng các HTTP Client như FeignClient.
2.2. Bài toán bảo toàn lịch sử dữ liệu (Snapshot Pattern):
Giả sử trong bảng chi tiết đơn hàng, lập trình viên chỉ lưu menuItemId để tham chiếu tới món ăn trong thực đơn của restaurant-service.
Thực tế vận hành cho thấy: Hôm nay khách hàng đặt món "Trà sữa trân châu" với giá 30.000đ. Ngày mai, nhà hàng cập nhật giá món ăn này lên 40.000đ hoặc đổi tên món thành "Trà sữa đặc biệt". Nếu hệ thống chỉ lưu ID và truy cập động từ restaurant-service, khi khách hàng xem lại lịch sử đơn hàng cũ, hóa đơn sẽ hiển thị sai lệch thông tin và giá tiền so với thời điểm họ mua.
Để giải quyết việc này, hệ thống cần chụp lại trạng thái dữ liệu (Snapshot) tại thời điểm giao dịch phát sinh. Các thông tin động dễ thay đổi như tên khách hàng (customerName), tên nhà hàng (merchantName), tên món ăn (itemName), và giá món (price) cần được sao chép và lưu cứng trực tiếp vào đơn hàng.
 

3. Kiến thức cốt lõi (Mô hình dữ liệu trong hệ thống phân tán)
3.1. Mô hình Database-per-Service
Mỗi dịch vụ sở hữu cơ sở dữ liệu logic riêng để đảm bảo tính cô lập và tự chủ:

 

3.2. Cơ chế Snapshot trong lưu trữ giao dịch
Khi một đơn hàng được tạo ra, dữ liệu từ các dịch vụ khác sẽ được chụp lại và lưu trực tiếp vào database của order-service:



 

3.3 Liên kết lỏng qua ID (Soft References) và Giao tiếp REST API
Để liên kết thông tin giữa các dịch vụ, các thực thể không dùng liên kết JPA trực tiếp (như @ManyToOne) mà chỉ lưu trữ khóa chính của thực thể đối tác dưới dạng một trường số nguyên thông thường (Long).

● Ví dụ: Thực thể Order lưu trường customerId (kiểu Long) thay vì đối tượng User.

● Khi cần lấy thông tin chi tiết của người dùng, dịch vụ sẽ gọi API sang user-service thông qua một FeignClient (công cụ khai báo REST client trực quan của Spring Cloud):

@FeignClient(name = "user-service")

public interface UserServiceClient {

    @GetMapping("/users/{id}")

    UserDTO getUserById(@PathVariable("id") Long id);

}

 

4. Thiết kế cấu trúc bảng dữ liệu
Dưới đây là thiết kế cấu trúc các bảng dữ liệu logic của hệ thống QuickBite, thể hiện rõ các khóa chính (PK), khóa ngoại nội bộ (FK) và các trường tham chiếu lỏng sang dịch vụ khác (Soft Ref):

 

4.1. Cơ sở dữ liệu: quickbite_user_db (Dịch vụ user-service)
Bảng users (Quản lý tài khoản và vai trò):
Bảng user_addresses (Danh sách địa chỉ nhận hàng):
Bảng user_wallets (Ví tiền giả lập để thanh toán):


4.2. Cơ sở dữ liệu: quickbite_restaurant_db (Dịch vụ restaurant-service)
Bảng restaurants (Thông tin cửa hàng):
Bảng menu_categories (Danh mục thực đơn):
Bảng menu_items (Danh sách món ăn trong thực đơn):
 

 

4.3. Cơ sở dữ liệu: quickbite_order_db (Dịch vụ order-service)
Bảng orders (Quản lý đơn hàng tổng quan):
Bảng order_items (Chi tiết món ăn trong đơn hàng):
Bảng order_status_history (Lưu vết lịch sử trạng thái đơn hàng):


 

4.4. Cơ sở dữ liệu: quickbite_notification_db (Dịch vụ notification-service)
Bảng notifications (Danh sách thông báo):


5. Hiểu lầm thường gặp (Foreigh key trong Microservices)
Hiểu lầm thường gặp: Có thể duy trì quan hệ khóa ngoại vật lý giữa bảng orders (trong database quickbite_order_db) và bảng users (trong database quickbite_user_db) nếu ta trỏ đúng địa chỉ kết nối IP.
Sự thật: Không thể tạo được. Khóa ngoại vật lý chỉ có thể được thiết lập khi hai bảng nằm trong cùng một cơ sở dữ liệu logic hoạt động trên cùng một công cụ lưu trữ. Khi đã phân tách thành Database-per-service, các cơ sở dữ liệu hoạt động hoàn toàn độc lập. Việc liên kết lỏng qua trường dữ liệu ID (Long) là giải pháp bắt buộc, đi kèm với việc sử dụng giao tiếp API (như Spring Cloud OpenFeign) để truy vấn thông tin khi cần thiết.
 

6. Tài liệu tham khảo
Mẫu thiết kế Database-per-Service:

Database per Service Pattern - Microservices.io

Khai báo giao tiếp mạng trong Spring với OpenFeign:

Spring Cloud OpenFeign Official Documentation

