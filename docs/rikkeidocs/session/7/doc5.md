Bài đọc: Chẩn đoán sự cố và quản trị log hệ thống CI/CD
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Xác định vị trí và truy cập giao diện Log Console của từng bước chạy (step) trên tab Actions của GitHub.
Chẩn đoán nguyên nhân lỗi của luồng công việc (workflow) bằng phương pháp phân tích log kỹ thuật.
Khắc phục trực tiếp các sự cố thường gặp được mô phỏng từ kịch bản Lesson 04 bao gồm: lỗi phân quyền thực thi (Permission Denied), lỗi biên dịch mã nguồn Java (Compilation Failed), và lỗi kiểm thử tự động thất bại (Test FAILED).
 

2. Thiết lập môi trường thực hành và demo trực tiếp
Bài học này được thiết kế để giảng viên thực hiện mô phỏng thao tác lỗi trực tiếp, hướng dẫn học viên thực hành rà soát kỹ năng đọc mã lỗi (error logs). Bài học không tập trung vào lý thuyết trừu tượng, thay vào đó mô phỏng luồng công việc cốt lõi của dự án Spring Boot tại Lesson 04.

 

2.1. Tệp cấu hình gốc (Baseline)
Học viên khởi đầu với tệp cấu hình .github/workflows/build.yml của hệ thống user-service:

name: Build Spring Boot App

 

on:

  push:

    branches:

      - main

 

jobs:

  build_executable_jar:

    runs-on: [self-hosted, quickbite]

    steps:

      - name: Checkout mã nguồn

        uses: actions/checkout@v5

       

      - name: Cài đặt JDK 17

        uses: actions/setup-java@v5

        with:

          java-version: '17'

          distribution: 'temurin'

         

      - name: Cấp quyền Gradle Wrapper

        run: chmod +x ./gradlew

       

      - name: Biên dịch tệp JAR

        run: ./gradlew bootJar

 

3. Kịch bản lỗi 1: Lỗi phân quyền Wrapper (Permission Denied) 
3.1. Quy trình tạo lỗi mô phỏng
Cố tình điều chỉnh tệp tin build.yml, vô hiệu hóa bước cấp quyền thực thi (xóa bỏ đoạn khai báo run: chmod +x ./gradlew).
Thực hiện tải mã nguồn lên kho lưu trữ (commit & push) để kích hoạt sự kiện push.
 

3.2. Nhận diện mã lỗi qua giao diện GitHub Actions
Truy cập tab Actions, chọn phiên chạy báo lỗi màu đỏ (Failed). Bấm chọn vào công việc build_executable_jar, sau đó trỏ vào bước Biên dịch tệp JAR để mở rộng nội dung log hiển thị:

Run ./gradlew bootJar

  ./gradlew bootJar

  shell: /usr/bin/bash -e {0}

/home/runner/work/_temp/...: line 2: ./gradlew: Permission denied

Error: Process completed with exit code 126.

 

3.3. Giải thích nguyên nhân và phương pháp khắc phục
Ý nghĩa thông báo lỗi: Tệp thực thi ./gradlew thiếu thuộc tính quyền thực thi (execute bit) đối với hệ điều hành phân phối dựa trên nền tảng Unix/Linux do máy chủ Runner đang sử dụng. Lỗi này thường gặp khi lập trình viên khởi tạo và biên soạn cấu trúc hệ thống trên môi trường Windows (hệ điều hành không quản lý chặt chẽ thuộc tính thực thi), do đó mã nguồn được lưu trữ thiếu thuộc tính này.
Phương pháp khắc phục: Cập nhật lại tệp build.yml với việc bổ sung lệnh run: chmod +x ./gradlew trước khối lệnh biên dịch nhằm cưỡng ép hệ thống cấp quyền chạy hợp lệ.
 

4. Kịch bản lỗi 2: Lỗi biên dịch mã nguồn (Compilation Failed) 
4.1. Quy trình tạo lỗi mô phỏng
Giảng viên chủ ý thay đổi mã nguồn Java để tạo lỗi cú pháp. Cụ thể, truy cập tệp src/main/java/com/quickbite/user/service/UserService.java và chỉnh sửa khai báo thư viện không chính xác: 
// Cố tình viết sai tên lớp từ UserRepository sang UserReposotory

private UserReposotory userRepository;

Giữ nguyên tệp build.yml hợp lệ từ cấu hình gốc và đẩy mã nguồn lên hệ thống.
 

4.2. Nhận diện mã lỗi qua giao diện GitHub Actions
Mở log công việc bị đình chỉ và kiểm tra trạng thái kiểm thử:

$ ./gradlew bootJar

> Task :compileJava FAILED

/builds/quickbite/user-service/src/main/java/com/quickbite/user/service/UserService.java:24: error: cannot find symbol

        private UserReposotory userRepository;

                ^

  symbol:   class UserReposotory

  location: class UserService

1 error

 

