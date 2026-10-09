#!/usr/bin/env bash
# Point the nginx upstream back at the blue container and bring it back up.
# Usage: rollback.sh <service>
set -euo pipefail

SERVICE="$1"; BASE=/opt/fintech

declare -A PORTS=( [api-gateway]=8080 [transaction-engine]=8081 [account-ledger-service]=8082 \
  [payment-gateway-service]=8083 [notification-service]=8084 [audit-service]=8085 [eureka-server]=8761 )
PORT="${PORTS[$SERVICE]:?unknown service}"; GREEN=$((PORT + 1000))

cd "$BASE"

OLD="$(docker ps -a --filter "name=${SERVICE}-old-" --format '{{.Names}}' | sort | tail -1)"
if [ -n "$OLD" ]; then
  # the swap already happened: restore the blue port and restart the old container
  sed -i "s|127\.0\.0\.1:${GREEN}|127.0.0.1:${PORT}|" infra/nginx/conf.d/upstream.conf
  docker exec nginx nginx -t
  docker exec nginx nginx -s reload
  docker rm -f "${SERVICE}-green" >/dev/null 2>&1 || true
  docker start "$OLD" >/dev/null
  docker rename "$OLD" "$SERVICE"
  echo "[rollback] ${SERVICE} restored from ${OLD}"
else
  # the swap never happened: blue is still running, just drop the unhealthy green
  docker rm -f "${SERVICE}-green" >/dev/null 2>&1 || true
  echo "[rollback] ${SERVICE}: blue still running, green removed"
fi
