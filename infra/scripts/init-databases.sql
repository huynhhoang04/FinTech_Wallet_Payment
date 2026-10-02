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

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'wallet') THEN
        CREATE ROLE wallet LOGIN PASSWORD 'wallet';
    END IF;
END
$$;

-- CREATE DATABASE không chạy được trong transaction/DO block nên dùng \gexec (psql).
SELECT 'CREATE DATABASE ' || datname || ' OWNER wallet'
FROM (VALUES ('tx_db'), ('ledger_db'), ('payment_db'), ('notification_db'), ('audit_db')) AS databases(datname)
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE pg_database.datname = databases.datname)
\gexec

-- Kiểm tra kết quả:
--   psql -U postgres -c '\l'      -- phải thấy 5 database trên
--   psql -U wallet -d ledger_db -c 'select current_user, current_database();'
