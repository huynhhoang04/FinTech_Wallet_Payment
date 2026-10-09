#!/usr/bin/env bash
# Re-apply infra/scripts/init-databases.sql (idempotent: skips existing roles/databases).
# First boot of the postgres volume runs it automatically via docker-entrypoint-initdb.d;
# use this after wiping the volume without removing the container.
set -euo pipefail

BASE="$(cd "$(dirname "$0")/../.." && pwd)"
PG_USER="$(grep -oP '^POSTGRES_USER=\K.*' "$BASE/infra/docker/.env")"

docker exec -i postgres psql -U "$PG_USER" -d postgres < "$BASE/infra/scripts/init-databases.sql"
echo "[seed] databases ready"
