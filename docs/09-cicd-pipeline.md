# 09 — Thiết kế pipeline CI/CD hoàn chỉnh (GitHub Actions → GHCR → VPS Docker Compose)

> Thiết kế pipeline **build → test → đóng gói → deploy → giám sát** cho toàn hệ thống FinTech Wallet
> & Payment Gateway trên **1 VPS Docker Compose** (4 vCPU / 8 GB RAM — hard constraint `AGENTS.md`).
>
> Tài liệu tham chiếu chính: 12 session tổng hợp tại `docs/rikkeidocs/session/*` (trích dẫn dạng
> **(S07/doc1)** = `docs/rikkeidocs/session/7/doc1.md`), đề tài gốc `README.MD`, quy ước devops tại
> `.agents/skills/devops/SKILL.md` (gọi tắt **devops-skill**).
>
> **Trạng thái triển khai (2026-10-09): pipeline đã được dựng theo thiết kế này.** Đã có:
> `.github/workflows/backend-ci.yml`, `.github/workflows/backend-cd.yml`,
> `infra/docker/docker-compose.{yml,app.yml,prod.yml}` + `.env.example`, `infra/nginx/*`,
> `infra/monitoring/*`, `infra/scripts/{deploy,rollback,backup-db,seed}.sh`. Jib đã được khai báo
> trong POM của cả 7 service (parent giữ cấu hình dùng chung: temurin 21-jre, non-root, tag `latest`).
>
> Khác biệt có chủ đích so với các đoạn ví dụ trong tài liệu (lý do ghi ngay trong code):
> 1. `proto`/`common` khai báo jib với `skip=true` — goal `jib:build` với `-am` chạy trên toàn reactor,
>    không khai báo thì build fail ở module không có main class.
> 2. Job `changes` strip prefix `backend/` trước khi `cut -d/ -f1` (đường dẫn repo là `backend/<module>/...`).
> 3. Tên image GHCR phải lowercase: CD lấy `${GITHUB_REPOSITORY_OWNER,,}`, `deploy.sh` suy ra từ
>    `git remote get-url origin`.
> 4. nginx chạy `network_mode: host` để khớp upstream `127.0.0.1:<port>` và cách publish green
>    `127.0.0.1:<green>` của `deploy.sh`; vì vậy nó nằm trong `docker-compose.prod.yml`. `grpc.conf`
>    đi qua upstream `ledger_grpc` (loopback) thay vì DNS container. Các service chỉ publish port ra
>    127.0.0.1 — không port nào public ra Internet.
> 5. `deploy.sh` rename blue cũ thành `<service>-old-<ts>` trước khi nhận tên `<service>` cho green
>    (tránh conflict tên container); các container `-old-*` được dọn ở đầu lần deploy kế tiếp của cùng
>    service — đó là rollback window.
> 6. Alert `ServiceDown` và Prometheus scrape gồm cả `eureka-server:8761`.
>
> Trước khi pipeline chạy thật: hoàn thành mục 1 (checklist), mục 2 (VPS bootstrap) và khai báo
> secrets ở mục 9.

---

## 0. Tổng quan luồng

```
 git push / PR ──► CI (backend-ci.yml)                    [chặn merge nếu đỏ]
                   checkout → JDK 21 + cache Maven → ./mvnw -B verify
                   (unit + integration Testcontainers) → JaCoCo → gitleaks → trivy fs
                                                   │
 merge vào main ──► CD (backend-cd.yml)                   [chỉ chạy trên main]
                   verify lại merge commit → Jib build 7 image → push GHCR (tag = SHA)
                   → SSH vào VPS → deploy.sh <service> <sha> (blue/green per service)
                   → health-gate /actuator/health → swap nginx upstream → smoke test
                   → OK: dọn container cũ   |   FAIL: rollback.sh + báo Telegram
                                                   │
                   Production VPS ◄────── Nginx (443/TLS, REST + gRPC) ──► 7 service
                                                   │
                   Prometheus (scrape 15s) → Grafana + Alertmanager → Telegram
```

Nguyên tắc xuyên suốt (lấy từ giáo trình và quy ước dự án):

1. **Không deploy image chưa test** — `mvn verify` xanh mới được build image (devops-skill §1.6; S1/ss1_doc2).
2. **Mọi thứ reproducible từ repo** — không bước thủ công nào chỉ tồn tại trên một máy (devops-skill §1; S1/ss1_doc1 về nỗi đau triển khai thủ công).
3. **Không secret trong repo** — Compose đọc `.env`, CI đọc GitHub Secrets (devops-skill §1.3).
4. **Healthcheck là cổng bắt buộc** — chưa healthy thì chưa tính là deployed (devops-skill §1.4; README §4 yêu cầu blue/green không rơi giao dịch).
5. **Một VPS, Docker Compose, không Kubernetes** (AGENTS.md §2; devops-skill §1.1).

