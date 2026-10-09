Bài đọc: Xây dựng cấu trúc Workflow CI/CD cơ bản
 

1. Mục tiêu bài học
Sau khi hoàn thành bài học này, học viên có khả năng:

Nắm vững cú pháp YAML và quy tắc thụt lề bắt buộc trong file cấu hình Workflow của GitHub Actions.
Khai báo và cấu trúc thành công một file Workflow cơ bản gồm các thành phần cốt lõi: name, on, jobs, runs-on, và steps.
Sử dụng được các action có sẵn (như actions/checkout, actions/setup-java) để thiết lập môi trường chạy.
Áp dụng cơ chế nhãn (labels) trên runs-on để điều phối công việc chạy trên đúng máy chủ Runner mong muốn.
 

2. Vấn đề thực tế (Thách thức trong thiết lập kịch bản tự động hoá)
Khi đã cài đặt và cấu hình thành công một máy chủ Runner, hệ thống vẫn chưa thể tự động nhận biết cần phải thực thi những lệnh gì cho dự án mã nguồn của bạn.

Mã nguồn có tự động được tải về không?
Cần chạy lệnh gì để in ra thông tin biến môi trường?
Sử dụng phiên bản JDK nào để biên dịch?
Để giải quyết vấn đề này, GitHub Actions áp dụng nguyên lý Infrastructure as Code (Hạ tầng như mã nguồn). Bạn phải biên soạn kịch bản cấu hình lưu trữ trực tiếp cùng mã nguồn bằng định dạng YAML. Trong GitHub Actions, các tệp cấu hình này được gọi là Workflows.


Quy định về cấu trúc thư mục Workflow:
 GitHub Actions yêu cầu bắt buộc tất cả các tệp tin Workflow (ví dụ: ci.yml) phải được đặt trong thư mục ẩn có đường dẫn chuẩn xác là .github/workflows/ tính từ thư mục gốc của kho lưu trữ. Nếu đặt sai thư mục, GitHub sẽ bỏ qua và không kích hoạt quy trình tự động.

 

3. Kiến thức cốt lõi (Cú pháp và các từ khoá nền tảng)
3.1. Cú pháp YAML cơ bản
YAML là định dạng dữ liệu có cấu trúc phân cấp bằng thụt lề khoảng trắng (spaces):

Không sử dụng phím Tab để thụt lề; bắt buộc sử dụng phím cách (phổ biến nhất là 2 khoảng trắng).
Định nghĩa dữ liệu theo cặp key: value. Lưu ý bắt buộc phải có một khoảng trắng (space) sau dấu hai chấm :.
Các phần tử của một danh sách (array) được bắt đầu bằng dấu gạch ngang - và một khoảng trắng.
 

3.2. Các từ khóa điều phối luồng (Workflow Triggers)
name: Tên của luồng công việc hiển thị trên giao diện Actions của GitHub.
on: Định nghĩa các sự kiện (events) kích hoạt quy trình chạy. Phổ biến nhất là push (khi có mã nguồn mới được đẩy lên) hoặc pull_request.
env: Khai báo các biến môi trường (environment variables) dùng chung cho toàn bộ luồng công việc.
 

3.3. Định nghĩa một Job cụ thể
Một Job là đơn vị thực thi dòng lệnh nhỏ nhất trong pipeline. Cấu trúc khai báo một job gồm:

Tên job: Do lập trình viên tự đặt (ví dụ: print_env_job).
runs-on: Xác định loại hệ điều hành hoặc nhãn (label) của máy chủ Runner sẽ thực thi công việc này (ví dụ: ubuntu-latest cho máy chủ của GitHub, hoặc self-hosted cho máy chủ tự cấu hình).
steps: Chuỗi các bước thực thi tuần tự trong cùng một công việc. Một bước có thể chạy một câu lệnh shell (run) hoặc gọi một action đóng gói sẵn (uses).
 

