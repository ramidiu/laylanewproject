# Layla Money Transfer — Deployment to test.laylamoneytransfer.co.uk

**Target server:** AlmaLinux 8 @ `68.169.55.246`
**Frontend path:** `/var/www/layla/data/www/test.laylamoneytransfer.co.uk/`
**Backend service:** systemd unit running on port 8080 (proxied via nginx)
**Database:** local MySQL 8 (already installed)
**Image storage:** `/var/www/layla/data/kyc-uploads/legacy/`

All commands assume you SSH as `root` (or a user with sudo). Adjust paths if you use a different user.

---

## 0. Build artifacts (already produced locally)

Located in `/mnt/c/Users/kreat/claude/laylaproject/deploy/`:

| File | Size | Purpose |
|------|------|---------|
| `layla-backend.jar` | ~90 MB | Spring Boot uber-jar (runs on Java 17) |
| `frontend-www.tar.gz` | ~5 MB | Angular production build (extracts to `dist/` or `www/`) |
| `remitz.sql.gz` | ~16 MB | MySQL dump of the local `remitz` database |
| `images/` (folder) | ~700 MB, 826 files | Legacy KYC images for migrated users |

---

## 1. Upload everything from your machine (run in WSL)

```bash
DEPLOY=/mnt/c/Users/kreat/claude/laylaproject/deploy
SRV=root@68.169.55.246

# Backend JAR
scp $DEPLOY/layla-backend.jar $SRV:/tmp/

# Frontend bundle
scp $DEPLOY/frontend-www.tar.gz $SRV:/tmp/

# Database dump
scp $DEPLOY/remitz.sql.gz $SRV:/tmp/

# Images (rsync = resumable + delta) — ~700 MB
rsync -avzP /mnt/c/Users/kreat/claude/layla-static/images/ \
  $SRV:/var/www/layla/data/kyc-uploads/legacy/
```

You'll be prompted for the root password on each step.

---

## 2. On the server — one-time setup

SSH in (`ssh root@68.169.55.246`) and run:

### 2a. Install Java 17

```bash
dnf install -y java-17-openjdk java-17-openjdk-devel
java -version    # expect: openjdk version "17.x.x"
```

### 2b. Create application directories

```bash
mkdir -p /opt/layla
mkdir -p /var/www/layla/data/kyc-uploads/legacy
mkdir -p /var/www/layla/data/www/test.laylamoneytransfer.co.uk

# Allow the app to read/write the upload dir
chown -R root:root /var/www/layla/data
```

### 2c. Restore the database

```bash
# Create the schema (root password is whatever you set during mysql install)
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS remitz CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Create an app user (so backend doesn't run as root)
mysql -uroot -p <<'SQL'
CREATE USER IF NOT EXISTS 'layla'@'localhost' IDENTIFIED BY 'CHANGE_ME_STRONG_PASSWORD';
GRANT ALL PRIVILEGES ON remitz.* TO 'layla'@'localhost';
FLUSH PRIVILEGES;
SQL

# Restore
zcat /tmp/remitz.sql.gz | mysql -uroot -p remitz

# Sanity check
mysql -uroot -p -e "SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM kyc_documents;" remitz
```

### 2d. Place the backend JAR

```bash
mv /tmp/layla-backend.jar /opt/layla/layla-backend.jar
chmod 644 /opt/layla/layla-backend.jar
```

### 2e. Place the frontend bundle

```bash
tar -xzf /tmp/frontend-www.tar.gz -C /var/www/layla/data/www/test.laylamoneytransfer.co.uk/ --strip-components=1
chown -R nginx:nginx /var/www/layla/data/www/test.laylamoneytransfer.co.uk
```

(If you use Apache instead of nginx, use `apache:apache`.)

### 2f. Verify the legacy images uploaded

```bash
ls /var/www/layla/data/kyc-uploads/legacy/userIdentity | wc -l   # expect 437
ls /var/www/layla/data/kyc-uploads/legacy/userAddress | wc -l    # expect 389
```

---

## 3. systemd unit for the backend

Create `/etc/systemd/system/layla-backend.service`:

