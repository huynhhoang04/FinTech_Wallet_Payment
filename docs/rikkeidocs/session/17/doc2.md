Bài đọc: Triển khai EFK Stack bằng Docker Compose
PHẦN 1. MỤC TIÊU BÀI HỌC
Sau khi hoàn thành bài học này, học viên có khả năng:

Tổ chức và triển khai được ngăn xếp EFK độc lập bằng Docker Compose trên máy chủ Ubuntu Server.
Cấu hình được Elasticsearch, Fluentd và Kibana cùng tham gia vào Docker network quickbite-net.
Xây dựng được Custom Dockerfile cho Fluentd để cài đặt plugin kết nối fluent-plugin-elasticsearch.
Thiết lập hệ thống Persistent Volume cho dữ liệu index của Elasticsearch và bộ đệm (buffer) của Fluentd.
Kiểm tra trạng thái sẵn sàng (Healthcheck) của các container và thiết lập kết nối an toàn tới Kibana qua SSH Tunnel.
PHẦN 2. VẤN ĐỀ THỰC TẾ
Ba thành phần Elasticsearch, Fluentd và Kibana thuộc về lớp hạ tầng giám sát (Observability Infrastructure) và có vòng đời vận hành hoàn toàn độc lập với các microservices nghiệp vụ của QuickBite. Nếu gộp chung toàn bộ vào tệp docker-compose.yml tại thư mục gốc, tệp cấu hình sẽ bị phình to, phức tạp và gây khó khăn khi cần nâng cấp hoặc khởi động lại riêng cụm logging.

Tương tự như cách QuickBite đã tách riêng ngăn xếp Prometheus/Grafana, cụm EFK được tổ chức thành một Docker Compose project độc lập trong thư mục efk/:

~/quickbite/
├── docker-compose.yml # Quản lý 4 microservices nghiệp vụ
├── prometheus_grafana/ # Quản lý hạ tầng Metrics
└── efk/ # Quản lý hạ tầng Logging tập trung
    ├── docker-compose.yml
    └── fluentd/
        ├── Dockerfile
        └── fluent.conf

Mặc dù nằm ở hai Compose project riêng biệt, các container vẫn có thể giao tiếp thông suốt với nhau thông qua mạng ngoài dùng chung quickbite-net.

PHẦN 3. KIẾN THỨC CỐT LÕI
3.1 Yêu cầu cấu hình Kernel Host cho Elasticsearch
Elasticsearch sử dụng công nghệ mmapfs để ánh xạ các chỉ mục lưu trữ vào bộ nhớ ảo. Mặc định trên Ubuntu, giới hạn số lượng vùng ánh xạ vm.max_map_count quá thấp khiến Elasticsearch bị lỗi khi khởi động.

Thiết lập cấu hình vĩnh viễn trên Ubuntu host:

# Ghi cấu hình kernel và áp dụng ngay lập tức
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elasticsearch.conf
sudo sysctl --system
 
# Xác nhận giá trị đã được cập nhật thành công
sysctl vm.max_map_count

Kết quả đầu ra hợp lệ:

vm.max_map_count = 262144

3.2 Khóa phiên bản và Thiết lập Persistent Volumes
Đồng bộ phiên bản: Elasticsearch và Kibana phải sử dụng cùng phiên bản. Bài lab này cố định ở 8.15.0 để các thành phần tương thích và tái lập được; đây không phải khẳng định về phiên bản mới nhất.
Lưu trữ dữ liệu bền vững (Persistent Volumes):
Tên Volume

Vị trí gắn trong Container

Mục đích kỹ thuật

elasticsearch-data

/usr/share/elasticsearch/data

Lưu trữ toàn bộ chỉ mục log; bảo toàn dữ liệu khi container bị xóa hoặc nâng cấp.

fluentd-buffer

/fluentd/buffer

Lưu trữ các tệp đệm log tạm thời trên đĩa khi Elasticsearch gặp sự cố hoặc quá tải.

3.3 Xây dựng Custom Fluentd Image
Image Fluentd chính thức trên Docker Hub chỉ chứa các plugin cơ bản. Để gửi dữ liệu sang Elasticsearch, ta cần tạo một Custom Dockerfile dựa trên phiên bản Debian:

# Sử dụng base image Debian chính thức hỗ trợ jemalloc tối ưu bộ nhớ
FROM fluent/fluentd:v1.19.3-debian-2.2
 
USER root
# Cài đặt plugin output Elasticsearch và chuẩn bị thư mục buffer
RUN gem install fluent-plugin-elasticsearch --no-document \
    && mkdir -p /fluentd/buffer \
    && chown -R fluent:fluent /fluentd
USER fluent

3.4 Cấu hình chi tiết fluent.conf
Tạo tệp cấu hình ~/quickbite/efk/fluentd/fluent.conf:

# 1. Tiếp nhận log từ Docker logging driver qua giao thức forward
<source>
  @type forward
  port 24224
  bind 0.0.0.0
</source>
 
# 2. Bóc tách trường 'log' từ chuỗi text sang các trường JSON độc lập
<filter quickbite.**>
  @type parser
  key_name log
  reserve_data true
  <parse>
    @type json
  </parse>
</filter>
 
# 3. Bổ sung các nhãn metadata môi trường chung
<filter quickbite.**>
  @type record_transformer
  <record>
    environment production
    collector fluentd
  </record>
