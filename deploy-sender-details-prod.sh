#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — Sender details on receipts + transaction detail view.
#
# Ships ONLY the receipt/sender changes:
#   BACKEND (2 files, targeted scp — does NOT rsync the whole source, so the
#            undeployed Trust "Option B" pay-in fix stays OUT of prod):
#     1) service/TransactionReceiptService.java  (sender name/email/mobile resolve)
#     2) resources/templates/receipt.html        (SENDER DETAILS + BENEFICIARY DETAILS)
#   FRONTEND (full production bundle built locally — ships the Sender Details card,
#            the Sender/Beneficiary headers in the Details view, and the en/ar i18n
#            keys, on top of what is already live).
#
# Run from WSL:  ! bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-sender-details-prod.sh
# You will be asked for the server password a few times (scp / rsync / ssh).
# =============================================================================
set -e

SRV=root@213.171.210.135
SRC=/mnt/c/Users/umesh/claude/laylanewproject
REMOTE_ROOT=/opt/layla-live
WEB=/var/www/layla/data/www/laylamoneytransfer.co.uk
LOCAL_CONTAINER=layla-frontend-new

RCPT=layla-backend/src/main/java/com/remitz/modules/transaction/service/TransactionReceiptService.java
TMPL=layla-backend/src/main/resources/templates/receipt.html

echo "== 0/6  Local sanity — sender-detail markers present =="
grep -q "resolveSenderPhone" "$SRC/$RCPT" || { echo "ERROR: resolveSenderPhone missing in service — aborting"; exit 1; }
grep -q "SENDER DETAILS"     "$SRC/$TMPL" || { echo "ERROR: SENDER DETAILS missing in template — aborting"; exit 1; }
echo "   [OK] backend markers present"

echo "== 1/6  Extract the freshly-built frontend from the local container =="
rm -rf /tmp/layla-www /tmp/frontend.tgz
docker cp "$LOCAL_CONTAINER:/usr/share/nginx/html" /tmp/layla-www
# sanity: new i18n key must be in the shipped assets, else the frontend wasn't rebuilt
grep -q "SENDER_DETAILS" /tmp/layla-www/assets/i18n/en.json \
  || { echo "ERROR: SENDER_DETAILS not in built bundle — rebuild frontend first:"; \
       echo "       docker compose -f docker-compose.layla.yml up -d --build frontend"; exit 1; }
echo "   [OK] frontend bundle contains the new sender-detail i18n keys"
tar -czf /tmp/frontend.tgz -C /tmp/layla-www .
echo "   frontend.tgz: $(du -h /tmp/frontend.tgz | cut -f1)"

echo "== 2/6  Back up prod (2 backend files + web root) =="
ssh "$SRV" "for f in '$RCPT' '$TMPL'; do p=$REMOTE_ROOT/\$f; [ -f \"\$p\" ] && cp -a \"\$p\" \"\$p.bak.senderdetails\" && echo \"   backup: \$p.bak.senderdetails\"; done; \
  rm -rf '${WEB}.bak.senderdetails' && cp -a '$WEB' '${WEB}.bak.senderdetails' && echo '   backup: ${WEB}.bak.senderdetails'"

echo "== 3/6  Upload the 2 backend files + frontend bundle =="
scp "$SRC/$RCPT" "$SRV:$REMOTE_ROOT/$RCPT"
scp "$SRC/$TMPL" "$SRV:$REMOTE_ROOT/$TMPL"
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
  && echo -n '   SENDER_DETAILS live in web root?: ' \
  && (grep -q 'SENDER_DETAILS' '$WEB'/assets/i18n/en.json && echo yes || echo 'NO (unexpected)')"

echo "== 6/6  Wait for backend boot, then health-check =="
sleep 45
ssh "$SRV" "cd $REMOTE_ROOT \
  && docker compose -f docker-compose.production.yml logs --tail=120 backend | grep -iE 'Started .*Application|APPLICATION FAILED' \
  ; echo -n 'health: '; curl -s http://127.0.0.1:8086/actuator/health; echo"

echo "== DONE =="
echo "Rollback backend:  ssh $SRV 'for f in $RCPT $TMPL; do p=$REMOTE_ROOT/\$f; [ -f \"\$p.bak.senderdetails\" ] && cp -a \"\$p.bak.senderdetails\" \"\$p\"; done && cd $REMOTE_ROOT && docker compose -f docker-compose.production.yml build --no-cache backend && docker compose -f docker-compose.production.yml up -d backend'"
echo "Rollback frontend: ssh $SRV 'rm -rf $WEB && mv ${WEB}.bak.senderdetails $WEB && systemctl reload nginx'"
