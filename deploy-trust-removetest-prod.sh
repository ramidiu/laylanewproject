#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — remove Trust test/sandbox routing (FRONTEND ONLY).
#
# Ships ONLY the rebuilt frontend bundle. Does NOT touch the backend and does
# NOT rsync the working tree.
#
# The change (send-money.page.ts):
#   - removed the ?trusttest URL flag + persisted localStorage 'trust_test'
#   - removed the Trust SANDBOX site reference test_laylalondo147950
#   - card payments now always use the LIVE site reference laylalondo147951
#   - clears any legacy trust_test flag on page load
#
# Run from WSL:  ! bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-trust-removetest-prod.sh
# You will be asked for the server password a few times (scp / ssh).
# =============================================================================
set -e

SRV=root@213.171.210.135
WEB=/var/www/layla/data/www/laylamoneytransfer.co.uk
LOCAL_CONTAINER=layla-frontend-new

echo "== 1/5  Extract the freshly-built frontend from the local container =="
rm -rf /tmp/layla-www /tmp/frontend.tgz
docker cp "$LOCAL_CONTAINER:/usr/share/nginx/html" /tmp/layla-www

echo "== 2/5  Local sanity — sandbox ref gone, live ref present =="
if grep -rq 'test_laylalondo147950' /tmp/layla-www; then
  echo "ERROR: sandbox site reference STILL in the built bundle — rebuild the frontend first:"
  echo "       docker compose -f docker-compose.layla.yml up -d --build frontend"
  exit 1
fi
grep -rq 'laylalondo147951' /tmp/layla-www \
  || { echo "ERROR: live site reference NOT in the built bundle — aborting"; exit 1; }
echo "   [OK] bundle uses live site reference only"
tar -czf /tmp/frontend.tgz -C /tmp/layla-www .
echo "   frontend.tgz: $(du -h /tmp/frontend.tgz | cut -f1)"

echo "== 3/5  Back up the current prod web root =="
ssh "$SRV" "rm -rf '${WEB}.bak.removetest' && cp -a '$WEB' '${WEB}.bak.removetest' && echo '   backup: ${WEB}.bak.removetest'"

echo "== 4/5  Upload + deploy frontend bundle, reload nginx =="
scp /tmp/frontend.tgz "$SRV:/tmp/frontend.tgz"
ssh "$SRV" "set -e \
  && tar -xzf /tmp/frontend.tgz -C '$WEB' \
  && chown -R \$(stat -c '%u:%g' '$WEB') '$WEB' \
  && nginx -t && systemctl reload nginx"

echo "== 5/5  Verify live web root no longer carries the sandbox reference =="
ssh "$SRV" "if grep -rq 'test_laylalondo147950' '$WEB'; then echo '   NO — sandbox ref still present (unexpected)'; else echo '   yes — sandbox ref absent, live only'; fi"

echo "== DONE =="
echo "Rollback: ssh $SRV 'rm -rf $WEB && mv ${WEB}.bak.removetest $WEB && systemctl reload nginx'"