</filter>
 
# 4. Điều hướng và đẩy dữ liệu vào Elasticsearch
<match quickbite.**>
  @type elasticsearch
  host elasticsearch
  port 9200
  logstash_format true
  logstash_prefix quickbite
  include_tag_key true
  tag_key fluentd_tag
 
  # Cấu hình bộ đệm dạng file chống mất dữ liệu khi mất kết nối
  <buffer>
    @type file
    path /fluentd/buffer/quickbite
    flush_mode interval
    flush_interval 5s
    retry_forever true
    chunk_limit_size 8m
    total_limit_size 512m
  </buffer>
</match>

3.5 Tệp docker-compose.yml hoàn chỉnh cho cụm EFK
Tạo tệp ~/quickbite/efk/docker-compose.yml:

services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.15.0
    container_name: quickbite-elasticsearch
    environment:
      discovery.type: single-node
      xpack.security.enabled: "false"
      xpack.license.self_generated.type: basic
      ES_JAVA_OPTS: "-Xms1g -Xmx1g"
    volumes:
      - elasticsearch-data:/usr/share/elasticsearch/data
    ports:
      - "127.0.0.1:9200:9200"
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9200 >/dev/null"]
      interval: 10s
      timeout: 5s
      retries: 20
    networks:
      - quickbite-net
 
  fluentd:
    build:
      context: ./fluentd
    container_name: quickbite-fluentd
    volumes:
      - ./fluentd/fluent.conf:/fluentd/etc/fluent.conf:ro
      - fluentd-buffer:/fluentd/buffer
    environment:
      FLUENTD_CONF: fluent.conf
    ports:
      - "127.0.0.1:24224:24224"
      - "127.0.0.1:24224:24224/udp"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - quickbite-net
 
  kibana:
    image: docker.elastic.co/kibana/kibana:8.15.0
    container_name: quickbite-kibana
    environment:
      ELASTICSEARCH_HOSTS: '["http://elasticsearch:9200"]'
    ports:
      - "127.0.0.1:5601:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy
    networks:
      - quickbite-net
 
volumes:
  elasticsearch-data:
  fluentd-buffer:
 
networks:
  quickbite-net:
    external: true

PHẦN 4. DEMO VÀ THỰC HÀNH (KHỞI ĐỘNG VÀ KIỂM TRA CỤM EFK)
4.1 Tạo cấu trúc thư mục và khởi chạy Stack
Thiết lập cấu trúc thư mục làm việc:
cd ~/quickbite
mkdir -p efk/fluentd
cd efk

Đặt 3 file cấu hình đã chuẩn bị vào đúng vị trí:

 
docker-compose.yml (tại thư mục efk/)
fluentd/Dockerfile
fluentd/fluent.conf
Tiến hành build image và khởi chạy toàn bộ cụm dịch vụ trong nền:

docker compose up -d --build
docker compose ps

Kết quả đầu ra mong đợi:

NAME IMAGE STATUS
quickbite-elasticsearch elasticsearch:8.15.0 running (healthy)
quickbite-fluentd efk-fluentd running
quickbite-kibana kibana:8.15.0 running

4.2 Kiểm tra trạng thái Elasticsearch API qua Loopback
Kiểm tra trực tiếp từ máy chủ Ubuntu để xác nhận Elasticsearch đã sẵn sàng:

# Kiểm tra thông tin chung của node
curl http://127.0.0.1:9200
 
# Kiểm tra trạng thái sức khỏe của cụm (Cluster Health)
curl http://127.0.0.1:9200/_cat/health?v

Cụm hoạt động bình thường khi cột status hiển thị trạng thái green hoặc yellow (trạng thái bình thường của mô hình single-node).

4.3 Truy cập giao diện Kibana an toàn qua SSH Tunnel
Để bảo đảm an toàn dữ liệu, cổng 5601 của Kibana chỉ được bind vào 127.0.0.1 trên máy chủ. Kỹ sư vận hành truy cập từ máy cá nhân bằng cơ chế SSH Port Forwarding:

Trên máy tính cá nhân, mở terminal và chạy lệnh:

ssh -L 5601:127.0.0.1:5601 <deployer>@<vps-public-ip>

Mở trình duyệt web và truy cập địa chỉ:

http://127.0.0.1:5601

Giao diện trang chủ của Kibana sẽ hiển thị sẵn sàng cho việc tạo Data View và phân tích dữ liệu log.

PHẦN 5. TỔNG KẾT
Kiến trúc phân tách: EFK được vận hành như một Compose project độc lập, giúp bảo trì linh hoạt và không làm ảnh hưởng đến mã nguồn microservices của QuickBite.
Persistent Volumes: Bắt buộc sử dụng named volumes cho elasticsearch-data và fluentd-buffer để bảo toàn chỉ mục log và chống mất dữ liệu khi có sự cố mạng.
Cơ chế Buffer của Fluentd: Lưu trữ đệm dạng file trên đĩa giúp Fluentd tự động lưu giữ và retry liên tục cho tới khi Elasticsearch phục hồi.
Bảo mật truy cập: Tất cả các cổng quản trị (9200, 5601) và cổng collector (24224) chỉ mở ở phạm vi loopback của máy chủ.