---

## 1. Checklist những thứ phải chuẩn bị (trước khi pipeline chạy được)

| # | Thứ cần có | Chi tiết / giá trị khuyến nghị | Vì sao cần | Trích dẫn |
|---|---|---|---|---|
| 1 | **VPS** | Ubuntu 22.04/24.04 LTS, 4 vCPU, **8 GB RAM**, SSD ≥ 50 GB, 1 IPv4 public | Chỗ chạy toàn bộ hệ thống; RAM budget chi tiết ở mục 7 | README §4 Step 1; S10/doc1 |
| 2 | **Domain name** | ví dụ `wallet.example.com` — bắt buộc nếu muốn HTTPS thật; chấp nhận domain miễn phí (DuckDNS) nếu chỉ demo | Let's Encrypt chỉ cấp cert cho domain, không cấp cho IP | S11/doc4 |
| 3 | **DNS record** | `A` record: `wallet.<domain>` → IP VPS; (tuỳ chọn) `grpc.<domain>` nếu expose gRPC ra ngoài | Certbot HTTP-01 + client gọi API qua tên miền | S11/doc4 |
| 4 | **GitHub repository** | Branch `main` được bảo vệ, CI là **required status check** | Không merge được khi pipeline đỏ | devops-skill §5; S7/doc1 |
| 5 | **SSH key pair dành riêng cho deploy** | `ssh-keygen -t ed25519 -C "github-deploy"` — private key đưa vào GitHub Secrets, public key nạp vào user `deploy` trên VPS | CD SSH vào VPS mà không lộ mật khẩu; tắt password login | S10/doc2; devops-skill §5 |
| 6 | **Tài khoản Telegram Bot + Chat ID** | Tạo bot qua BotFather → lấy `BOT_TOKEN`; lấy `CHAT_ID` nhóm ops | Alertmanager và CD notify cùng báo qua 1 kênh | S14/doc5; devops-skill §8 |
| 7 | **GHCR (GitHub Container Registry)** | Dùng sẵn `GITHUB_TOKEN` của Actions (`packages: write`) — không cần PAT riêng nếu repo thuộc tài khoản/org hiện tại | Nơi chứa image do Jib build, VPS pull về khi deploy | devops-skill §4; S8/doc3-4 |
| 8 | **SMTP cho notification-service** | MailHog/Mailpit (demo) hoặc SMTP thật; biến `SMTP_HOST/PORT`, `MAIL_FROM` | Thông báo biến động số dư qua email | docs/02; `notification/application.yml` |
| 9 | **GitHub Secrets** (đăng ký tại *Settings → Secrets and variables → Actions*) | Xem bảng đầy đủ ở mục 9 | CI/CD đọc secret từ đây, tuyệt đối không commit | devops-skill §1.3 |
| 10 | **File `.env` trên VPS** | Tạo thủ công tại `/opt/fintech/.env` từ `.env.example` (1 lần), `chmod 600` | Compose prod đọc biến môi trường | devops-skill §1.3, §3 |

> **Domain có bắt buộc không?** Có, nếu muốn HTTPS + webhook provider gọi về đúng chuẩn (provider
> thật thường từ chối callback http/IP trần). Nếu chỉ chấm demo nội bộ, có thể tạm dùng IP + self-signed,
> nhưng thiết kế này mặc định **có domain** vì chi phí domain miễn phí là đủ (S11/doc4).

## 2. VPS bootstrap — làm đúng 1 lần

> Toàn bộ các bước dưới thực hiện tay **một lần duy nhất** khi nhận VPS. Sau đó mọi thay đổi đều đi
> qua pipeline. (Nền tảng lý thuyết: S1/ss1_doc1 — triển khai thủ công là nguồn của drift;
> S10/doc1–doc4 — VPS, SSH hardening, UFW, Docker, đồng bộ cấu hình bằng Git.)

### 2.1. Cập nhật hệ điều hành + tạo user deploy + khoá SSH

```bash
# 1. Vá bảo mật hệ điều hành (S10/doc1)
sudo apt update && sudo apt upgrade -y

# 2. Tạo user chạy deploy, không dùng root (S10/doc2)
sudo adduser --disabled-password --gecos "" deploy
sudo usermod -aG docker deploy          # sau khi cài Docker ở 2.2
sudo mkdir -p /home/deploy/.ssh
sudo vi /home/deploy/.ssh/authorized_keys   # dán PUBLIC KEY của "github-deploy" (mục 1.5)
sudo chmod 700 /home/deploy/.ssh && sudo chmod 600 /home/deploy/.ssh/authorized_keys
sudo chown -R deploy:deploy /home/deploy/.ssh

# 3. Khoá SSH: chỉ cho phép key, cấm password, cấm root login (S10/doc2)
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
sudo systemctl reload ssh
```