4.3. Giải thích nguyên nhân và phương pháp khắc phục
Ý nghĩa thông báo lỗi: Trình biên dịch Java (Java Compiler) từ chối tạo mã trung gian (bytecode) do cấu trúc lớp không tuân thủ cú pháp hệ thống hoặc không tìm thấy tham chiếu thư viện hợp lệ. Nhật ký log ghi rõ điểm phát sinh lỗi bao gồm đường dẫn tuyệt đối, số dòng lỗi (dòng 24) và mô tả ký tự sai lệch.
Phương pháp khắc phục: Cần xác định vị trí file và dòng được mô tả trong hệ thống log, sửa lại lỗi theo chuẩn ngôn ngữ Java. Bắt buộc thực hiện việc chạy thử nghiệm bằng câu lệnh ./gradlew compileJava ở môi trường cục bộ để xác nhận thành công trước khi đẩy lên máy chủ mã nguồn.
 

5. Kịch bản lỗi 3: Lỗi kiểm thử thất bại (Test Failed)
5.1. Quy trình tạo lỗi mô phỏng
Cố tình điều chỉnh tệp cấu hình luồng công việc để cưỡng chế hệ thống chạy quá trình kiểm thử tự động, cụ thể là thay đổi câu lệnh tác vụ từ bootJar sang build:
build_executable_jar:

  stage: build

  tags:

    - quickbite

  script:

    - chmod +x ./gradlew

    - ./gradlew build # Thay bootJar bằng build để kích hoạt chạy test

 

Sửa đổi hệ số logic của một kiểm thử trong thư mục src/test/java/... để hệ thống giả lập môi trường thất bại. Ví dụ: thay thế assertion assertEquals(200, status) thành assertEquals(500, status).
Tải lên và theo dõi log hành vi của hệ thống CI/CD.
 

5.2. Nhận diện mã lỗi qua giao diện GitHub Actions
Mở log của job thất bại và quan sát phần kết quả thực thi kiểm thử:

> Task :test

 

UserServiceApplicationTests > testCreateUser() FAILED

    org.opentest4j.AssertionFailedError at UserServiceApplicationTests.java:17

 

2 tests completed, 1 failed

 

> Task :test FAILED

 

5.3. Giải thích nguyên nhân và cách khắc phục
Ý nghĩa thông báo lỗi: Việc biên dịch lớp Java đã hoàn tất thuận lợi, tuy nhiên kết quả thực tế trả về từ các tệp kiểm thử không thỏa mãn điều kiện logic được định cấu hình. Cơ chế Gradle sẽ áp dụng hình thức "Fail-fast", ngay lập tức huỷ bỏ quá trình tạo tệp đóng gói user-service.jar nhằm tránh rò rỉ mã lỗi.
Phương pháp khắc phục:
Xác định tệp kiểm thử và phương thức bị từ chối dựa vào nội dung log chỉ định.
Hiệu chỉnh logic hoạt động hoặc giá trị khẳng định (assertion) trong mã nguồn kiểm thử sao cho trùng khớp với thiết kế.
Sử dụng lệnh ./gradlew test để đánh giá cục bộ tại thiết bị trước khi tiến hành tạo commit mới.
 

6. Quy trình chẩn đoán lỗi cơ bản
Khi quy trình CI/CD trả về báo cáo thất bại (giao diện thể hiện nút đỏ chéo), người kỹ sư cần tuân thủ quy trình xử lý bao gồm các bước phân tích:

Truy cập không gian làm việc: Truy cập tab Actions, chọn sự kiện Workflow, xác định công việc (Job) đang hiển thị trạng thái Failed.
Khai thác nhật ký log từ cấp độ thấp:
Mở phần mở rộng của từng bước (step) hiển thị nút lỗi. Cuộn xuống phần cuối log để theo dõi mã báo cáo quá trình (Exit code).
Lần ngược lên để đọc chi tiết thông báo lỗi được hệ thống sinh ra (Error Traces) và khoanh vùng nguyên nhân thông qua cấu trúc báo cáo của nền tảng (ví dụ: mô tả chính xác tệp/dòng đối với lỗi Compiler).
Phục hồi và Xác nhận: Sửa đổi mã nguồn hoặc tệp cấu hình theo giải pháp, tuyệt đối chạy lệnh tự kiểm tra tại máy cá nhân, tiếp đó hợp nhất thay đổi để xác minh trạng thái luồng.
 

7. Lưu ý, lỗi sai và hiểu lầm thường gặp
Phản ứng hoảng loạn và thay đổi thiếu cơ sở: Học viên thường có xu hướng thay đổi ngẫu nhiên cấu trúc mã nguồn một cách tùy tiện mà không dựa trên chứng cứ thực nghiệm từ bộ phân tích log. Thói quen này làm giảm tính chuyên nghiệp và tiêu tốn tài nguyên quy trình đáng kể.
Khước từ thông tin Exit Code: Việc phớt lờ thông điệp Exit Code từ hệ thống làm giảm đi tính phản xạ kỹ thuật. Ví dụ, exit code 126 báo hiệu thiếu quyền thực thi; exit code 127 đại diện cho việc lệnh gọi bị sai chuẩn chính tả hoặc không tồn tại.
Mặc định nguyên nhân xuất phát từ thuật toán: Rất nhiều trường hợp lỗi hệ thống xuất phát từ việc cấu trúc tệp .github/workflows dùng sai phím khoảng trắng thụt lề hoặc thiết lập phiên bản phần mềm JDK khác với chuẩn quy định, chứ không phải do logic lập trình mã nguồn bị sai sót.
 

8. Tài liệu tham khảo
Phân tích nhật ký luồng công việc:

Viewing workflow run history

Quy chuẩn mã thoát hệ thống Linux (Exit Codes):

Bash Exit Codes Reference