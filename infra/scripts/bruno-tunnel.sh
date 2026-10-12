#!/usr/bin/env bash
# Local SSH tunnel to the wallet services on the VPS, for running Bruno from a laptop.
#
# Blue/green deploy (infra/scripts/deploy.sh) rewrites /opt/fintech/infra/nginx/conf.d/upstream.conf
# on every deploy, moving each service to a new 127.0.0.1 port (base + 1000 per swap). So the
# REMOTE ports are read from the live upstream.conf at run time, while only the LOCAL ports are
# fixed at the base values the Bruno environments expect (8081-8085). That way the collection
# environments never change even though the server ports do.
#
# Usage:
#   infra/scripts/bruno-tunnel.sh
#   VPS_USER=deploy VPS_HOST=160.187.228.112 infra/scripts/bruno-tunnel.sh
#
# Leave the terminal open; Ctrl+C closes the tunnel. The gRPC port 9090 is NOT published by
# deploy.sh (it only publishes one HTTP port per service), so it is not forwarded here.
set -euo pipefail

VPS_USER="${VPS_USER:-deploy}"
VPS_HOST="${VPS_HOST:-160.187.228.112}"
REMOTE_CONF="${REMOTE_CONF:-/opt/fintech/infra/nginx/conf.d/upstream.conf}"

# local port (what Bruno uses) : upstream name in upstream.conf
MAP=(
  "8081:tx_engine"
  "8082:ledger_http"
  "8083:payment"
  "8084:notification"
  "8085:audit"
)

echo "[tunnel] reading live ports: ${VPS_USER}@${VPS_HOST}:${REMOTE_CONF}" >&2
CONF="$(ssh "${VPS_USER}@${VPS_HOST}" "cat ${REMOTE_CONF}")"

ARGS=()
for ENTRY in "${MAP[@]}"; do
  LOCAL="${ENTRY%%:*}"
  NAME="${ENTRY##*:}"
  REMOTE="$(printf '%s\n' "$CONF" | grep -oP "upstream\s+${NAME}\s*\{\s*server 127\.0\.0\.1:\K[0-9]+" || true)"
  if [ -z "$REMOTE" ]; then
    echo "[tunnel] WARN: no port for ${NAME}; skipping local ${LOCAL}" >&2
    continue
  fi
  echo "[tunnel] local ${LOCAL} -> ${NAME} (remote ${REMOTE})" >&2
  ARGS+=( -L "${LOCAL}:127.0.0.1:${REMOTE}" )
done

if [ "${#ARGS[@]}" -eq 0 ]; then
  echo "[tunnel] nothing to forward - is ${REMOTE_CONF} readable?" >&2
  exit 1
fi

echo "[tunnel] note: gRPC 9090 is not published by deploy.sh - not forwarded" >&2
exec ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 "${ARGS[@]}" "${VPS_USER}@${VPS_HOST}"