**Vì sao:** private key của user `deploy` nằm trong GitHub Secrets (`VPS_SSH_KEY`) — đây là cách CD
vào được VPS mà không có mật khẩu nào tồn tại ở dạng văn bản (S10/doc2, devops-skill §5).

### 2.2. Tường lửa UFW — chỉ mở 22/80/443

```bash
# S10/doc3 — mọi cổng service nội bộ đều KHÔNG publish ra Internet
sudo ufw default deny incoming && sudo ufw default allow outgoing
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
sudo ufw enable && sudo ufw status verbose
```

Nginx là **single entry point** duy nhất (80/443) — PostgreSQL 5432, Redis 6379, Kafka 9092,
Prometheus 9090, Grafana 3000, các service 8081–8085 đều chỉ bind trong mạng Docker nội bộ
(S11/doc1, devops-skill §3). Truy cập Grafana/Prometheus khi debug: SSH tunnel
`ssh -L 3000:127.0.0.1:3000 deploy@<vps>` (tư tưởng bind loopback của S17/doc1 §3.4).

### 2.3. Cài Docker + plugin Compose

```bash
# S10/doc3
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker deploy
docker --version && docker compose version
```

### 2.4. Đồng bộ repo cấu hình lên VPS (read-only)

```bash
# S10/doc4 — cấu hình trên VPS luôn đi từ Git, không sửa tay
sudo mkdir -p /opt/fintech && sudo chown deploy:deploy /opt/fintech
# Tạo deploy-key read-only trên GitHub (Settings → Deploy keys) cho repo, rồi:
sudo -u deploy git clone git@github.com:<org>/FinTech_Wallet_Payment.git /opt/fintech
sudo -u deploy git -C /opt/fintech checkout main
```

### 2.5. File `.env` trên VPS (1 lần, thủ công có kiểm soát)

```bash
cd /opt/fintech/infra/docker
cp .env.example .env && chmod 600 .env
# Điền: POSTGRES_PASSWORD, JWT_SECRET (base64 ≥ 32 byte), HMAC_SECRET, INTERNAL_TOKEN,
#       EUREKA_USER/EUREKA_PASSWORD, SMTP_*, TELEGRAM_*, secret webhook từng provider
```

Sinh secret: `openssl rand -base64 48`. Tuyệt đối không commit `.env` (AGENTS.md §10; S16/doc1 —
không để secret lọt vào log/repo).

### 2.6. Bật stack hạ tầng lần đầu

```bash
# Postgres init script tạo sẵn 5 DB (tx_db, ledger_db, payment_db, notification_db, audit_db)
# chỉ chạy khi volume trống (devops-skill §3)
docker compose -f infra/docker/docker-compose.yml up -d          # postgres, redis, kafka, monitoring
docker compose ps --format 'table {{.Name}}\t{{.Status}}'        # mọi container phải healthy
```

### 2.7. Certbot — TLS tự gia hạn

```bash
# S11/doc4 — HTTP-01 challenge qua cổng 80
sudo apt install -y certbot
sudo certbot certonly --standalone -d wallet.<domain> --pre-hook "docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml stop nginx" \
  --post-hook "docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml start nginx"
sudo systemctl enable --now certbot.timer     # hoặc systemd timer tự viết gọi lệnh trên định kỳ
```

Cert mount read-only vào container nginx: `/etc/letsencrypt:/etc/letsencrypt:ro`. Kiểm tra cấu hình
trước khi reload: `nginx -t` (devops-skill §7).

## 3. CI — `.github/workflows/backend-ci.yml`

**Mục tiêu:** mọi push/PR đều phải qua build + **unit test + integration test (Testcontainers)** +
quét bảo mật. Pipeline này là **required status check** của `main` (devops-skill §5). Khái niệm CI:
(S1/ss1_doc2), cấu trúc workflow: (S7/doc2), phân tách job: (S7/doc3), build Java trong CI: (S7/doc4).

```yaml
name: backend-ci

on:
  push:
    branches: [main]
    paths: ['backend/**', '.github/workflows/**']
  pull_request:
    paths: ['backend/**']

concurrency:                      # hủy run cũ của cùng một nhánh/PR để tiết kiệm phút CI
  group: backend-ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-verify:
    runs-on: ubuntu-latest        # GitHub-hosted; Testcontainers cần Docker — ubuntu-latest có sẵn
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'      # hard constraint: Java 21 LTS (AGENTS.md §2)
          cache: maven            # cache ~/.m2 giữa các run (S7/doc4)

      - name: Build + unit + integration tests
        working-directory: backend
        run: ./mvnw -B verify     # surefire (unit) + failsafe (IT + Testcontainers)
                                  # chạy y hệt lệnh local → không "chạy được ở máy tôi"

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            backend/**/target/surefire-reports
            backend/**/target/failsafe-reports
          if-no-files-found: ignore

  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }  # gitleaks cần full history

      - name: Gitleaks — chặn secret lọt vào repo
        uses: gitleaks/gitleaks-action@v2     # AGENTS.md §10: không commit .env/secret

      - name: Trivy — quét lỗ hổng dependency/filesystem
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: fs
          scan-path: backend
          severity: CRITICAL,HIGH

  app-check:                       # app/ là client React (Expo) — chỉ typecheck
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: app/web } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20, cache: npm, cache-dependency-path: app/web/package-lock.json }
      - run: npm ci || npm install
      - run: npx tsc --noEmit      # AGENTS.md §8 — kiểm tra TS trước khi merge
```

