#!/usr/bin/env bash
# One-shot prod deploy for Layla (backend source + frontend build).
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-prod.sh
# You'll be asked for the server password a few times (rsync / scp / ssh).
set -e

SRV=root@213.171.210.135
SRC=/mnt/c/Users/umesh/claude/laylanewproject
WEB=/var/www/layla/data/www/laylamoneytransfer.co.uk

echo "== 1/5  Build frontend artifact from the local container =="
rm -rf /tmp/layla-www
docker cp layla-frontend-new:/usr/share/nginx/html /tmp/layla-www
tar -czf /tmp/frontend.tgz -C /tmp/layla-www .
echo "   frontend.tgz: $(du -h /tmp/frontend.tgz | cut -f1)"

echo "== 2/5  Upload backend SOURCE (no jar) =="
rsync -avz --exclude 'target/' --exclude '.git/' "$SRC/layla-backend/" "$SRV:/opt/layla-live/layla-backend/"

echo "== 3/5  Upload frontend build =="
scp /tmp/frontend.tgz "$SRV:/tmp/"

echo "== 4/5  Rebuild backend (no cache) + deploy frontend on the server =="
ssh "$SRV" "cd /opt/layla-live \
  && docker compose -f docker-compose.production.yml build --no-cache backend \
  && docker compose -f docker-compose.production.yml up -d backend \
  && tar -xzf /tmp/frontend.tgz -C '$WEB' \
  && chown -R \$(stat -c '%u:%g' '$WEB') '$WEB' \
  && nginx -t && systemctl reload nginx"

echo "== 5/5  Waiting 45s for backend to boot, then checking =="
sleep 45
ssh "$SRV" "cd /opt/layla-live && docker compose -f docker-compose.production.yml logs --tail=80 backend | grep -iE 'Started RemitzApplication|APPLICATION FAILED'" \
  || echo "   (not logged 'Started' yet — check manually: ssh $SRV then docker compose -f /opt/layla-live/docker-compose.production.yml logs backend)"

echo "== DONE =="
