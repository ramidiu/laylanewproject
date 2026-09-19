#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — Create-Transaction beneficiary fix (FRONTEND ONLY).
#
# Why a full frontend bundle is still "targeted":
#   Prod's live frontend (deployed 2026-08-19) already contains every other
#   uncommitted working-tree change. The ONLY net-new change in the current
#   build is the payin-partner Create-Transaction page fix (removed the
#   "Use This Beneficiary" button + fixed Next validation for bank/mobile/cash).
#   So deploying the current build ships just that fix on top of what's live.
#
# This does NOT touch the backend (no rebuild, no restart) — frontend assets only.
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-createtxn-prod.sh
# You'll be asked for the server password (scp once, ssh once).
# =============================================================================
set -e
SRV=root@213.171.210.135
WEB=/var/www/layla/data/www/laylamoneytransfer.co.uk
LOCAL_CONTAINER=layla-frontend-new

echo "== 1/4  Extract the freshly-built frontend from the local container =="
rm -rf /tmp/layla-www /tmp/frontend.tgz
docker cp "$LOCAL_CONTAINER:/usr/share/nginx/html" /tmp/layla-www
# sanity: the fix must be in the bundle (button removed) — abort if the old button is still there
if grep -rlq "Use This Beneficiary" /tmp/layla-www/*.js 2>/dev/null; then
  echo "ERROR: built bundle still contains 'Use This Beneficiary' — rebuild the frontend first:"
  echo "       docker compose -f docker-compose.layla.yml up -d --build frontend"
  exit 1
fi
echo "   [OK] fix confirmed in bundle (old button absent)"
tar -czf /tmp/frontend.tgz -C /tmp/layla-www .
echo "   frontend.tgz: $(du -h /tmp/frontend.tgz | cut -f1)"

echo "== 2/4  Back up the current prod web root =="
ssh "$SRV" "rm -rf '${WEB}.bak.prev' && cp -a '$WEB' '${WEB}.bak.prev' && echo '   backup: ${WEB}.bak.prev'"

echo "== 3/4  Upload the bundle =="
scp /tmp/frontend.tgz "$SRV:/tmp/frontend.tgz"

echo "== 4/4  Deploy on the server + reload nginx =="
ssh "$SRV" "set -e \
  && tar -xzf /tmp/frontend.tgz -C '$WEB' \
  && chown -R \$(stat -c '%u:%g' '$WEB') '$WEB' \
  && nginx -t && systemctl reload nginx \
  && echo '   deployed. Use-This-Beneficiary now in bundle?:' \
  && (grep -rlq 'Use This Beneficiary' '$WEB'/*.js && echo 'STILL PRESENT (unexpected)' || echo 'removed (correct)')"

echo "== DONE — hard-refresh the Create Transaction page (or wait ~2 min for the self-updater). =="
echo "Rollback: ssh $SRV 'rm -rf $WEB && mv ${WEB}.bak.createtxn $WEB && systemctl reload nginx'"