**Quy tắc CI:**

- `./mvnw -B verify` chạy **đầy đủ** unit + integration (Testcontainers dựng Postgres/Kafka/Redis
  thật trong CI) — bao gồm bộ test bắt buộc của đường tiền: entries cân 0, snapshot = SUM(entries),
  cùng idempotency key chỉ post 1 lần, A↔B đồng thời không deadlock, ledger chết → 503
  (backend-skill §9). Đây chính là yêu cầu "chạy test kế toán kép và idempotency trước khi deploy"
  của đề tài (README §4 Step 4.1).
- CI đỏ = không merge được vào `main` (branch protection, devops-skill §5).
- Lệnh local đồng nhất: `cd backend && ./mvnw -B verify` (devops-skill §5).

## 4. CD — `.github/workflows/backend-cd.yml`

**Mục tiêu:** merge vào `main` → image mới lên GHCR → deploy **không downtime** từng service lên VPS.
Khái niệm CD: (S1/ss1_doc2); build & push image trong pipeline: (S8/doc1, doc3–4); truyền artifact
giữa job bằng `needs:`: (S7/doc3). Dự án dùng **Jib thay Dockerfile** (lý do ở mục 12).

```yaml
name: backend-cd

on:
  push:
    branches: [main]
    paths: ['backend/**']

concurrency:                      # 2 lần deploy song song trên 1 VPS là tai nạn — chỉ 1 run/main
  group: backend-cd-main
  cancel-in-progress: false

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ghcr.io/${{ github.repository_owner }}/fintech-wallet

jobs:
  # ---------- Job 1: verify lại merge commit (không bao giờ deploy code chưa test) ----------
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - working-directory: backend
        run: ./mvnw -B verify

  # ---------- Job 2: phát hiện service thay đổi ----------
  changes:
    needs: verify
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.diff.outputs.services }}
      services_json: ${{ steps.diff.outputs.services_json }}
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 2 }
      - id: diff
        working-directory: backend
        run: |
          # proto/ hoặc common/ đổi → build lại TẤT CẢ (chúng nằm trong -am của mọi service)
          CHANGED=$(git diff --name-only HEAD~1 HEAD | cut -d/ -f1 | sort -u)
          if echo "$CHANGED" | grep -qE '^(proto|common)$'; then
            SERVICES="eureka-server api-gateway transaction-engine account-ledger-service payment-gateway-service notification-service audit-service"
          else
            SERVICES=$(echo "$CHANGED" | grep -E '^(eureka-server|api-gateway|transaction-engine|account-ledger-service|payment-gateway-service|notification-service|audit-service)$' | tr '\n' ' ')
          fi
          echo "services=$SERVICES" >> "$GITHUB_OUTPUT"
          echo "services_json=$(echo $SERVICES | jq -R -s -c 'split(" ") | map(select(length>0))')" >> "$GITHUB_OUTPUT"

  # ---------- Job 3: Jib build + push GHCR (ma trận theo service thay đổi) ----------
  build-push:
    needs: changes
    if: needs.changes.outputs.services != ''
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        service: ${{ fromJSON(needs.changes.outputs.services_json) }}   # xem chú ý bên dưới
    permissions:
      contents: read
      packages: write               # GITHUB_TOKEN đẩy image lên GHCR — không cần PAT
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }

      - name: Jib build + push (tag = git SHA + latest)
        working-directory: backend
        env:
          GHCR_USER: ${{ github.actor }}
          GHCR_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          ./mvnw -B -pl ${{ matrix.service }} -am jib:build \
            -Djib.to.image=${IMAGE_PREFIX}-${{ matrix.service }}:${{ github.sha }} \
            -Djib.to.auth.username=$GHCR_USER -Djib.to.auth.password=$GHCR_TOKEN \
            -Djib.additionalTags=latest
        # Jib build không cần Docker daemon → không cần DinD/DooD (S8/doc1); -am bắt buộc
        # để build proto + common trước (devops-skill §4)

  # ---------- Job 4: deploy blue/green từng service lên VPS qua SSH ----------
  deploy:
    needs: build-push
    runs-on: ubuntu-latest
    environment: production          # GitHub Environment — có thể gắn required reviewer
    steps:
      - uses: actions/checkout@v4  # để có infra/scripts/deploy.sh trong workspace

      - name: SSH deploy từng service
        env:
          VPS_HOST: ${{ secrets.VPS_HOST }}
          VPS_USER: ${{ secrets.VPS_USER }}
          VPS_SSH_KEY: ${{ secrets.VPS_SSH_KEY }}
        run: |
          mkdir -p ~/.ssh && chmod 700 ~/.ssh
          echo "$VPS_SSH_KEY" > ~/.ssh/deploy_key && chmod 600 ~/.ssh/deploy_key
          ssh-keyscan -H "$VPS_HOST" >> ~/.ssh/known_hosts
          for SVC in ${{ needs.changes.outputs.services }}; do
            ssh -i ~/.ssh/deploy_key "$VPS_USER@$VPS_HOST" \
              "/opt/fintech/infra/scripts/deploy.sh $SVC ${{ github.sha }}" \
              || { echo "DEPLOY FAILED: $SVC — rollback đã chạy trong script"; exit 1; }
          done

      - name: Smoke test qua nginx (health + 1 API public)
        run: |
          curl -fsS --retry 5 --retry-delay 3 "https://${{ secrets.PUBLIC_DOMAIN }}/actuator/health" \
            | grep -q '"status":"UP"'
          curl -fsS "https://${{ secrets.PUBLIC_DOMAIN }}/api/v1/auth/login" -o /dev/null -w '%{http_code}' \
            | grep -qE '400|405'   # endpoint tồn tại (400/405 chứng minh route sống, chưa cần body)

      - name: Notify Telegram
        if: always()
        run: |
          curl -s "https://api.telegram.org/bot${{ secrets.TELEGRAM_BOT_TOKEN }}/sendMessage" \
            -d chat_id="${{ secrets.TELEGRAM_CHAT_ID }}" \
            -d text="CD ${{ job.status }} — ${{ github.sha }} — services: ${{ needs.changes.outputs.services }}"
```

