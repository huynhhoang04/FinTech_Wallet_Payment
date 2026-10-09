Bài đọc: Build ứng dụng Spring Boot bằng Maven/Gradle trong CI
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Cấu hình được môi trường biên dịch (JDK) thông qua Action chuẩn actions/setup-java phù hợp với phiên bản Java của từng microservice.
Biên soạn thành công quy trình đóng gói hoàn chỉnh: Tải mã nguồn -> Cấu hình Java -> Cấp quyền Gradle Wrapper -> Biên dịch ra file JAR thương mại (bootJar) một cách tối giản.
Lưu trữ tệp tin thành phẩm bằng cách sử dụng Action actions/upload-artifact để có thể tải xuống từ giao diện GitHub.
Tối ưu hóa hiệu năng luồng chạy bằng cách tinh giản các bước kiểm thử rườm rà.
 

2. Vấn đề thực tế (Thách thức về hiệu năng và tài nguyên) 
Trong quy trình thực hành học tập, chúng ta tận dụng tài nguyên GitHub-hosted runner miễn phí được cấp phát chung cho các dự án mã nguồn mở hoặc dự án cá nhân nhỏ. Tuy nhiên, chúng có những giới hạn về mặt hệ thống:

Máy ảo chỉ được cấp phát cấu hình RAM/CPU có giới hạn (thường là 2 vCPU và 7GB RAM).
Môi trường sạch hoàn toàn ở mỗi lượt chạy, đồng nghĩa với việc không duy trì bộ đệm thư viện (cache) nếu không được thiết lập tường minh.
Nếu kích hoạt toàn bộ quy trình kiểm thử tự động cùng lúc, Spring Boot sẽ phải nạp toàn bộ cấu trúc ứng dụng (Spring Context) lên RAM của Runner, làm tăng đáng kể thời gian thực thi và rủi ro hết tài nguyên cục bộ.
 

Để tối ưu hóa thời gian trả về kết quả (thường chỉ nên nằm dưới mức 1.5 phút cho các tác vụ biên dịch), chúng ta cần thiết kế một luồng công việc (workflow) tinh giản tối đa: tạm thời bỏ qua bước kiểm thử tự động phức tạp và tiến thẳng vào mục tiêu cốt lõi là biên dịch ra tệp tin JAR thành phẩm.

 

3. Kiến thức cốt lõi (Cấu hình Pipeline CI tinh giản) 
3.1. Cấu hình cài đặt Java/JDK
Ứng dụng Spring Boot yêu cầu môi trường JDK đầy đủ để biên dịch mã nguồn Java. Thay vì thiết lập hệ điều hành theo cách thủ công, GitHub cung cấp Action actions/setup-java:

Hỗ trợ cài đặt nhanh các phiên bản: Java 17 (dành cho user-service), Java 21 (dành cho restaurant-service).
Chỉ định nhà phân phối bằng tham số distribution: 'temurin' (tương đương với bộ Eclipse Temurin JDK tối ưu hóa).
 

3.2. Tái sử dụng Gradle Wrapper (gradlew)
Hệ thống không yêu cầu cài đặt cài đặt Gradle vào hệ điều hành của máy chủ Runner. Chúng ta sẽ tận dụng tệp tin thực thi Gradle Wrapper (gradlew và thư mục gradle/wrapper/) đã được nhà phát triển tích hợp sẵn trong mã nguồn gốc:

chmod +x ./gradlew: Cấp quyền thực thi cho tệp tin (execute bit) trước khi khởi chạy (điều kiện bắt buộc trên môi trường Linux).
./gradlew bootJar: Câu lệnh yêu cầu công cụ Gradle biên dịch và đóng gói ứng dụng Spring Boot thành tệp JAR thực thi. Tác vụ bootJar có đặc điểm là mặc định không kích hoạt các quy trình kiểm thử (tests), đảm bảo hệ thống phản hồi nhanh nhất.
 

