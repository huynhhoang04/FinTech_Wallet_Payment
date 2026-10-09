Bài đọc: Triển khai Prometheus bằng Docker Compose thu thập chỉ số Backend
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Khởi chạy thành công container Prometheus bằng Docker Compose trên máy chủ VPS.
Biên soạn tệp tin cấu hình prometheus.yml để định kỳ tự động quét chỉ số các microservices sử dụng cơ chế phân giải tên miền của Docker Network.
Cấu hình volume để lưu trữ lâu dài dữ liệu metrics của Prometheus, tránh mất lịch sử giám sát khi container bị dừng hoặc nâng cấp.
Giải thích được các nguyên tắc bảo mật mạng khi triển khai máy chủ Prometheus (không expose cổng 9090).
PHẦN 2. VẤN ĐỀ THỰC TẾ
Các microservices backend của QuickBite đã phơi bày dữ liệu chỉ số tại các cổng /actuator/prometheus nội bộ. Thế nhưng, tại mỗi thời điểm, các container Java chỉ hiển thị các con số tức thời (chỉ số real-time) chứ không có khả năng lưu trữ lịch sử dữ liệu (ví dụ: RAM 5 phút trước là bao nhiêu byte).

Để có thể vẽ được biểu đồ trực quan, chúng ta cần một bộ não trung tâm có nhiệm vụ:

Định kỳ chủ động đi gõ cửa từng microservice để lấy metrics về.
Ghi nhận và lưu trữ toàn bộ các metrics kèm mốc thời gian vào đĩa cứng của máy chủ VPS để làm cơ sở phân tích xu hướng tải.
Công cụ giải quyết xuất sắc nhiệm vụ này chính là Prometheus Server. Chúng ta cần đóng gói và vận hành Prometheus dưới dạng một container Docker chạy chung mạng ảo với các service để kéo chỉ số dễ dàng.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Cấu trúc tệp cấu hình prometheus.yml
Tệp cấu hình của Prometheus điều khiển toàn bộ hành vi thu thập dữ liệu thông qua định dạng YAML:

Khối global: Chứa cấu hình toàn cục.
scrape_interval: 15s: Định nghĩa chu kỳ kéo metrics (cứ mỗi 15 giây Prometheus sẽ gửi request HTTP GET tới các target một lần).
Khối scrape_configs: Khai báo danh sách các công việc quét chỉ số (jobs). Mỗi job bao gồm:
job_name: Tên phân biệt công việc quét.
metrics_path: Đường dẫn endpoint (mặc định là /metrics, đối với Spring Boot là /actuator/prometheus).
static_configs.targets: Danh sách các địa chỉ IP/Port hoặc tên DNS của các target cần kéo dữ liệu.
3.2 Tận dụng DNS nội bộ của Docker Network
Do Prometheus container được đặt chung trong mạng ảo Bridge với các microservices backend (ví dụ: mạng quickbite-net), Prometheus có khả năng phân giải tên miền (Service Discovery) dựa trên tên của các container khai báo trong Docker Compose.

Do đó, trong danh sách targets của file cấu hình, chúng ta không ghi địa chỉ IP thô (như 172.18.0.3:8083) vì IP của container sẽ bị đổi mỗi khi khởi động lại. Chúng ta viết trực tiếp tên dịch vụ:
targets: ['order-service:8083']

Docker Engine sẽ tự động phân giải tên

order-service

thành địa chỉ IP động chính xác của container đó trong mạng nội bộ.

3.3 Đảm bảo an toàn cổng 9090 của Prometheus
Mặc định, Prometheus Server lắng nghe ở cổng 9090 và cung cấp một giao diện web thô sơ để kiểm tra dữ liệu và viết câu lệnh truy vấn.

[!CAUTION] Giao diện web mặc định này của Prometheus không tích hợp sẵn cơ chế bảo mật xác thực (No Authentication). Bất kỳ ai biết được IP VPS của bạn và cổng 9090 đều có thể truy cập xem toàn bộ thông số hạ tầng hoặc gửi lệnh xóa sạch dữ liệu. Do đó, trên Production, tuyệt đối không map cổng 9090:9090 ra ngoài máy host. Cổng này chỉ được mở nội bộ bên trong Docker Network để dịch vụ hiển thị đồ họa (Grafana) kết nối an toàn.

PHẦN 4. DEMO VÀ THỰC HÀNH (TRIỂN KHAI PROMETHEUS TRÊN VPS)
Học viên thực hiện thiết lập cấu hình và khởi chạy Prometheus trên máy chủ VPS.

4.1 Tạo tệp cấu hình prometheus.yml trên VPS
Tạo thư mục chứa cấu hình trên VPS:
mkdir -p ~/projects/quickbite-infra/prometheus
cd ~/projects/quickbite-infra/prometheus

Tạo file cấu hình prometheus.yml:
nano prometheus.yml

Nhập nội dung cấu hình sau:
global:
  scrape_interval: 15s # Quét chỉ số mỗi 15 giây một lần
  evaluation_interval: 15s
 
scrape_configs:
  - job_name: 'quickbite-backend'
    metrics_path: '/actuator/prometheus'
    static_configs:
      # Gọi trực tiếp qua DNS nội bộ của Docker
      - targets:
          - 'user-service:8081'
          - 'restaurant-service:8082'
          - 'order-service:8083'
          - 'notification-service:8084'

Lưu và thoát nano.
4.2 Cập nhật tệp docker-compose.yml hạ tầng của dự án
Di chuyển ra thư mục gốc chứa file docker-compose.yml của dự án hạ tầng QuickBite và thêm dịch vụ Prometheus:

version: '3.8'
 
services:
  # ... các backend services giữ nguyên cấu hình ...
 
  prometheus:
    image: prom/prometheus:v2.45.0
    container_name: quickbite-prometheus
    volumes:
      # Mount file cấu hình từ VPS vào container
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      # Sử dụng volume để lưu trữ lâu dài cơ sở dữ liệu metrics TSDB
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    networks:
      - quickbite-net
    # KHÔNG expose cổng 9090 ra ngoài host để bảo mật
 
volumes:
  prometheus-data:
    driver: local
 
networks:
  quickbite-net:
    driver: bridge

4.3 Khởi chạy dịch vụ và kiểm tra trạng thái
Thực hiện khởi chạy container Prometheus:
docker compose up -d prometheus

Kiểm tra xem container đã chạy ổn định chưa:
docker compose ps

Chạy lệnh truy vấn trực tiếp vào API nội bộ của Prometheus từ bên trong container để kiểm tra trạng thái của các targets:
docker exec -it quickbite-prometheus wget -qO- http://localhost:9090/api/v1/targets

Kết quả mong đợi:

Nhận về dữ liệu JSON hiển thị danh sách các microservices kèm theo trạng thái

"health": "up"

tương ứng.