**Chú ý kỹ thuật:**

- `services_json`: job `changes` nên xuất thêm dạng JSON array cho `strategy.matrix`
  (`SERVICES_JSON=$(jq -R -s -c 'split(" ")[:-1]' <<< "$SERVICES")`) — còn `services` (chuỗi) dùng
  cho vòng lặp deploy tuần tự (deploy **tuần tự**, không song song: VPS 8 GB không chịu nổi 2 bản
  green cùng lúc, devops-skill §5–6).
- Deploy dùng SSH key (S10/doc2); `environment: production` cho phép bắt reviewer duyệt tay trước
  khi đẩy lên prod nếu muốn.
- Smoke test fail → job fail; rollback tự động nằm trong `deploy.sh` (mục 5) chứ không chờ smoke.

## 5. Blue/green **theo từng service** — `infra/scripts/deploy.sh` + `rollback.sh`

Blue/green toàn hệ thống không vừa 8 GB RAM, nên áp dụng **per service** (devops-skill §5;
README §4 Step 4.2 yêu cầu không rơi giao dịch khi nâng cấp). Ý tưởng:

1. Service đang chạy ở cổng **blue** (8081), container mới khởi động ở cổng **green** (8181 = blue+1000) trên `127.0.0.1`.
2. Health-gate: chờ `GET 127.0.0.1:8181/actuator/health` = `UP` (tối đa 90s).
3. Swap `infra/nginx/conf.d/upstream.conf` sang 8181 → `nginx -t && nginx -s reload` (zero downtime — Nginx giữ kết nối cũ, S11/doc2-3).
4. Container cũ **stop nhưng không xoá** trong rollback window (30 phút).
5. Bất kỳ bước nào fail → `rollback.sh` tự chạy: upstream trỏ lại blue, start lại container cũ, báo Telegram.

