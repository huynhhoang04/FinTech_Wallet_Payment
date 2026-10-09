Bài đọc: Quản lý vòng đời hệ thống với Docker Compose
1. Vấn đề thực tế (Ma trận lỗi khi khởi chạy đồng thời)

Hãy tưởng tượng bạn vừa thực hiện lệnh khởi chạy toàn bộ hệ thống QuickBite bằng Docker Compose:

docker compose up -d

Màn hình terminal in ra trạng thái cả quickbite-db và quickbite-user đều báo Started màu xanh lá cây. 

 

Tuy nhiên, khi bạn mở trình duyệt hoặc Postman gửi API request tới cổng 8081 của quickbite-user, hệ thống báo lỗi 500 Internal Server Error hoặc lỗi không phản hồi. Bạn rơi vào ma trận hoang mang:

Database đã thực sự sẵn sàng nhận kết nối chưa?
Hay database đang chạy lỗi khởi động nên sập?
Hay mã nguồn Java của backend bị ném ngoại lệ (Exception) lúc kết nối?
Nếu làm theo cách cũ, bạn phải mở 2 terminal mới chạy 2 lệnh docker logs riêng biệt cho từng container để so khớp dòng thời gian lỗi. Việc này cực kỳ mất thời gian và khiến bạn bối rối không biết nguyên nhân gốc rễ bắt đầu từ đâu.

Để chẩn đoán nhanh và vận hành hệ thống trơn tru, bạn cần làm chủ bộ câu lệnh quản lý vòng đời và chẩn đoán của Docker Compose CLI.

 

2. Kiến thức cốt lõi (Các lệnh điều khiển Docker Compose CLI chủ chốt)
Docker Compose CLI cung cấp các công cụ tương tác trực tiếp với toàn bộ "stack" (ngăn xếp) container của bạn:



2.1. Nhóm lệnh khởi tạo và dừng
docker compose up -d: Khởi chạy toàn bộ hệ thống chạy ngầm dưới nền (Detached mode). Lệnh này sẽ tự động tạo mạng, volume, build image (nếu cấu hình build) và khởi chạy container.
docker compose stop: Tạm dừng hoạt động của các container (trạng thái container chuyển thành Exited), nhưng không xóa bỏ container. Dữ liệu ghi tạm trong container vẫn được giữ nguyên.
docker compose start: Khởi động lại các container đã bị dừng bởi lệnh stop.
docker compose down: Dừng và xóa bỏ hoàn toàn các container và mạng ảo của dự án. Lệnh này giúp dọn sạch RAM và CPU của máy.
 

2.2. Nhóm lệnh chẩn đoán và tương tác
docker compose ps: Liệt kê danh sách các container thuộc dự án hiện hành kèm theo ID, trạng thái hoạt động (Up/Exit), và cổng ánh xạ mạng.
docker compose logs -f: Stream log tổng hợp của tất cả các container trong cụm thời gian thực.
  Điểm ưu việt: Docker Compose sẽ tự động phân tách log của từng dịch vụ bằng các màu sắc khác nhau và chèn thêm tiền tố tên dịch vụ ở đầu dòng (ví dụ: quickbite-user-1  |, quickbite-db-1    |). Điều này giúp bạn dễ dàng theo dõi trình tự lỗi diễn ra giữa các dịch vụ.
docker compose exec [tên_service] [lệnh]: Chạy một lệnh trực tiếp bên trong một container đang hoạt động của cụm (tương tự docker exec).
3. Thực hành: Chẩn đoán và điều khiển cụm container Quickbite
Hãy thực hành quy trình chẩn đoán lỗi tiêu chuẩn của một kỹ sư DevOps:

A. Khởi chạy hệ thống ở chế độ chạy ngầm:

docker compose up -d

B. Kiểm tra danh sách trạng thái của cụm container:

docker compose ps

Kết quả mong đợi: Màn hình hiển thị bảng danh sách các container, đảm bảo cột STATUS hiển thị Up cho cả database và backend.
C. Stream log tổng hợp để giám sát quá trình khởi động:

docker compose logs -f --tail=50

Quan sát cách các dòng log đan xen nhau. Nhấn Ctrl + C để thoát chế độ xem log (lưu ý: việc thoát logs không làm dừng container).
D. Kiểm tra xem database PostgreSQL đã thực sự sẵn sàng nhận kết nối hay chưa bằng công cụ chẩn đoán nội bộ của Postgres:

docker compose exec quickbite-db pg_isready -U postgres

Kết quả mong đợi: Console trả về thông báo: /var/run/postgresql:5432 - accepting connections.
E. Dọn dẹp sạch sẽ tài nguyên hệ thống sau khi làm việc xong:

docker compose down

 

4. Hiểu lầm thường gặp (Docker compose down vs stop)
Hiểu sai: Lệnh docker compose down sẽ xóa sạch toàn bộ dữ liệu của tôi trong database.
Sự thật: Không hề.
Lệnh docker compose down chỉ xóa container và network ảo. Dữ liệu nằm trong Named Volumes vật lý trên máy host vẫn được giữ an toàn 100%. Khi bạn gõ docker compose up -d trở lại, dữ liệu database cũ sẽ tự động xuất hiện.
Trường hợp mất dữ liệu thật: Chỉ xảy ra khi bạn cố tình truyền thêm cờ xóa volume: docker compose down -v (hoặc --volumes). Cờ -v sẽ ép Docker xóa sạch cả Named Volumes lưu trữ. Hãy cực kỳ cẩn thận với cờ này trên môi trường Staging/Production!
 

5. Tài liệu tham khảo
Tài liệu tra cứu lệnh Docker Compose CLI:
Docker Compose CLI Command Reference - Docker Docs

Chi tiết về lệnh docker compose down:
Docker Compose Down command reference - Docker Docs