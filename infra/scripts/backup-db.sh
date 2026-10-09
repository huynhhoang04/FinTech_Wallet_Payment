#!/usr/bin/env bash
# Dump all five databases into backups/<timestamp>/ (gzip), keep 14 days.
set -euo pipefail

BASE="$(cd "$(dirname "$0")/../.." && pwd)"
PG_USER="$(grep -oP '^POSTGRES_USER=\K.*' "$BASE/infra/docker/.env")"

OUT="$BASE/backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

for DB in tx_db ledger_db payment_db notification_db audit_db; do
  docker exec -i postgres pg_dump -U "$PG_USER" "$DB" | gzip > "$OUT/$DB.sql.gz"
  echo "[backup] $DB -> $OUT/$DB.sql.gz"
done

find "$BASE/backups" -maxdepth 1 -type d -name '20*' -mtime +14 -exec rm -rf {} +