```bash
#!/usr/bin/env bash
# infra/scripts/deploy.sh <service> <git-sha>
set -euo pipefail
SERVICE="$1"; SHA="$2"; BASE=/opt/fintech
IMAGE="ghcr.io/<org>/fintech-wallet-${SERVICE}:${SHA}"

declare -A PORTS=( [api-gateway]=8080 [transaction-engine]=8081 [account-ledger-service]=8082 \
  [payment-gateway-service]=8083 [notification-service]=8084 [audit-service]=8085 [eureka-server]=8761 )
PORT="${PORTS[$SERVICE]:?unknown service}"; GREEN=$((PORT + 1000))

cd "$BASE"
git fetch --all --quiet && git checkout --quiet "$SHA"          # cấu hình luôn đi từ Git (S10/doc4)
docker pull "$IMAGE"                                            # cần `docker login ghcr.io` 1 lần ở bootstrap

echo "[deploy] start green ${SERVICE}@${GREEN}"
docker run -d --name "${SERVICE}-green" --network fintech-net --restart unless-stopped \
  --env-file infra/docker/.env -e SPRING_PROFILES_ACTIVE=prod -e SERVER_PORT="$GREEN" \
  -e JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70" \
  --memory "$(grep -oP "$(echo $SERVICE | tr 'a-z-' 'A-Z_')_MEM=\K.*" infra/docker/.env || echo 512m)" \
  -p "127.0.0.1:${GREEN}:${GREEN}" "$IMAGE" >/dev/null

for i in $(seq 1 30); do                                        # health-gate 90s
  sleep 3
  if curl -fsS "http://127.0.0.1:${GREEN}/actuator/health" | grep -q '"status":"UP"'; then
    echo "[deploy] green healthy"
    sed -i -E "s|(${SERVICE//-/.}*127\.0\.0\.1:)${PORT}|\\1${GREEN}|" infra/nginx/conf.d/upstream.conf
    docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml exec nginx nginx -t
    docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml exec nginx nginx -s reload
    docker stop "$SERVICE" >/dev/null                           # GIỮ stopped cho rollback window
    docker rename "${SERVICE}-green" "$SERVICE"
    echo "[deploy] OK ${SERVICE} -> ${GREEN}"
    exit 0
  fi
done

echo "[deploy] green KHÔNG healthy — rollback"
"$BASE/infra/scripts/rollback.sh" "$SERVICE"; exit 1
```

```bash
#!/usr/bin/env bash
# infra/scripts/rollback.sh <service> — trỏ upstream về bản cũ và start lại nó
set -euo pipefail
SERVICE="$1"; BASE=/opt/fintech
cd "$BASE"
git checkout --quiet main                                       # upstream.conf quay về trạng thái blue
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.app.yml exec nginx nginx -s reload
docker rm -f "${SERVICE}-green" 2>/dev/null || true
docker start "$SERVICE"
echo "[rollback] ${SERVICE} quay về bản trước đó"
```

**Giới hạn RAM khi blue+green cùng sống:** tạm +~300–500 MB/service trong vài phút — nằm trong
headroom ~2 GB của budget (mục 7). Tuyệt đối không deploy song song nhiều service (CD chạy tuần tự).

## 6. Nginx edge — TLS + REST + gRPC (S11/doc1–doc4)

Nginx là **cổng duy nhất** publish ra Internet (devops-skill §7):

- `infra/nginx/conf.d/api.conf` — `listen 443 ssl http2`, cert Let's Encrypt, redirect 80→443,
  `proxy_pass http://127.0.0.1:8080` (api-gateway), rate-limit zone ở edge, `client_max_body_size 1m`,
  HSTS. Đây là lớp trước API Gateway — gateway vẫn tự kiểm HMAC/JWT (docs/08 mục G-1/G-4).
- `infra/nginx/conf.d/grpc.conf` — `grpc_pass grpc://account-ledger-service:9090` cho client gRPC
  ngoài (nếu cần), pattern y hệt ví dụ `grpc-ledger` trong README §4 Step 3.
- `infra/nginx/conf.d/upstream.conf` — map `service → 127.0.0.1:<port>`; **file duy nhất bị
  `deploy.sh` sửa** khi swap blue/green.

```nginx
# infra/nginx/conf.d/upstream.conf (trích)
upstream tx_engine { server 127.0.0.1:8081; }        # blue; deploy.sh sed sang 8181 khi green
```

## 7. Docker Compose trên VPS + ngân sách RAM

3 file tách vai trò (devops-skill §2–3):

| File | Vai trò |
|---|---|
| `infra/docker/docker-compose.yml` | hạ tầng: postgres (init 5 DB), redis, kafka (KRaft), prometheus, grafana, loki, promtail, alertmanager |
| `infra/docker/docker-compose.app.yml` | 7 service backend + nginx |
| `infra/docker/docker-compose.prod.yml` | override prod: `restart: unless-stopped`, `mem_limit` khớp `-XX:MaxRAMPercentage=70`, log cap `max-size: 10m / max-file: 3`, service bind `127.0.0.1:<port>` (không public) |

**Ngân sách RAM 8 GB** (devops-skill §6 — không vượt trần này):

| Thành phần | ~RAM | | Thành phần | ~RAM |
|---|---|---|---|---|
| OS + Docker | 800 MB | | Prometheus + Grafana | 500 MB |
| Nginx | 50 MB | | Loki + Promtail | 300 MB |
| 5 JVM service | ~2.3 GB | | Alertmanager | 50 MB |
| PostgreSQL | 500 MB | | Kafka KRaft | 1.2 GB |
| Redis | 150 MB | | **Tổng** | **~5.9 GB** |

Khi thiếu RAM cắt theo thứ tự: Loki → Grafana/Prometheus (chạy off-VPS) → tracing; **không bao giờ**
cắt Postgres/Redis/Kafka (devops-skill §6). Kiểm tra: `docker stats --no-stream`.

