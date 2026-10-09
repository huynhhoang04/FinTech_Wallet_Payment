Bài đọc: Phiên bản hóa và Đẩy Docker Image lên Registry từ Local
 

1. Mục tiêu bài đọc
Sau khi hoàn thành bài học này, học viên có khả năng:

Giải thích được vai trò và cơ chế hoạt động của GitLab Container Registry.
Áp dụng các quy tắc đặt tên và gắn tag phiên bản (tagging) cho Docker image.
Thực hiện đăng nhập bảo mật và đẩy (push) Docker image từ local lên Registry trực tuyến thành công.
 

2. Vấn đề thực tế (Nơi lưu trữ Docker Image)
Trong các bài học trước, chúng ta tự động hóa việc build Docker image trực tiếp trên GitLab Runner. Tuy nhiên chúng ta chưa biết cách lưu trữ lại Image đó để có thể sử dụng sau này. Để làm quen với các khái niệm cốt lõi của GitLab Container Registry, học viên cần nắm vững các thao tác thủ công từ môi trường phát triển local.
Mục đích của kịch bản thực hành:
Mô phỏng từng bước (Step-by-step Learning): Tách biệt tiến trình đóng gói và đẩy image để trực quan hóa cách thức xác thực, gắn nhãn (tag) phiên bản và truyền tải dữ liệu bằng Docker CLI.
Tối ưu hóa thời gian thực hành: Tận dụng bộ nhớ cache dependencies Gradle (~/.gradle) và Docker layer có sẵn tại máy local giúp đẩy nhanh tiến độ làm bài thực hành.

Không áp dụng trong môi trường Production thực tế:
Quy trình build và push image thủ công từ máy local của lập trình viên là một anti-pattern và vi phạm nghiêm trọng nguyên tắc bảo mật. Máy local không được kiểm soát an toàn như Runner, đồng thời quy trình này gây mất tính nhất quán của mã nguồn (image được push lên có thể chứa code chưa được commit trên Git). Trong thực tế, toàn bộ tiến trình này bắt buộc phải chạy tự động hóa hoàn toàn trên pipeline CI/CD.

 

3. Kiến thức cốt lõi
3.1. Gitlab Container Registry
GitLab Container Registry là kho lưu trữ Docker image riêng tư (private) đi kèm với từng dự án GitLab.

Địa chỉ máy chủ lưu trữ mặc định: registry.gitlab.com.
Đường dẫn lưu image của dự án có dạng: registry.gitlab.com/<namespace>/user-service (với <namespace> là username hoặc group của học viên trên GitLab).
Quản lý trực quan: Xem danh sách image đã đẩy lên tại giao diện Web của GitLab -> Deploy -> Container Registry. Sau khi đăng nhập thành công bằng lệnh docker login registry.gitlab.com, học viên có thể truy cập trực tiếp đường dẫn https://gitlab.com/<namespace>/user-service/container_registry trên trình duyệt để xem trong kho chứa những gì.
 

3.2. Quy tắc đặt nhãn (Tagging) và Phiên bản hóa (Versioning)
Để đẩy được image lên Registry, tên của image nguồn ở máy local bắt buộc phải khớp chính xác với đường dẫn Registry được GitLab phân quyền:

docker tag <local_image_name> registry.gitlab.com/<namespace>/<project_name>:<version_tag>

 

● Version tag: Phân bản image rõ ràng theo phiên bản phát hành (ví dụ: 1.0.0, 1.1.0-RC1) để phục vụ quản lý phiên bản và rollback khi có lỗi, tránh dùng nhãn tĩnh latest.

 

3.3. Đăng nhập bảo mật từ Local bằng Access Token
Vì GitLab Registry là private, máy local cần đăng nhập để xác thực quyền ghi (push). Để đảm bảo an toàn thông tin:

● Tuyệt đối không sử dụng mật khẩu tài khoản GitLab chính để đăng nhập từ terminal.

● Sử dụng Personal Access Token (PAT) hoặc Project Deploy Token được cấp quyền giới hạn (write_registry, read_registry).

 

4. Demo thực hành (Build và Push image từ local) 
Học viên thực hiện đóng gói, gắn tag phiên bản và đẩy image của microservice user-service từ máy phát triển cá nhân lên GitLab Container Registry.

 

4.1. Tạo Personal Access Token trên GitLab
Trên giao diện Web của GitLab, click chọn ảnh đại diện ở góc trên bên trái -> Preferences -> Access Tokens.
Nhấp chọn Add new token.
Điền tên token (ví dụ: local-docker-access), chọn Scopes: write_registry và read_registry.
Nhấp Create personal access token và sao chép mã token hiển thị (mã này chỉ xuất hiện 1 lần duy nhất).
 

4.2. Thực thi các lệnh build và đẩy image tại Local Terminal
Mở terminal tại máy cá nhân, di chuyển vào thư mục gốc của dự án user-service và thực hiện chuỗi lệnh:

# 1. Đăng nhập an toàn vào Registry sử dụng Personal Access Token

# Thay <gitlab_username> bằng tên đăng nhập và nhập token vừa tạo khi terminal hỏi mật khẩu (Password)

docker login registry.gitlab.com -u <gitlab_username>

 

# 2. Biên dịch JAR nhanh chóng tại local (nhờ tận dụng cache dependencies Gradle có sẵn)

./gradlew bootJar

 

# 3. Build Docker image tại local sử dụng Dockerfile đơn tầng (đã chuẩn bị ở Lesson 01)

docker build -t user-service:1.0.0 .

 

# 4. Gắn tag Registry cho image (thay thế <namespace> bằng username hoặc group của bạn trên GitLab)

docker tag user-service:1.0.0 registry.gitlab.com/<namespace>/user-service:1.0.0

 

# 5. Đẩy image lên GitLab Container Registry trực tuyến

docker push registry.gitlab.com/<namespace>/user-service:1.0.0

 

Không kiểm tra trạng thái trong after_script:
Việc kiểm chứng trạng thái đẩy image được thực hiện trực tiếp thông qua log output thành công của lệnh docker push tại terminal local và xác minh trên giao diện Web UI của GitLab.

 

4.3. Kết quả mong đợi
Terminal in ra log tiến trình tải các layer lên mạng internet và kết thúc bằng thông báo thành công. Khi truy cập vào dự án GitLab trên trình duyệt, mục Deploy -> Container Registry hiển thị image user-service đi kèm tag 1.0.0 hoạt động chính xác.

 

5. Lưu ý, lỗi sai và hiểu lầm thường gặp
Lỗi từ chối phân quyền (denied: requested access to the resource is denied): Lỗi xảy ra do học viên gõ sai cấu trúc đường dẫn Registry (ví dụ sai username hoặc sai tên dự án), hoặc quên đăng nhập docker login thành công trước đó.
Khắc phục: Kiểm tra kỹ địa chỉ registry của dự án trên giao diện GitLab Web UI và thực hiện đăng nhập lại bằng Personal Access Token có đủ quyền.
Lộ lọt mật khẩu cá nhân: Sinh viên gõ mật khẩu tài khoản chính của GitLab trực tiếp vào terminal hoặc lưu trong các file script tự động.
Khắc phục: Luôn dùng Personal Access Token hoặc Deploy Token để đăng nhập trên terminal, đảm bảo tính bảo mật tối đa.
 

6. Tài liệu tham khảo
Tài liệu hướng dẫn GitLab Container Registry:

GitLab Container Registry | GitLab Documentation

Quản lý Personal Access Tokens trên GitLab:

Personal access tokens | GitLab Documentation

