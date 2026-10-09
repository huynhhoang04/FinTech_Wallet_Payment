Bài đọc: Mô hình môi trường Dev, Staging và Production
1. Vấn đề thực tế (Nguy cơ lộ thông tin Database)
Hiện tại, dịch vụ user-service của QuickBite cần kết nối đến cơ sở dữ liệu PostgreSQL để lưu thông tin tài khoản. Trong quá trình phát triển ở local, lập trình viên đang cấu hình cứng (hardcode) tài khoản trong file user-service/src/main/resources/application.yml như sau:

spring:

  datasource:

    url: jdbc:postgresql://localhost:5432/quickbite_user

    username: postgres

    password: super-secret-password-123

Khi chuẩn bị đưa ứng dụng này lên chạy thử trên máy chủ Staging và chạy thật trên Production, quy trình thủ công này lập tức gặp các vấn đề nghiêm trọng:

Lộ lọt thông tin bảo mật: Kể cả khi dự án sử dụng Private Repository (kho lưu trữ riêng tư) trên GitHub/GitLab, rủi ro rò rỉ thông tin vẫn cực kỳ cao. Kho code private của dự án thường được phân quyền rộng rãi: 
Các lập trình viên
Các bạn QA/QC (đôi khi không chuyên sâu về tech) 
Các bạn thực tập sinh (intern) thời vụ - những người có thể chỉ làm việc vài tháng rồi chuyển đi.
Việc để mật khẩu database Production thật hiển thị công khai trên Git đồng nghĩa với việc bạn trao chìa khóa hệ thống cho tất cả những người này.

Khó khăn khi chuyển đổi môi trường: Mỗi môi trường có một IP database và mật khẩu riêng. Nếu cấu hình cứng, mỗi lần chuyển từ Dev sang Staging hay Prod, bạn lại phải mở code ra sửa file cấu hình, chạy lệnh build lại file JAR mới. Quy trình này rất cồng kềnh và dễ gây nhầm lẫn phiên bản.
2. Mô hình ba môi trường tiêu chuẩn
 

Để quản lý sản phẩm an toàn, dự án QuickBite được chia làm 3 môi trường hoạt động độc lập:

Môi trường

Đối tượng sử dụng

Đặc điểm dữ liệu

Yêu cầu độ ổn định

Dev (Development)

Lập trình viên

Dữ liệu giả lập, tự tạo

Thấp (lỗi có thể sửa ngay)

Staging (UAT/Thử nghiệm)

Tester & Khách hàng duyệt

Mô phỏng dữ liệu thật (không nhạy cảm)

Trung bình - Cao (giống 99% Prod)

Prod (Production/Chạy thật)

Người dùng cuối

Dữ liệu thật, nhạy cảm

Tuyệt đối (Không được sập)

 

Nguyên tắc vàng của DevOps: Build once, run anywhere (Build một lần, chạy mọi nơi).

Một file JAR duy nhất sau khi được build thành công từ máy chủ CI phải chạy được trên cả Dev, Staging và Prod mà không được phép biên dịch lại. Sự khác biệt giữa các môi trường chỉ là các tham số cấu hình được nạp vào lúc khởi chạy (nạp cấu hình tại runtime).

3. Giải pháp: Quản lý cấu hình bằng biến môi trường
Áp dụng triết lý của phương pháp luận Twelve-Factor App (tách biệt hoàn toàn cấu hình khỏi mã nguồn), chúng ta sẽ cấu hình ứng dụng Spring Boot đọc thông số kết nối Database động từ biến môi trường (Environment Variables) của hệ điều hành.

3.1. Cấu hình động trong file application.yml của user-service
Thay vì viết cứng mật khẩu, ta sử dụng cú pháp placeholder của Spring Boot: ${TÊN_BIẾN:Giá_trị_mặc_định}:

spring:

  datasource:

    url: ${QUICKBITE_DB_URL:jdbc:postgresql://localhost:5432/quickbite_user}

    username: ${QUICKBITE_DB_USER:postgres}

    password: ${QUICKBITE_DB_PASS:password123}

 

Giải thích: Khi khởi chạy, Spring Boot sẽ tìm biến môi trường có tên QUICKBITE_DB_URL. Nếu tìm thấy, nó sẽ nạp giá trị đó; nếu không tìm thấy, nó sẽ tự động dùng giá trị mặc định phía sau dấu hai chấm (jdbc:postgresql://localhost:5432/quick bite user) để chạy ở máy local của dev.

4.2. Lệnh khởi chạy trên server Staging bằng cách nạp biến môi trường
Khi deploy lên server Staging, người vận hành chỉ cần export các biến môi trường tương ứng của Staging trên terminal của server trước khi chạy ứng dụng:

# 1. Nạp các cấu hình của môi trường Staging vào hệ điều hành

export QUICKBITE_DB_URL=jdbc:postgresql://10.0.1.20:5432/quickbite_user_staging

export QUICKBITE_DB_USER=staging_admin

export QUICKBITE_DB_PASS=StagingSecurePass@2026

 

# 2. Khởi chạy file JAR duy nhất

java -jar user-service-0.0.1.jar

 

Kết quả: Ứng dụng user-service tự động kết nối vào database của Staging mà không cần sửa bất kỳ dòng code nào. Mật khẩu database thật hoàn toàn nằm trên server, không bị lộ trên Github hay Gitlab.

4. Hiểu lầm tai hại
Hiểu sai: Dự án của tôi dùng Private Repository (kho lưu trữ riêng tư) trên GitHub/GitLab, nên tôi có thể tạo các file cấu hình cứng chứa mật khẩu thật (như application-prod.yml) rồi commit lên Git, khi chạy chỉ cần gõ --spring.profiles.active=prod là xong.
Đính chính: Đây là tư duy bảo mật cực kỳ sai lầm. Như đã phân tích ở Phần 2, kho code private của bạn vẫn có rất nhiều người truy cập được (từ các lập trình viên khác, QA/QC cho đến cộng tác viên và thực tập sinh thời vụ). 
Hãy luôn ghi nhớ một châm ngôn bảo mật bất di bất dịch: "Github là nơi quản lý mã nguồn, không phải nơi quản lý bí mật (secret)." Việc để mật khẩu Production tồn tại trên Git (dù là private repo) đồng nghĩa với việc bạn tự ý tiết lộ thông tin quan trọng nhất của hệ thống. Mật khẩu Production chỉ được phép tồn tại duy nhất ở runtime trên server Production (dưới dạng các biến môi trường được nạp bảo mật lúc chạy).