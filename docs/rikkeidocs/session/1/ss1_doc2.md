Bài đọc: Khái niệm CI/CD (quy trình build, test, deploy)
1. Vấn đề thực tế (câu chuyện Leader và Intern)
Hãy tưởng tượng dự án user-service của QuickBite vừa nhận thêm một bạn thực tập sinh (Intern). Bạn intern này rất chăm chỉ viết code, nhưng vì mới đi làm nên không ai dám đảm bảo chất lượng code của bạn ấy, thậm chí không biết code đẩy lên có bị lỗi cú pháp hay có biên dịch (build) nổi hay không.

Nếu chạy theo quy trình truyền thống:

Mỗi lần bạn intern đẩy một commit nhỏ lên, anh Tech Lead lại phải tải code về máy cá nhân, chạy thử xem có build được không, rồi mới tiến hành review code.
Hậu quả là anh Tech Lead sẽ không còn thời gian làm việc khác. Việc đi sửa các lỗi build vặt vãnh hay chạy test hộ cho intern biến Tech Lead thành một "intern cao cấp", cực kỳ lãng phí tài nguyên của dự án.
Giải pháp thực tế:

Chúng ta tận dụng các dịch vụ hỗ trợ của nền tảng Git (như GitHub hoặc GitLab). Các công cụ này cung cấp cơ chế tự động: Ngay khi bạn intern thực hiện lệnh git push để đẩy code lên, hệ thống sẽ tự động kích hoạt (trigger) một máy chủ độc lập chạy lệnh build và chạy các bài kiểm thử (Unit Test) ngay lập tức. 

Nếu build thất bại hoặc lỗi test -> Hệ thống lập tức báo đỏ, yêu cầu bạn intern tự sửa. Tech Lead hoàn toàn bỏ qua, không cần sờ vào.
Chỉ khi nào hệ thống báo xanh (build & test thành công trên server tập trung) -> Tech Lead mới vào review code. Tiết kiệm hàng giờ làm việc mỗi ngày!
Lưu ý quan trọng: Bước tự động build và chạy unit test để lọc lỗi thô ngay khi push code này mới chỉ là bước đầu tiên (giai đoạn CI) trong một chuỗi dây chuyền. CI/CD thực chất là một Đường ống (Pipeline) dài hơn thế, bao gồm cả đóng gói, quản lý phiên bản và tự động triển khai lên server chạy thật. Quy trình này không chỉ đơn thuần là tự động chạy test.

2. Phân biệt các khái niệm: CI vs CD (Delivery) vs CD (Deployment)
Để giải quyết vấn đề trên, quy trình CI/CD ra đời. CI/CD thực chất là sự chia nhỏ quy trình triển khai thành các giai đoạn tự động nối tiếp nhau:

 



2.1. Continuous Integration (CI - Tích hợp liên tục)
CI là hoạt động tự động kiểm soát chất lượng mã nguồn mỗi khi có thay đổi.

Hành động: Ngay khi lập trình viên gõ git push, máy chủ CI (CI Engine) sẽ tự động clone code về, thực hiện biên dịch (Compile) và chạy toàn bộ các bài kiểm thử tự động (Unit Test).
Lưới an toàn (Unit Test): CI không có ý nghĩa nếu thiếu Unit Test. Unit Test chính là bộ lọc tự động kiểm tra xem code mới có phá vỡ các tính năng cũ hay không.
Tư duy: Fail-fast (Thất bại sớm). Lỗi phải được phát hiện và báo đỏ ngay lập tức để sửa đổi, không để lỗi tồn tại quá lâu trong hệ thống.
2.2. Continuous Delivery (Chuyển giao liên tục) và Continuous Deployment (Triển khai liên tục)
Hai khái niệm này đều viết tắt là CD, nhưng có một ranh giới rất rõ ràng ở bước triển khai:

Continuous Delivery (Chuyển giao): Sau khi CI thành công, mã nguồn được tự động đóng gói (thành file JAR hoặc Docker Image) và sẵn sàng ở trạng thái có thể deploy lên bất cứ môi trường nào. Tuy nhiên, việc kích hoạt deploy lên Production cần một thao tác bấm nút phê duyệt bằng tay (Manual Approval) của người quản trị để đảm bảo an toàn về mặt nghiệp vụ.
Continuous Deployment (Triển khai): Tự động hóa hoàn toàn 100%. Code sau khi qua bước kiểm tra của CI sẽ tự động chạy script cấu hình và deploy thẳng lên Production server chạy thật mà không cần bất kỳ sự can thiệp hay phê duyệt nào của con người.
3. Sơ đồ luồng kiểm soát tự động (Pipeline Flow)
Trong thực tế, đường ống CI/CD vận hành theo nguyên lý nghiêm ngặt: Bất kỳ bước nào thất bại, toàn bộ đường ống sẽ dừng lại ngay lập tức.



Nếu Compile lỗi (ví dụ: viết thiếu dấu chấm phẩy) -> Dừng luôn pipeline, gửi mail cảnh báo.
Nếu Compile thành công nhưng chạy Unit Test có 1/100 test bị fail -> Dừng pipeline, không đóng gói, không deploy để bảo vệ an toàn cho server.
4. Hiểu lầm kinh điển
Hiểu sai: Tôi viết một đoạn script tự động copy file JAR lên server mỗi khi code thay đổi là tôi đã có CI/CD hoàn chỉnh.

Đính chính: Đó mới chỉ là tự động hóa thao tác copy file (Deployment). Nếu bạn bỏ qua bước compile tập trung trên máy chủ CI độc lập và không có bước chạy Unit Test tự động để lọc lỗi logic, bạn mới chỉ làm phần CD (Triển khai), hoàn toàn chưa có cấu phần CI (Tích hợp & Kiểm soát chất lượng).