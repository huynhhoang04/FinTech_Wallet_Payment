# Deploy Runbook

Reference for the `devops` skill. Follow top to bottom; do not improvise on the VPS.

## 1. First-time VPS setup (once)

```bash
# 1. Create a deploy user and lock down SSH
adduser deploy && usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh && chmod 700 /home/deploy/.ssh
# paste the CI public key into authorized_keys, chmod 600
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl restart ssh

# 2. Firewall
ufw default deny incoming && ufw default allow outgoing
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable

# 3. Docker
curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy

# 4. Swap (small VPS safety net for JVM/Kafka spikes)
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 5. App directory
sudo -u deploy git clone <repo> /home/deploy/fintech-wallet
cd /home/deploy/fintech-wallet/infra/docker
cp .env.example .env && chmod 600 .env      # fill real values
```

## 2. TLS certificates

```bash
sudo apt install -y certbot
docker compose -f docker-compose.yml stop nginx        # free port 80 for the HTTP-01 challenge
certbot certonly --standalone -d api.<domain> -d grpc-<domain>
docker compose -f docker-compose.yml start nginx
```

Mount `infra/nginx/certs` (or the Let's Encrypt live dir) into the nginx container and verify
auto-renewal: `certbot renew --dry-run`.

## 3. Normal deploy (called by CD)

`infra/scripts/deploy.sh <service> <git-sha>`

```
1. Pull the new image:   docker pull ghcr.io/<org>/fintech-wallet-<service>:<sha>
2. Determine active slot: read infra/nginx/conf.d/upstream.conf -> blue | green
3. Start the new slot:    IMAGE_TAG=<sha> docker compose -p app-<inactive> up -d <service>
4. Wait for health:       poll http://127.0.0.1:<inactive-port>/actuator/health until UP
                          (timeout 120s -> abort and clean up the new slot)
5. Switch traffic:        rewrite upstream.conf -> nginx -t -> nginx -s reload
6. Smoke test:            curl HTTPS /actuator/health, one signed request through the gateway
7. Keep old slot:         leave it stopped for the rollback window (30 min), then remove
8. Record:                echo "<sha> <timestamp> <service>" >> /home/deploy/deploy.log
```

Rules:
- Abort on the first failure; never leave traffic pointed at an unhealthy slot.
- One service at a time. Do not deploy two services concurrently on 8 GB.

## 4. Rollback

```bash
# Fast path: point nginx back at the previous slot and start it
./infra/scripts/rollback.sh            # reads the last good entry from deploy.log
docker compose -p app-blue up -d <service>
docker compose exec nginx nginx -s reload
```

Verify with the smoke test before closing the incident. Rollback must take one command - if it
takes more, the script is wrong, not the operator.

## 5. Smoke test (used by CD)

```bash
curl -fsS https://api.<domain>/actuator/health                 # expect {"status":"UP"}
curl -fsS https://api.<domain>/api/v1/accounts -H "Authorization: Bearer $TEST_JWT"  # expect 200
# one signed transfer with a fresh idempotency key, then assert the balance delta
```

## 6. Incident triage

| Symptom | First checks |
|---|---|
| 502 from Nginx | `docker compose ps`; is the container healthy? `nginx -t` |
| Service flaps | `docker events`, container OOMKilled? `dmesg | grep -i oom` |
| `429` everywhere | Redis rate-limit keys; is the gateway's Redis reachable? |
| Kafka lag grows | consumer group offset, `notification-service` logs, dead-letter table |
| Balance mismatch alert | run reconciliation: `POST /api/v1/reconciliation/run`, inspect `difference` |
| VPS slow | `docker stats`, `free -h`, `df -h`; apply the RAM cut list from the skill |
| Disk full | container log cap, old images (`docker image prune`), Postgres WAL |

## 7. Backup and restore

```bash
# Backup all five databases
./infra/scripts/backup-db.sh          # pg_dump each into infra/backups/<date>/
# Restore one
gunzip -c infra/backups/2026-10-11/ledger_db.sql.gz | \
  docker compose exec -T postgres psql -U wallet -d ledger_db
```

Take a backup **before** every production deploy that touches the ledger schema.

## 8. Golden rules

- Never `docker compose down -v` on the VPS: the `-v` deletes the database volumes.
- Never edit files directly on the VPS; everything comes from git + `.env`.
- Never deploy a tag that is not on `main`.
- Always confirm `docker stats` headroom before starting a dual-slot deploy.
