Bài đọc: Kiểm tra log và truy cập container (logs, exec)
1. Vấn đề thực tế (Kịch bản kiểm tra lỗi Database trong container)
Hãy quay lại tình huống thực tế tại dự án QuickBite:

Ứng dụng user-service báo lỗi không thể kết nối tới database. Anh Tech Lead giao việc cho bạn Intern: "Em hãy vào kiểm tra xem database quickbite_user đã được tạo tự động bên trong container quickbite-db chưa, và kiểm tra xem có log lỗi gì trong Postgres không".

Bạn Intern loay hoay vì file log của Postgres không nằm ngoài máy host. Để truy cập vào database, bạn ấy gõ lệnh:

docker run -it postgres:15-alpine sh

Rắc rối xuất hiện: Bạn Intern thấy mình chui vào shell của máy Linux, nhưng tìm mãi không thấy database nào tên là quickbite_user. 
Lý do: Bạn Intern đã dùng lệnh docker run, lệnh này luôn tạo ra một container mới tinh, trống rỗng từ image gốc, chứ không hề can thiệp vào container database quickbite-db đang chạy lỗi kia.
Để chẩn đoán đúng tiến trình đang chạy mà không làm gián đoạn dịch vụ, chúng ta cần sử dụng hai lệnh chẩn đoán tối quan trọng: docker logs và docker exec.

2. Kiến thức cốt lõi
2.1. Cơ chế Logs của Docker (docker logs)
Docker Daemon tự động thu thập toàn bộ luồng đầu ra tiêu chuẩn (STDOUT) và báo lỗi (STDERR) phát ra từ tiến trình chính bên trong container và lưu lại.

 Để xem toàn bộ log lịch sử từ lúc khởi động: dùng docker logs [tên_container].
 Để theo dõi log thời gian thực (giống lệnh tail -f trong Linux): thêm cờ -f (Follow). Ví dụ: docker logs -f quickbite-db.
 

2.2. Lệnh tương tác trực tiếp (docker exec)
Cho phép bạn chạy một tiến trình mới (ví dụ: một shell terminal sh hoặc bash) ngay bên trong phân vùng cô lập của một container đang chạy.

 Cú pháp bắt buộc để mở Terminal tương tác:
docker exec -it [tên_container] [lệnh_shell]

Ý nghĩa của cờ -it:
-i (interactive): Giữ luồng nhập dữ liệu (STDIN) luôn mở để bạn có thể gõ phím tương tác.
-t (tty): Cấp một Terminal ảo để hiển thị giao diện dòng lệnh giống như một phiên đăng nhập shell Linux thực tế.
3. Các lệnh cốt lõi
Hãy đảm bảo container quickbite-db ở Lesson 4 đang chạy, sau đó thực hiện các bước chẩn đoán lỗi sau:

# 1. Xem toàn bộ log khởi động của database PostgreSQL

docker logs quickbite-db

# 2. Theo dõi log thời gian thực (gõ xong nhấn Ctrl+C để thoát theo dõi)

docker logs -f quickbite-db

# 3. Truy cập vào shell bên trong container database

docker exec -it quickbite-db sh

# ---- CÁC LỆNH CHẠY BÊN TRONG CONTAINER (Terminal của Container) ----

# Gọi công cụ psql truy cập database postgres với user postgres

psql -U postgres

# Liệt kê danh sách các database đang tồn tại

\l

# Thoát khỏi công cụ psql

\q

# Thoát khỏi shell của container để quay về terminal máy host

exit

# ----------------------------------------------------------------

4. Hiểu lầm thường gặp
Hiểu sai: Gõ lệnh exit để thoát khỏi shell của docker exec sẽ làm dừng (stop) container database chính.
Đính chính: Hoàn toàn không. Khi bạn dùng docker exec, Docker Daemon khởi tạo shell dưới dạng một tiến trình con phụ (child process) chạy song song với tiến trình chính của container. Việc bạn gõ exit chỉ tắt tiến trình shell con đó, tiến trình PostgreSQL chính vẫn chạy bình thường.
5. Tài liệu tham khảo
Docker logs CLI reference - Docker Docs

Docker exec CLI reference - Docker Docs

 

 