#!/usr/bin/env bash
# Local SSH tunnel to the wallet services and the monitoring stack on the VPS, so the Bruno
# collections and a browser on the laptop can reach them.
#
# Blue/green deploy (infra/scripts/deploy.sh) rewrites /opt/fintech/infra/nginx/conf.d/upstream.conf on
# every deploy, moving each service to a new 127.0.0.1 port (base + 1000 per swap). So the REMOTE ports
# of the REST services are read from the live upstream.conf at run time, while only the LOCAL ports are
# fixed at the base values the Bruno collection environments expect (8081-8085).
#
# Everything else is NOT published on the host at all (only nginx is), so those forwards target the
# CONTAINER IP on the docker network:
#   - 9090  ledger gRPC          (deploy.sh publishes one HTTP port per service)
#   - 3000  Grafana              - 9091 Prometheus   - 3100 Loki   - 9093 Alertmanager
# Prometheus uses local 9091 because 9090 is already taken by gRPC.
#
# Usage:
#   infra/scripts/bruno-tunnel.sh
#   VPS_USER=deploy VPS_HOST=160.187.228.112 infra/scripts/bruno-tunnel.sh
#   DRY_RUN=1 infra/scripts/bruno-tunnel.sh      # print the forwards without opening them
#
# Leave the terminal open; Ctrl+C closes the tunnel.
set -euo pipefail

VPS_USER="${VPS_USER:-deploy}"
VPS_HOST="${VPS_HOST:-160.187.228.112}"
REMOTE_CONF="${REMOTE_CONF:-/opt/fintech/infra/nginx/conf.d/upstream.conf}"

# local port (what Bruno/browser uses) : upstream name in upstream.conf
MAP=(
  "8081:tx_engine"
  "8082:ledger_http"
  "8083:payment"
  "8084:notification"
  "8085:audit"
)

# local port : container name : container port
CONTAINERS=(
  "9090:account-ledger-service:9090"
  "3000:grafana:3000"
  "9091:prometheus:9090"
  "3100:loki:3100"
  "9093:alertmanager:9093"
)

echo "[tunnel] reading live ports: ${VPS_USER}@${VPS_HOST}:${REMOTE_CONF}" >&2
CONF="$(ssh "${VPS_USER}@${VPS_HOST}" "cat ${REMOTE_CONF}")"

CONTAINER_NAMES=""
for ENTRY in "${CONTAINERS[@]}"; do
  REST="${ENTRY#*:}"                   # <container>:<port>
  CONTAINER_NAMES+=" ${REST%%:*}"
done
IPS="$(ssh "${VPS_USER}@${VPS_HOST}" \
  "docker inspect -f '{{.Name}} {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${CONTAINER_NAMES} 2>/dev/null || true")"

container_ip() {
  printf '%s\n' "$IPS" | awk -v name="/$1" '$1 == name { print $2 }'
}

ARGS=()
for ENTRY in "${MAP[@]}"; do
  LOCAL="${ENTRY%%:*}"
  NAME="${ENTRY##*:}"
  REMOTE="$(printf '%s\n' "$CONF" | grep -oP "upstream\s+${NAME}\s*\{\s*server 127\.0\.0\.1:\K[0-9]+" || true)"
  if [ -z "$REMOTE" ]; then
    echo "[tunnel] WARN: no port for ${NAME}; skipping local ${LOCAL}" >&2
    continue
  fi
  echo "[tunnel] local ${LOCAL} -> ${NAME} (remote 127.0.0.1:${REMOTE})" >&2
  ARGS+=( -L "${LOCAL}:127.0.0.1:${REMOTE}" )
done

for ENTRY in "${CONTAINERS[@]}"; do
  LOCAL="${ENTRY%%:*}"
  REST="${ENTRY#*:}"
  CONTAINER="${REST%%:*}"
  PORT="${REST##*:}"
  IP="$(container_ip "$CONTAINER")"
  if [ -z "$IP" ]; then
    echo "[tunnel] WARN: ${CONTAINER} has no container IP; skipping local ${LOCAL}" >&2
    continue
  fi
  echo "[tunnel] local ${LOCAL} -> ${CONTAINER} (remote ${IP}:${PORT})" >&2
  ARGS+=( -L "${LOCAL}:${IP}:${PORT}" )
done

if [ "${#ARGS[@]}" -eq 0 ]; then
  echo "[tunnel] nothing to forward - is ${REMOTE_CONF} readable?" >&2
  exit 1
fi

if [ "${DRY_RUN:-}" = "1" ]; then
  echo "[tunnel] DRY_RUN - would run:" >&2
  echo "ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 ${ARGS[*]} ${VPS_USER}@${VPS_HOST}" >&2
  exit 0
fi

echo "[tunnel] Grafana http://127.0.0.1:3000 | Prometheus http://127.0.0.1:9091 | Loki http://127.0.0.1:3100 | Alertmanager http://127.0.0.1:9093" >&2
exec ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 "${ARGS[@]}" "${VPS_USER}@${VPS_HOST}"
