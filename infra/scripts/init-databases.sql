-- Bootstrap PostgreSQL cho toàn hệ thống: 1 instance, mỗi service 1 database riêng.
--
-- Chạy bằng superuser (postgres), ví dụ:
--   psql -U postgres -f infra/scripts/init-databases.sql
--
-- Sau khi chạy xong, các service kết nối được với cấu hình mặc định trong application.yml:
--   POSTGRES_HOST=localhost POSTGRES_PORT=5432 POSTGRES_USER=wallet POSTGRES_PASSWORD=wallet
--   LEDGER_DB=ledger_db TX_DB=tx_db PAYMENT_DB=payment_db
--   NOTIFICATION_DB=notification_db AUDIT_DB=audit_db
--
-- LƯU Ý: mật khẩu 'wallet' chỉ dùng cho môi trường dev/local. Trên VPS phải đặt qua .env.
--
-- Vì sao dùng TEMPLATE template0: nếu hệ điều hành nâng glibc/ICU sau khi cluster Postgres được
-- tạo, Postgres ghi nhận collation version cũ hơn của OS và từ chối CREATE DATABASE từ template1
--   ERROR: template database "template1" has a collation version mismatch
-- template0 không ghi collation version nên né được lỗi này mà không cần sửa metadata của cluster
-- (an toàn khi cluster còn dùng cho việc khác). Cách khắc phục triệt để, nếu muốn:
--   ALTER DATABASE template1 REFRESH COLLATION VERSION;
--   ALTER DATABASE postgres  REFRESH COLLATION VERSION;
-- (cân nhắc REINDEX cho các index trên cột text đã tạo bằng collation cũ).

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'wallet') THEN
        CREATE ROLE wallet LOGIN PASSWORD 'wallet';
    END IF;
END
$$;

-- CREATE DATABASE không chạy được trong transaction/DO block nên dùng \gexec (psql).
SELECT 'CREATE DATABASE ' || datname || ' OWNER wallet TEMPLATE template0'
FROM (VALUES ('tx_db'), ('ledger_db'), ('payment_db'), ('notification_db'), ('audit_db')) AS databases(datname)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE pg_database.datname = databases.datname)
\gexec

-- Kiểm tra kết quả:
--   psql -U postgres -c '\l'      -- phải thấy 5 database trên
--   psql -U wallet -d ledger_db -c 'select current_user, current_database();'