```ini
[Unit]
Description=Layla Money Transfer backend
After=network.target mysqld.service

[Service]
Type=simple
WorkingDirectory=/var/www/layla/data
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar /opt/layla/layla-backend.jar
Restart=on-failure
RestartSec=5

# DB connection
Environment=MYSQL_HOST=localhost
Environment=MYSQL_PORT=3306
Environment=MYSQL_USER=layla
Environment=MYSQL_PASSWORD=CHANGE_ME_STRONG_PASSWORD
Environment=SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/remitz?useSSL=false&serverTimezone=UTC

# Redis (install via dnf: dnf install -y redis && systemctl enable --now redis)
Environment=REDIS_HOST=localhost
Environment=REDIS_PORT=6379

# JWT — generate a strong secret: openssl rand -hex 64
Environment=JWT_SECRET=REPLACE_WITH_OUTPUT_OF_openssl_rand_hex_64

# Storage
Environment=KYC_UPLOAD_DIR=/var/www/layla/data/kyc-uploads

# Brand
Environment=BRAND_NAME=Layla Money Transfer
Environment=BRAND_SUPPORT_EMAIL=support@laylamoneytransfer.com
Environment=FRONTEND_URL=https://test.laylamoneytransfer.co.uk

# Email (Brevo)
Environment=BREVO_API_KEY=CHANGE_ME_BREVO_KEY
Environment=BREVO_FROM_NAME=Layla Money Transfer
Environment=BREVO_FROM_EMAIL=noreply@laylamoneytransfer.com

# RemitOne (if used)
Environment=REMIT_ONE_ENABLED=true
Environment=REMIT_ONE_USERNAME=Abdulazim.mohammed2817
Environment=REMIT_ONE_PASSWORD=Abolayla2007
Environment=REMIT_ONE_PIN=12345
Environment=REMIT_ONE_AGENT_NAME=Universal Securities

# Schema management — set to `validate` once stable
Environment=SPRING_JPA_HIBERNATE_DDL_AUTO=update

[Install]
WantedBy=multi-user.target
```

Then:

```bash
# Install Redis if not present
dnf install -y redis
systemctl enable --now redis

# Enable and start the backend
systemctl daemon-reload
systemctl enable layla-backend
systemctl start layla-backend

# Watch the log
journalctl -u layla-backend -f
# (Wait ~30s for "Started RemitzApplication in N seconds")

# Health check
curl http://localhost:8080/actuator/health
```

---

## 4. nginx reverse proxy

Create `/etc/nginx/conf.d/test.laylamoneytransfer.co.uk.conf`:

```nginx
server {
    listen 80;
    server_name test.laylamoneytransfer.co.uk;

    # Frontend
    root /var/www/layla/data/www/test.laylamoneytransfer.co.uk;
    index index.html;

    # Angular SPA — fallback to index.html for client-side routes
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Backend API
    location /api/ {
        proxy_pass         http://127.0.0.1:8080/api/;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        client_max_body_size 25M;   # large enough for KYC document uploads
    }

    # Spring actuator (lock down in prod)
    location /actuator/ {
        proxy_pass http://127.0.0.1:8080/actuator/;
    }
}
```

Test + reload:

```bash
nginx -t
systemctl reload nginx
# If you haven't installed nginx yet:  dnf install -y nginx && systemctl enable --now nginx
```

---

## 5. Frontend API URL

The Angular build was produced with `environment.apiUrl` pointing at the dev backend. If after deployment the frontend can't reach the API:

- Open browser DevTools → Network → see what URL it's calling.
- If it's hitting `http://localhost:8095/api/...` instead of `https://test.laylamoneytransfer.co.uk/api`, we need to rebuild Angular with the production environment file pointing at `https://test.laylamoneytransfer.co.uk/api`. Tell me and I'll rebuild.

---

## 6. (Optional) HTTPS

After the site loads on `http://test.laylamoneytransfer.co.uk`, add Let's Encrypt:

```bash
dnf install -y certbot python3-certbot-nginx
certbot --nginx -d test.laylamoneytransfer.co.uk --redirect -m you@laylamoneytransfer.com --agree-tos -n
```

---

## 7. Smoke test

From the server:

```bash
curl -I http://localhost                                # nginx 200
curl http://localhost:8080/actuator/health              # {"status":"UP"}
curl http://localhost/api/users  -H "Accept: application/json"   # 403 (auth required = good)
```

From your laptop:

```bash
curl -I http://test.laylamoneytransfer.co.uk            # 200 from nginx serving Angular
```

---

## 8. If something breaks

| Symptom | Where to look |
|--------|----------------|
| Backend won't start | `journalctl -u layla-backend -n 200` |
| DB connection refused | check `MYSQL_PASSWORD` env in unit file vs the GRANT |
| 404 on Angular route refresh | nginx `try_files` line missing |
| API 502 from nginx | backend down or wrong port |
| Image preview 404s | check `KYC_UPLOAD_DIR` env matches where images live |
| Front shows "Failed to load" preview | DB path doesn't match disk path; query `kyc_documents.file_path` |

---

## 9. Rolling updates

Future deploys (no schema change):

```bash
# locally
mvn clean package -DskipTests -f layla-backend/pom.xml
scp layla-backend/target/layla-monolith-1.0.0-SNAPSHOT.jar root@68.169.55.246:/opt/layla/layla-backend.jar

# server
systemctl restart layla-backend
journalctl -u layla-backend -f
```

For frontend:

```bash
# locally
cd laylamoneytransfernew && npm run build
tar -czf /tmp/frontend.tgz -C www .
scp /tmp/frontend.tgz root@68.169.55.246:/tmp/

# server
rm -rf /var/www/layla/data/www/test.laylamoneytransfer.co.uk/*
tar -xzf /tmp/frontend.tgz -C /var/www/layla/data/www/test.laylamoneytransfer.co.uk/
chown -R nginx:nginx /var/www/layla/data/www/test.laylamoneytransfer.co.uk
```