3.3. Tận dụng Fallback của application.yml
Khi build JAR, Spring Boot có thể cần kiểm tra cấu hình kết nối. Nhờ cách viết cấu hình thông minh sử dụng fallback trong application.yml ở Session 5:

url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:quickbite_user}

 

Nếu không nạp biến môi trường hệ thống, ứng dụng sẽ tự động sử dụng giá trị mặc định (như localhost). Nhờ đó, quá trình biên dịch và đóng gói file JAR diễn ra mượt mà mà không đòi hỏi phải có một cơ sở dữ liệu PostgreSQL thực tế đang chạy lúc build.

 

3.4. Lưu trữ Artifacts
Khác với kiến trúc máy chủ nguyên khối thông thường, tệp JAR sau khi được sinh ra sẽ nằm trên không gian ảo của Runner và sẽ biến mất khi phiên chạy kết thúc. Để giữ lại thành quả, cần dùng actions/upload-artifact@v4 để đóng gói tệp tin đó và tải lên không gian lưu trữ của GitHub.

 

4. Demo và thực hành (Biên soạn file CI cho user-service) 
4.1. Biên soạn tệp cấu hình .github/workflows/build.yml
Tạo file build.yml tại thư mục .github/workflows/ của dự án user-service với cấu hình tinh giản sau:

name: Build Spring Boot App

 

on:

  push:

    branches:

      - main

 

jobs:

  build_executable_jar:

    runs-on: [self-hosted, quickbite] # Sử dụng Self-hosted runner của tổ chức

    steps:

      - name: Checkout mã nguồn

        uses: actions/checkout@v5

       

      - name: Cài đặt môi trường JDK 17

        uses: actions/setup-java@v5

        with:

          java-version: '17'

          distribution: 'temurin'

         

      - name: Cấp quyền thực thi cho Gradle Wrapper

        run: chmod +x ./gradlew

       

      - name: Biên dịch và đóng gói tệp JAR (Bỏ qua tác vụ Test)

        run: ./gradlew bootJar

       

      - name: Lưu trữ sản phẩm (Artifact)

        uses: actions/upload-artifact@v4

        with:

          name: user-service-jar

          path: build/libs/*.jar

          retention-days: 3

 

Đồng bộ cho restaurant-service:

Đối với dịch vụ restaurant-service sử dụng nền tảng Java 21, học viên chỉ cần điều chỉnh tham số java-version: '17' thành java-version: '21' tại bước cài đặt môi trường. Các quy trình còn lại được kế thừa nguyên vẹn. 

 

4.2. Kết quả mong đợi
Khi push code lên Github, pipeline sẽ khởi chạy và hoàn thành chỉ trong khoảng 1 đến 1.5 phút. Tệp tin sản phẩm user-service.jar được sinh ra tại đường dẫn build/libs/ và được lưu giữ an toàn trên hệ thống lưu trữ artifact của Github.

 

5. Lưu ý, lỗi sai và hiểu lầm thường gặp
Bỏ quên lệnh cấp quyền cho Gradle Wrapper: Rất nhiều học viên không khai báo lệnh chmod +x ./gradlew trước khối lệnh biên dịch, hậu quả là máy chủ Runner sẽ trả về mã lỗi thoát (Exit code) với thông báo Permission Denied.
Khai báo không đồng bộ phiên bản JDK: Lỗi do thiết lập java-version: '17' cho các cấu phần sử dụng chuẩn ngôn ngữ Java 21. Lỗi này gây ra thông báo lỗi định dạng lớp không tương thích (UnsupportedClassVersionError) từ trình biên dịch.
Sai cấu trúc đường dẫn Artifact: Nếu khai báo tham số path: target/*.jar (dành cho Maven) thay vì build/libs/*.jar (dành cho Gradle), hệ thống sẽ không tìm thấy tệp tin nào để tải lên và báo cảnh báo No files were found with the provided path.
 

6. Tài liệu tham khảo
Thiết lập Java Action:

actions/setup-java repository

Quy chuẩn lưu trữ dữ liệu luồng công việc:

actions/upload-artifact repository

 