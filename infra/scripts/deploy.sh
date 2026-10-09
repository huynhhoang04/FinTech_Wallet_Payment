#!/usr/bin/env bash
# Blue/green per service: start green on (blue port + 1000), health-gate /actuator/health,
# swap infra/nginx/conf.d/upstream.conf, keep the old container stopped for rollback.
# Usage: deploy.sh <service> <git-sha>   (CD runs this on the VPS in /opt/fintech)
#
# Notes:
# - After a successful swap the new container carries the compose container_name, so compose
#   no longer manages this service - all further deploys of it go through this script.
# - Old stopped containers (<service>-old-*) are removed at the start of the NEXT deploy of
#   the same service, which is the rollback window.
set -euo pipefail

SERVICE="$1"; SHA="$2"; BASE=/opt/fintech

declare -A PORTS=( [api-gateway]=8080 [transaction-engine]=8081 [account-ledger-service]=8082 \
  [payment-gateway-service]=8083 [notification-service]=8084 [audit-service]=8085 [eureka-server]=8761 )
PORT="${PORTS[$SERVICE]:?unknown service}"; GREEN=$((PORT + 1000))

cd "$BASE"

# GHCR image names must be lowercase - derive the owner from the clone's origin URL
OWNER="$(git remote get-url origin | sed -E 's#.*[:/]([^/]+)/[^/]+(\.git)?$#\L\1#')"
IMAGE="ghcr.io/${OWNER}/fintech-wallet-${SERVICE}:${SHA}"

MEM_VAR="$(echo "$SERVICE" | tr 'a-z-' 'A-Z_')_MEM"
MEM="$(grep -oP "^${MEM_VAR}=\K.*" infra/docker/.env || echo 512m)"

# drop the previous old (stopped blue) container - new rollback window starts now
docker ps -aq --filter "name=${SERVICE}-old-" | xargs -r docker rm -f >/dev/null

git fetch --all --quiet && git checkout --quiet "$SHA"   # config always comes from git
docker pull "$IMAGE"                                      # needs `docker login ghcr.io` once at bootstrap

docker rm -f "${SERVICE}-green" >/dev/null 2>&1 || true
echo "[deploy] start green ${SERVICE}@${GREEN}"
docker run -d --name "${SERVICE}-green" --network fintech-net --restart unless-stopped \
  --env-file infra/docker/.env -e SPRING_PROFILES_ACTIVE=prod -e SERVER_PORT="$GREEN" \
  --memory "$MEM" \
  -p "127.0.0.1:${GREEN}:${GREEN}" "$IMAGE" >/dev/null

for i in $(seq 1 30); do
  sleep 3
  if curl -fsS "http://127.0.0.1:${GREEN}/actuator/health" | grep -q '"status":"UP"'; then
    echo "[deploy] green healthy - swap nginx upstream"
    sed -i "s|127\.0\.0\.1:${PORT}|127.0.0.1:${GREEN}|" infra/nginx/conf.d/upstream.conf
    docker exec nginx nginx -t
    docker exec nginx nginx -s reload
    docker stop "$SERVICE" >/dev/null
    docker rename "$SERVICE" "${SERVICE}-old-$(date +%s)"
    docker rename "${SERVICE}-green" "$SERVICE"
    echo "[deploy] OK ${SERVICE} -> ${GREEN}"
    exit 0
  fi
done

echo "[deploy] green did not become healthy - rollback"
"$BASE/infra/scripts/rollback.sh" "$SERVICE"
exit 1
