#!/usr/bin/env bash
# Keep the Prometheus scrape targets on the ports the services are actually listening on.
#
# Two things break the static targets in infra/monitoring/prometheus/prometheus.yml:
#   - deploy.sh moves every service to a new port on each blue/green swap (base + 1000);
#   - the `git checkout` inside deploy.sh resets that file back to the committed base ports.
# So the targets are rewritten from infra/nginx/conf.d/upstream.conf - the same source of truth
# nginx already uses - and Prometheus is told to reload its config with SIGHUP (which, unlike
# POST /-/reload, needs no --web.enable-lifecycle flag).
#
# Usage: infra/scripts/sync-prometheus-targets.sh   (called by deploy.sh and rollback.sh)
set -euo pipefail

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$BASE"

python3 - infra/nginx/conf.d/upstream.conf infra/monitoring/prometheus/prometheus.yml <<'PY'
import re
import sys

conf, path = sys.argv[1], sys.argv[2]
ports = dict(re.findall(r"upstream\s+(\w+)\s*\{\s*server 127\.0\.0\.1:(\d+)", open(conf).read()))
upstream = {
    "api-gateway": "gateway",
    "transaction-engine": "tx_engine",
    "account-ledger-service": "ledger_http",
    "payment-gateway-service": "payment",
    "notification-service": "notification",
    "audit-service": "audit",
}
text = open(path).read()
for service, name in upstream.items():
    port = ports.get(name)
    if port:
        text = re.sub(r"(?m)^(\s*- )%s:\d+$" % re.escape(service), r"\g<1>%s:%s" % (service, port), text)
open(path, "w").write(text)
PY

grep -E "^\s+- (api-gateway|transaction-engine|account-ledger-service|payment-gateway-service|notification-service|audit-service|eureka-server):" \
	infra/monitoring/prometheus/prometheus.yml | sed 's/^[[:space:]]*- /[prometheus] target /'

docker kill -s HUP prometheus >/dev/null 2>&1 \
	|| echo "[prometheus] reload skipped: container not running" >&2
