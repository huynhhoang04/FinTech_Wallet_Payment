Bài đọc: Triển khai Node Exporter giám sát tài nguyên VPS
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được vai trò của Node Exporter trong mô hình giám sát hạ tầng phần cứng.
Cấu hình mount các thư mục hệ thống của hệ điều hành host (/proc, /sys) vào container ở chế độ chỉ đọc (Read-Only) an toàn.
Cấu hình Prometheus định kỳ quét và thu thập các chỉ số phần cứng VPS từ Node Exporter.
Phân tích được sự khác biệt giữa giám sát cấp hệ điều hành (Node Exporter) và giám sát cấp máy ảo ứng dụng (JVM Actuator).
PHẦN 2. VẤN ĐỀ THỰC TẾ
Giám sát sức khỏe của các container microservices Java mới chỉ là một nửa bức tranh. Trong quá trình vận hành thực tế trên Production, máy chủ VPS của bạn có thể bị sập hoặc ngừng hoạt động do các sự cố phần cứng cấp thấp:

Ổ đĩa cứng SSD của VPS bị đầy (100% disk usage) do log file phình to, khiến database không thể ghi thêm dữ liệu và crash.
Tài nguyên CPU của VPS bị nghẽn 100% do có tiến trình chạy lặp vô tận, làm treo toàn bộ web server.
Băng thông mạng của VPS bị cạn kiệt do bị tấn công hoặc tải file quá nhiều.
Bản thân máy chủ Prometheus hay các container Java Spring Boot không thể tự đọc được các thông số phần cứng này của hệ điều hành Linux (Ubuntu) máy host do cơ chế cô lập của container. Chúng ta cần một công cụ chuyên biệt chạy ở cấp hệ thống để đo đạc toàn bộ tài nguyên VPS và xuất dữ liệu ra cho Prometheus. Công cụ đó chính là Node Exporter.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Node Exporter là gì?
Node Exporter: Là một agent giám sát chính thức được phát triển bởi cộng đồng Prometheus. Nó có nhiệm vụ đo đạc và hiển thị hàng trăm chỉ số liên quan đến nhân kernel của hệ điều hành Linux và phần cứng máy chủ (CPU load, Memory usage, Disk I/O, Network Traffic).
Node Exporter lắng nghe ở cổng mặc định là 9100 và xuất dữ liệu thô tại endpoint /metrics.
3.2 Cơ chế đọc dữ liệu host từ container cô lập
Một container thông thường khi chạy sẽ bị cô lập hoàn toàn (Namespace isolation) và không thể nhìn thấy tài nguyên thực tế của máy host Linux. Để Node Exporter chạy trong container vẫn có thể đo đạc chính xác các thông số phần cứng của VPS, chúng ta áp dụng cơ chế mount đặc biệt:

Mount /proc thành /host/proc:ro: Thư mục /proc là một hệ thống tệp tin ảo của Linux, chứa toàn bộ thông tin về các tiến trình hệ thống, bộ nhớ RAM và trạng thái CPU.
Mount /sys thành /host/sys:ro: Thư mục /sys chứa các thông tin cấu hình và thiết bị phần cứng của nhân Linux.
Mount / thành /rootfs:ro: Thư mục gốc của máy host để đo đạc dung lượng ổ đĩa.
[!IMPORTANT] Toàn bộ các thư mục ảo trên bắt buộc phải được mount với cờ :ro (Read-Only - Chỉ đọc). Việc này đảm bảo container Node Exporter chỉ có quyền đọc các thông số hệ thống để xuất metrics, tuyệt đối không có quyền chỉnh sửa hay can thiệp phá hoại cấu trúc tệp tin của hệ điều hành máy host VPS.

3.3 Node Exporter vs JVM Actuator
DevOps cần phân biệt rõ trách nhiệm của hai công cụ giám sát này:

Tiêu chí

Node Exporter

Spring Boot Actuator

Phạm vi giám sát

Tầng vật lý / Hệ điều hành (VPS Host)

Tầng ứng dụng / Máy ảo (Java JVM)

Chỉ số thu thập

CPU load, dung lượng RAM vật lý, dung lượng đĩa SSD, băng thông mạng card eth0.

Dung lượng JVM Heap, số lượng GC Pauses, số active Spring threads, số request HTTP thành công.

Đối tượng cài đặt

Cài 1 instance duy nhất trên mỗi máy chủ VPS.

Tích hợp vào mã nguồn của từng Microservice con.

PHẦN 4. DEMO VÀ THỰC HÀNH (TÍCH HỢP NODE EXPORTER VÀO QUỐC LỘ GIÁM SÁT)
Học viên thực hiện tích hợp Node Exporter vào cụm Docker Compose và cấu hình Prometheus thu thập dữ liệu trên VPS.

4.1 Cập nhật tệp docker-compose.yml hạ tầng trên VPS
Mở file docker-compose.yml của dự án hạ tầng QuickBite và bổ sung thêm dịch vụ node-exporter chạy chung mạng với Prometheus:

version: '3.8'
 
services:
  # ... các services khác giữ nguyên ...
 
  node-exporter:
    image: prom/node-exporter:v1.6.0
    container_name: quickbite-node-exporter
    # Mount Read-Only các thư mục hệ thống ảo của VPS host vào container
    volumes:
      - /proc:/host/proc:ro
      - /sys:/host/sys:ro
      - /:/rootfs:ro
    command:
      - '--path.procfs=/host/proc'
      - '--path.sysfs=/host/sys'
      - '--collector.filesystem.mount-points-exclude=^/(sys|proc|dev|host|etc)($$|/)'
    networks:
      - quickbite-net
    # Không cần mở port 9100 ra ngoài host vì chỉ phục vụ Prometheus quét nội bộ

4.2 Cập nhật file cấu hình prometheus.yml của Prometheus
Mở file cấu hình /etc/prometheus/prometheus.yml và bổ sung thêm job quét dữ liệu phần cứng VPS:

global:
  scrape_interval: 15s
  evaluation_interval: 15s
 
scrape_configs:
  - job_name: 'quickbite-microservices'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'user-service:8081'
          - 'restaurant-service:8082'
          - 'order-service:8083'
          - 'notification-service:8084'
 
  # Job MỚI: Thu thập dữ liệu phần cứng từ Node Exporter
  - job_name: 'vps-hardware'
    metrics_path: '/metrics' # Endpoint mặc định của Node Exporter
    static_configs:
      - targets: ['node-exporter:9100']

4.3 Khởi chạy hệ thống và kiểm chứng
Di chuyển ra thư mục chứa docker-compose.yml và khởi chạy dịch vụ mới:
docker compose up -d node-exporter

Thực hiện khởi động lại container Prometheus để nạp lại file cấu hình prometheus.yml mới:
docker compose restart prometheus

Kiểm chứng kết nối: Gọi vào API của Prometheus xem target Node Exporter đã hoạt động chưa:
docker exec -it quickbite-prometheus wget -qO- http://localhost:9090/api/v1/targets

Kết quả mong đợi:

Nhận về dữ liệu JSON chứa thông tin target

node-exporter:9100

hiển thị trạng thái

"health": “up”