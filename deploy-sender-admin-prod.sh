#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — Sender details in the SUPERADMIN transactions "Details" popup.
#
# Ships ONLY:
#   BACKEND (1 file, targeted scp — does NOT rsync the whole source, so the
#            undeployed Trust "Option B" pay-in fix stays OUT of prod):
#     1) controller/AdminTransactionController.java
#        (adds senderPhone + robust senderEmail to the admin transactions list)
#   FRONTEND (full production bundle built locally — the superadmin "Details"
#            popup now shows a Sender Details section above Beneficiary Details).
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-sender-admin-prod.sh
# =============================================================================
set -e

SRV=root@213.171.210.135
SRC=/mnt/c/Users/umesh/claude/laylanewproject
REMOTE_ROOT=/opt/layla-live
WEB=/var/www/layla/data/www/laylamoneytransfer.co.uk
LOCAL_CONTAINER=layla-frontend-new

CTL=layla-backend/src/main/java/com/remitz/modules/transaction/controller/AdminTransactionController.java

echo "== 0/6  Local sanity — sender markers present =="
grep -q 'map.put("senderPhone"' "$SRC/$CTL" || { echo "ERROR: senderPhone missing in controller — aborting"; exit 1; }
echo "   [OK] backend marker present"

echo "== 1/6  Extract the freshly-built frontend from the local container =="
rm -rf /tmp/layla-www /tmp/frontend.tgz
docker cp "$LOCAL_CONTAINER:/usr/share/nginx/html" /tmp/layla-www
# sanity: the new Sender Details popup section must be in the built superadmin chunk
grep -rslq "Sender Details" /tmp/layla-www/*.js \
  || { echo "ERROR: 'Sender Details' not found in built bundle — rebuild frontend first:"; \
       echo "       docker compose -f docker-compose.layla.yml up -d --build frontend"; exit 1; }
echo "   [OK] frontend bundle contains the Sender Details popup section"
tar -czf /tmp/frontend.tgz -C /tmp/layla-www .
echo "   frontend.tgz: $(du -h /tmp/frontend.tgz | cut -f1)"

echo "== 2/6  Back up prod (1 backend file + web root) =="
ssh "$SRV" "p=$REMOTE_ROOT/$CTL; [ -f \"\$p\" ] && cp -a \"\$p\" \"\$p.bak.senderadmin\" && echo \"   backup: \$p.bak.senderadmin\"; \
  rm -rf '${WEB}.bak.senderadmin' && cp -a '$WEB' '${WEB}.bak.senderadmin' && echo '   backup: ${WEB}.bak.senderadmin'"

echo "== 3/6  Upload the backend file + frontend bundle =="
scp "$SRC/$CTL" "$SRV:$REMOTE_ROOT/$CTL"
scp /tmp/frontend.tgz "$SRV:/tmp/frontend.tgz"

echo "== 4/6  Rebuild backend (no cache) + restart =="
ssh "$SRV" "cd $REMOTE_ROOT \
  && docker compose -f docker-compose.production.yml build --no-cache backend \
  && docker compose -f docker-compose.production.yml up -d backend"

echo "== 5/6  Deploy frontend bundle + reload nginx =="
ssh "$SRV" "set -e \
  && tar -xzf /tmp/frontend.tgz -C '$WEB' \
  && chown -R \$(stat -c '%u:%g' '$WEB') '$WEB' \
  && nginx -t && systemctl reload nginx \
  && echo '   frontend deployed'"

echo "== 6/6  Wait for backend boot, then health-check =="
sleep 45
ssh "$SRV" "cd $REMOTE_ROOT \
  && docker compose -f docker-compose.production.yml logs --tail=120 backend | grep -iE 'Started .*Application|APPLICATION FAILED' \
  ; echo -n 'health: '; curl -s http://127.0.0.1:8086/actuator/health; echo"

echo "== DONE =="
echo "Rollback backend:  ssh $SRV 'p=$REMOTE_ROOT/$CTL; [ -f \"\$p.bak.senderadmin\" ] && cp -a \"\$p.bak.senderadmin\" \"\$p\"; cd $REMOTE_ROOT && docker compose -f docker-compose.production.yml build --no-cache backend && docker compose -f docker-compose.production.yml up -d backend'"
echo "Rollback frontend: ssh $SRV 'rm -rf $WEB && mv ${WEB}.bak.senderadmin $WEB && systemctl reload nginx'"