## 8. Monitoring & Alerting — mắt nhìn của pipeline sau deploy

> Khái niệm metrics + pull model Prometheus (S13/doc1); Spring Actuator expose (S13/doc2);
> Prometheus bằng compose (S13/doc3); node-exporter (S13/doc4); Grafana (S14/doc1–2); dashboard
> VPS/Spring Boot (S14/doc3–4); alert khi service sập (S14/doc5). Logging production JSON stdout
> (S16/doc1, doc4) + traceId MDC (S16/doc5); tập trung hoá log — xem lưu ý Loki ở mục 12.

### 8.1. Prometheus scrape (`infra/monitoring/prometheus/prometheus.yml`)

```yaml
global:
  scrape_interval: 15s            # chu kỳ kéo metrics (S13/doc1)
scrape_configs:
  - job_name: backend
    metrics_path: /actuator/prometheus     # mọi service đã expose (docs/08 STT 55)
    static_configs:
      - targets: [api-gateway:8080, transaction-engine:8081, account-ledger-service:8082,
                  payment-gateway-service:8083, notification-service:8084, audit-service:8085]
  - job_name: node
    static_configs: [{ targets: [node-exporter:9100] }]   # RAM/disk/CPU VPS (S13/doc4)
  - job_name: cadvisor
    static_configs: [{ targets: [cadvisor:8080] }]        # metrics per container
```

### 8.2. Alert rules tài chính — **không được thương lượng** (`alert-rules.yml`)

| Alert | Điều kiện | Vì sao |
|---|---|---|
| `LedgerImbalance` | `ledger_imbalance_total != 0` | Nợ ≠ Có trong sổ cái = tiền bị sinh/mất (README §4 Step 5; audit STT 37/54) |
| `TxErrorRateHigh` | rate lỗi giao dịch > 2% trong 5m | README §4 Step 5 |
| `ServiceDown` | `up == 0` trong 1m | (S14/doc5) |
| `VpsResourcePressure` | RAM hoặc disk > 85% | headroom mỏng (mục 7) |
| `ReconciliationStale` | `time() - reconciliation_last_success_timestamp_seconds > 90000` | đối chuẩn đêm im lặng là mất lớp bảo vệ cuối |

Alertmanager → Telegram (cùng bot với CD notify). Dashboard Grafana tối thiểu: throughput, p95
latency, error rate, tài nguyên VPS, **panel `ledger_imbalance_total`** (devops-skill §8).

### 8.3. Log tập trung

Mọi service ghi **JSON ra stdout** (không ghi file trong container — 12-factor devops-skill §9;
S16/doc3–4), Promtail kéo sang Loki, truy vấn theo `X-Request-Id` mà gateway gắn (docs/08 G-2;
tư tưởng traceId của S16/doc5). Quy tắc bảo mật log: không password/token/PII trong log
(S16/doc1 §3.4) — kiểm chứng bằng `gitleaks` trong CI + review.


## 9. Kho secret — ai giữ, ai dùng

> Nguyên tắc: **không secret trong repo** (devops-skill §1.3); CI đọc GitHub Secrets, VPS đọc
> `/opt/fintech/infra/docker/.env` (chmod 600). Không ghi secret vào log (S16/doc1).

| Secret | Nơi khai báo | Ai dùng | Ghi chú |
|---|---|---|---|
| `VPS_HOST`, `VPS_USER` | GitHub Secrets | CD job deploy | IP/hostname + user `deploy` |
| `VPS_SSH_KEY` | GitHub Secrets | CD job deploy | private key ed25519 của user `deploy` (mục 2.1) |
| `PUBLIC_DOMAIN` | GitHub Secrets (hoặc Variables) | smoke test CD | ví dụ `wallet.example.com` |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | GitHub Secrets + `.env` VPS | CD notify + Alertmanager | cùng 1 bot |
| `GITHUB_TOKEN` (packages:write) | có sẵn trong Actions | Jib push GHCR | không cần PAT nếu cùng org; VPS cần `docker login ghcr.io` 1 lần bằng PAT read:packages |
| `JWT_SECRET`, `HMAC_SECRET`, `INTERNAL_TOKEN` | `.env` VPS | mọi service | `openssl rand -base64 48`; HS256 + HMAC + nội bộ (docs/08 G-1/G-6/G-7) |
| `POSTGRES_PASSWORD`, `EUREKA_USER/PASSWORD` | `.env` VPS | compose | |
| `MOMO_SECRET`, `ZALOPAY_SECRET`, ... | `.env` VPS | payment webhook verify | map vào `PaymentProperties.providerSecrets()` (docs/08 STT 25) |
| `SMTP_HOST/PORT`, `MAIL_FROM` | `.env` VPS | notification-service | MailHog khi demo |
| `APP_PAYMENT_DEMO_ENDPOINTS=false` | `.env` VPS | payment | **bắt buộc ở prod** — tắt đường đúc tiền demo (docs/08 STT 27) |
| `ADMIN_PHONES` | `.env` VPS | ledger auth | allowlist role ADMIN (docs/08 STT 2) |