3.4. Sử dụng Actions đóng gói sẵn (Pre-built Actions)
GitHub Actions cung cấp hệ sinh thái các module lệnh có sẵn gọi là "actions" để giảm thiểu thời gian cấu hình:

actions/checkout@v5: Action tự động sao chép (clone) mã nguồn từ kho lưu trữ về máy chủ Runner.

actions/setup-java@v5: Action cấu hình nhanh môi trường Java JDK, giúp bạn không phải viết lệnh tải và cài đặt Java thủ công trên hệ điều hành.

 

4. Demo và thực hành (tạo file Workflow đầu tiên)
4.1. Biên soạn tệp cấu hình
Tạo thư mục .github/workflows/ ở thư mục gốc của dự án user-service. Sau đó tạo tệp tin ci.yml với nội dung sau:

name: Print Environment Info

 

on:

  push:

    branches:

      - main

 

env:

  PROJECT_NAME: "QuickBite-User-Service"

 

jobs:

  print_env_job:

    runs-on: [self-hosted, quickbite] # Chỉ định chạy trên Runner cục bộ

    steps:

      - name: Lấy mã nguồn về máy Runner

        uses: actions/checkout@v5

       

      - name: Thiết lập môi trường Java 17

        uses: actions/setup-java@v5

        with:

          java-version: '17'

          distribution: 'temurin'

         

      - name: In thông tin môi trường

        run: |

          echo "Chuẩn bị in thông tin môi trường cho dự án ${PROJECT_NAME}..."

          java -version

          echo "Tên dự án hiện tại là ${PROJECT_NAME}"

          echo "Nhánh Git đang chạy workflow là ${GITHUB_REF_NAME}"

          echo "Hoàn thành job in thông tin."

 

4.2. Kết quả mong đợi
Khi bạn thực hiện commit và push thư mục .github lên nhánh main, GitHub Server sẽ tự động phát hiện tệp tin này và kích hoạt luồng công việc. Khi kiểm tra tab Actions trên GitHub repository, bạn sẽ thấy job print_env_job chạy thành công (Passed) và hiển thị log:

Thông tin phiên bản JDK 17 (Eclipse Temurin) vừa được cấu hình.
Giá trị biến nội suy QuickBite-User-Service.
Tên nhánh Git đang chạy thông qua biến định sẵn của GitHub GITHUB_REF_NAME.


 

5. Lưu ý, lỗi sai và hiểu lầm thường gặp
Đặt sai đường dẫn tệp tin Workflow: Sinh viên thường đặt tệp tin cấu hình ở thư mục gốc thay vì trong thư mục ẩn .github/workflows/. Hậu quả là GitHub không nhận diện được kịch bản và không có bất kỳ luồng công việc nào được kích hoạt.
Sử dụng phím Tab để thụt lề cấu hình: Cú pháp YAML cấm sử dụng phím Tab. Sinh viên thường quen nhấn Tab khiến hệ thống báo lỗi phân tích cú pháp (YAML syntax error) và từ chối chạy workflow. Bắt buộc phải sử dụng khoảng trắng.
Quên bước checkout mã nguồn: Trong GitHub Actions, mã nguồn không tự động được tải về máy Runner. Nếu quên khai báo bước uses: actions/checkout@v4, máy chủ Runner sẽ bắt đầu ở một thư mục trống, dẫn đến các lệnh liên quan đến tệp tin mã nguồn bị lỗi No such file or directory.
Hiểu lầm về biến môi trường định sẵn: Các biến môi trường bắt đầu bằng GITHUB_ (như GITHUB_REF_NAME, GITHUB_SHA) là các biến tự động do hệ thống cung cấp. Học viên không cần tự khai báo chúng trong khối env mà có thể gọi sử dụng trực tiếp trong lệnh run.
 

6. Tài liệu tham khảo
Cú pháp Workflow của GitHub Actions:

Workflow syntax for GitHub Actions

Danh sách các biến môi trường mặc định (Default environment variables):

Default environment variables

 