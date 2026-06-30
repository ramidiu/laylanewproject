# Layla — Build & Upload Commands (canonical)

> Server: `root@68.169.55.246`  ·  App dir: `/opt/layla-live`  ·  Web root: `/var/www/layla/data/www/laylamoneytransfer.co.uk`
> Use **this** sequence every deploy. Java goes up as **source** (built on the server, no jar); Angular is built locally and uploaded as a tar.
>
> **Claude does the local build (step 1) and regenerates `/tmp/frontend.tgz` (step 3) automatically — the user only runs the upload (steps 2–3 scp/rsync) and the server steps (step 4).**

---

## 1. Build locally (Java + Angular) via Docker

```bash
cd /mnt/c/Users/umesh/claude/laylanewproject
docker compose -f docker-compose.layla.yml up -d --build
```

## 2. Upload — Java backend (only changed source, no jar)

```bash
rsync -avz --exclude 'target/' --exclude '.git/' /mnt/c/Users/umesh/claude/laylanewproject/layla-backend/ root@68.169.55.246:/opt/layla-live/layla-backend/
```

## 3. Upload — Angular frontend (build tar from the container, then scp)

```bash
docker cp layla-frontend-new:/usr/share/nginx/html /tmp/layla-www
tar -czf /tmp/frontend.tgz -C /tmp/layla-www .
scp /tmp/frontend.tgz root@68.169.55.246:/tmp/
```

## 4. On the server — SSH in

```bash
ssh root@68.169.55.246
```

### 4a. Backend — rebuild from the uploaded source

```bash
cd /opt/layla-live
docker compose -f docker-compose.production.yml up -d --build backend
docker compose -f docker-compose.production.yml logs -f backend    # wait for "Started ... in N seconds"
curl -s http://127.0.0.1:8086/actuator/health; echo                # {"status":"UP"}
```

### 4b. Frontend — extract, fix owner, reload nginx

```bash
WEB=/var/www/layla/data/www/laylamoneytransfer.co.uk
tar -xzf /tmp/frontend.tgz -C "$WEB"
chown -R $(stat -c '%u:%g' "$WEB") "$WEB"
nginx -t && systemctl reload nginx
```

---

## Notes

- **`chown` uses numeric `%u:%g`** (not `%U:%G`) because the ISPmanager web dir owner shows as `UNKNOWN:mgrsecure`; the name form errors with `invalid user`.
- **No hard refresh needed:** `index.html` is served `no-cache` (see `deploy-live/laylamoneytransfer.co.uk.conf`), and the app has a **self-updater** that reloads the open page within ~2 min of a new build. On ISPmanager, make sure the `no-cache` rule for `index.html` is in the panel vhost so it isn't overwritten.
- **DB migrations** (Flyway `V5xx`) run automatically when the backend starts — no manual SQL.
- First time only: `docker volume create laylaproject_layla_mysql_data && docker volume create laylaproject_layla_kyc_uploads`, then import DB + KYC images (see `deploy-live/DEPLOY-LIVE.md` §3–§4).