## 10. Bảo mật của chính pipeline

1. **Branch protection `main`**: bắt PR + CI xanh (required check `backend-ci`), cấm push thẳng.
2. **Secret scan mọi push**: `gitleaks` trong CI (AGENTS.md §10 — commit `.env`/secret là anti-pattern).
3. **Quét lỗ hổng**: `trivy fs` quét dependency ở CI; có thể mở rộng `trivy image` sau bước Jib.
4. **Quyền tối thiểu**: `GITHUB_TOKEN` chỉ `packages: write` + `contents: read` ở job build; job
   deploy không đụng registry. Runner GitHub-hosted sạch mỗi lần chạy (S7/doc1).
5. **`environment: production`** ở job deploy — bật *required reviewers* nếu muốn người duyệt tay
   trước khi chạm VPS.
6. **VPS**: SSH chỉ key, UFW 22/80/443, mọi cổng nội bộ bind loopback (S10/doc2-3, S11/doc1,
   S17/doc1 §3.4).
7. **Log**: JSON stdout, không secret/PII trong log (S16/doc1 §3.4); log của chính pipeline CI/CD
   được giữ để chẩn đoán (S7/doc5).

## 11. Definition of Done + ánh xạ tiêu chí chấm (README §5)

| # | Kiểm chứng | Cách chứng minh |
|---|---|---|
| 1 | CI chặn merge đỏ | Mở PR phá test → Actions đỏ → không merge được |
| 2 | Test kế toán kép + idempotency chạy trong CI | Job `build-verify` chạy `*IT` Testcontainers: entries cân 0, snapshot = SUM, 100 request trùng idempotency key chỉ trừ 1 lần (README §5.2) |
| 3 | Deploy không downtime | Merge PR đổi 1 service → quan sát upstream swap blue→green, request liên tục không 5xx (README §4 Step 4.2) |
| 4 | Rollback tự động | Deploy image lỗi cố ý (health fail) → `deploy.sh` gọi `rollback.sh`, hệ thống về bản cũ |
| 5 | Alert tài chính | Inject lệch sổ trong DB test → `LedgerImbalance` bắn Telegram (README §4 Step 5) |
| 6 | Không secret lọt repo | Commit giả chứa `AWS_SECRET...` → gitleaks đỏ |
| 7 | Truy vết end-to-end | Gọi API có lỗi → tìm `X-Request-Id` trong Loki thấy đủ chuỗi qua các service |

## 12. Khác biệt có chủ đích so với giáo trình (ghi rõ để tránh nhầm)

| Giáo trình (rikkeidocs) | Quyết định của dự án | Lý do |
|---|---|---|
| Dockerfile single/multi-stage (S4/doc1, S8/doc2) | **Jib** — build image thẳng từ Maven, không Dockerfile | AGENTS.md §2 + devops-skill §4; Jib tự tách layer, chạy non-root, không cần Docker daemon trong CI → bỏ qua bài toán DinD/DooD (S8/doc1) |
| Self-hosted runner (S7/doc1, S8/doc1) | **GitHub-hosted `ubuntu-latest`** | Testcontainers + Jib chạy được ngay trên runner GitHub (có Docker sẵn); không phải tự vận hành runner. Nếu sau này cần cache nóng/quota miễn phí cạn → chuyển self-hosted theo đúng hướng dẫn S7 |
| EFK — Elasticsearch/Fluentd/Kibana (S17/doc1–4) | **Loki + Promtail + Grafana** | Elasticsearch một mình ~1 GB RAM, không vừa ngân sách 8 GB (devops-skill §6); giữ nguyên *tư tưởng* của S17: JSON stdout, traceId, tập trung hoá, bind loopback |
| Nginx + systemd file JAR (S1/ss1_doc1) | Compose + container hoá toàn bộ | Container cô lập runtime từng service (S2/doc1), healthcheck làm cổng deploy |
| Ubuntu 22.04 (README §4) | Ubuntu 22.04 **hoặc** 24.04 LTS | Session 10 dùng 24.04 LTS — cả hai đều đủ Docker/UFW/Certbot |

---

**Tóm tắt một câu:** mọi commit vào `main` phải qua `verify` (unit + integration), Jib đẩy image lên
GHCR theo SHA, VPS kéo về và swap nginx sau health-gate, lỗi thì tự rollback, và mọi thứ — từ lỗi
giao dịch đến chênh lệch sổ cái — đều la lên trên Telegram. Không một bước nào chỉ tồn tại trên máy
của một người.
