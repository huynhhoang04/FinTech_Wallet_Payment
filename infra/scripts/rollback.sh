#!/usr/bin/env bash
# Point the nginx upstream back at the previous (blue) container and bring it back up.
# Usage: rollback.sh <service>
#
# The upstream port is taken from the old container's own published port, so a rollback works no
# matter how many blue/green swaps happened before it.
set -euo pipefail

SERVICE="$1"; BASE=/opt/fintech

declare -A UPSTREAM=( [api-gateway]=gateway [transaction-engine]=tx_engine \
  [account-ledger-service]=ledger_http [payment-gateway-service]=payment \
  [notification-service]=notification [audit-service]=audit )
UP="${UPSTREAM[$SERVICE]:-}"

cd "$BASE"

OLD="$(docker ps -a --filter "name=${SERVICE}-old-" --format '{{.Names}}' | sort | tail -1)"
if [ -n "$OLD" ]; then
  OLD_PORT="$(docker port "$OLD" 2>/dev/null | grep -oP '127\.0\.0\.1:\K[0-9]+' | head -1 || true)"
  if [ -n "$UP" ] && [ -n "$OLD_PORT" ]; then
    sed -i "s|^\(upstream *${UP} *{ *server *127\.0\.0\.1:\)[0-9]*|\1${OLD_PORT}|" \
      infra/nginx/conf.d/upstream.conf
    docker exec nginx nginx -t
    docker exec nginx nginx -s reload
    # a failed sync must not turn a rollback into a failure
    bash "$BASE/infra/scripts/sync-prometheus-targets.sh" \
      || echo "[rollback] warning: prometheus targets not synced" >&2
  fi
  docker rm -f "${SERVICE}-green" >/dev/null 2>&1 || true
  docker start "$OLD" >/dev/null
  docker rename "$OLD" "$SERVICE"
  echo "[rollback] ${SERVICE} restored from ${OLD} (port ${OLD_PORT:-unchanged})"
else
  # the swap never happened: blue is still running, just drop the unhealthy green
  docker rm -f "${SERVICE}-green" >/dev/null 2>&1 || true
  echo "[rollback] ${SERVICE}: no previous container, green removed"
fi
