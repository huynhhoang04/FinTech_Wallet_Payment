Bài đọc: Grafana và vai trò trong giám sát hệ thống
 

PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được vai trò của Grafana trong kiến trúc giám sát (Monitoring stack).
Phân tích được sự khác biệt giữa giao diện thô của Prometheus và giao diện trực quan hóa chuyên nghiệp của Grafana.
Mô tả luồng dữ liệu truyền tải giữa các thành phần: Target -> Prometheus Server -> Grafana.
Cài đặt thành công Grafana sử dụng Docker Compose trong cụm hạ tầng.
 

PHẦN 2. VẤN ĐỀ THỰC TẾ
Sau khi cài đặt xong Prometheus ở Session 13, chúng ta đã có một kho lưu trữ dữ liệu thời gian (TSDB) chứa đầy đủ các metrics từ microservices và phần cứng VPS. Tuy nhiên, giao diện web mặc định của Prometheus ở cổng 9090 lại gặp các vấn đề lớn:

Giao diện rất thô sơ, chỉ hiển thị biểu đồ đơn lẻ cho từng câu lệnh truy vấn PromQL, không thể xem đồng thời nhiều biểu đồ.
Không thể tạo và lưu trữ các Dashboard (bảng điều khiển) hoàn chỉnh, đẹp mắt để chia sẻ cho các thành viên trong đội ngũ vận hành hoặc báo cáo cho quản lý.
Không hỗ trợ cơ chế phân quyền người dùng (Role-based Access Control - RBAC) và không có mật khẩu bảo mật đăng nhập mặc định.
Để giải quyết vấn đề này, chúng ta cần một công cụ chuyên biệt đứng ở tầng hiển thị (Visualization Layer) để kết nối vào Prometheus và vẽ lên các biểu đồ trực quan, sinh động. Công cụ hàng đầu thế giới hiện nay cho việc này chính là Grafana.

 

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Grafana là gì?
Grafana là một nền tảng mã nguồn mở chuyên về trực quan hóa (visualization) và phân tích dữ liệu dạng chuỗi thời gian (time-series).
Grafana không tự lưu trữ dữ liệu (ngoại trừ cấu hình dashboard và người dùng). Nó đóng vai trò là một "lớp áo ngoài" kết nối tới các nguồn dữ liệu khác nhau (Data Sources) như Prometheus, InfluxDB, Elasticsearch, MySQL... để truy vấn dữ liệu và vẽ biểu đồ.
3.2 Sự bổ trợ giữa Prometheus và Grafana
Trong hệ thống giám sát tiêu chuẩn, Prometheus và Grafana kết hợp với nhau như sau:

Prometheus (Backend): Đóng vai trò là cơ sở dữ liệu và công cụ thu thập (Data Source). Nó đi quét metrics từ các target và lưu trữ vào ổ đĩa.
Grafana (Frontend): Đóng vai trò hiển thị. Nó gửi các câu truy vấn PromQL đến Prometheus API và biểu diễn kết quả trả về dưới dạng đồ thị, biểu đồ hình cột, bảng số liệu hoặc các cảnh báo màu sắc sinh động.
3.3 Sơ đồ kiến trúc luồng dữ liệu giám sát

PHẦN 4. DEMO VÀ THỰC HÀNH (CÀI ĐẶT GRAFANA BẰNG DOCKER COMPOSE)
4.1 Cập nhật tệp docker-compose.yml hạ tầng
Mở tệp docker-compose.yml của dự án hạ tầng QuickBite và bổ sung dịch vụ Grafana chạy chung mạng nội bộ với Prometheus:

version: '3.8'
 
services:
  # ... các services khác như prometheus, node-exporter giữ nguyên ...
 
  grafana:
    image: grafana/grafana:10.0.3
    container_name: quickbite-grafana
    ports:
      - "3000:3000" # Map cổng 3000 ra ngoài để truy cập giao diện
    volumes:
      # Sử dụng volume để lưu trữ cấu hình dashboards, users của Grafana
      - grafana-data:/var/lib/grafana
    networks:
      - quickbite-net
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin_password_bao_mat # Đặt mật khẩu admin mặc định
    restart: unless-stopped
 
volumes:
  grafana-data:
    driver: local
 
networks:
  quickbite-net:
    driver: bridge

4.2 Khởi chạy dịch vụ và đổi mật khẩu quản trị
Khởi chạy container Grafana:
docker compose up -d grafana

Kiểm tra container đã chạy ổn định:
docker compose ps

Truy cập vào giao diện Grafana qua trình duyệt: http://<vps_public_ip>:3000.
Đăng nhập với tài khoản mặc định:
Username: admin
Password: admin_password_bao_mat (hoặc mật khẩu bạn đã cấu hình trong biến môi trường).
Hệ thống sẽ yêu cầu bạn đổi mật khẩu mới ngay lần đăng nhập đầu tiên để đảm bảo an toàn.