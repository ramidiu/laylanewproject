#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — Trust Payments fund/settlement guards (BACKEND ONLY).
#
# Ships EXACTLY one changed file on top of whatever backend source is currently
# on prod, then rebuilds the backend. Does NOT touch the frontend and does NOT
# rsync the whole working tree (which has unrelated uncommitted changes).
#
# The fix (TrustPaymentController.applyCardPaymentResult) — Option B, 2026-09-11:
#   GUARD 2  amount match is best-effort — Trust does NOT send baseamount for this
#            account, so a missing amount now FUNDS on errorcode=0 using our own
#            expected amount (flagged for reconciliation); only a present+mismatching
#            baseamount rejects. (Prior FAIL-CLOSED version stalled ALL card pay-ins.)
#   GUARD 3  settlement gate per Trust docs — settlestatus 0/1/10 = scheduled to
#            settle -> PROCESSING; 100 = settled -> FUNDS_RECEIVED; 2/3 -> not funded.
#   /callback now accepts POST + GET (fixes "Unhandled exception on GET /callback").
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-trustfix-prod.sh
# You'll be asked for the server password a few times (scp / ssh).
# =============================================================================
set -e

SRV=root@213.171.210.135
SRC=/mnt/c/Users/umesh/claude/laylanewproject
REL=layla-backend/src/main/java/com/remitz/modules/payin/trust/controller/TrustPaymentController.java
REMOTE=/opt/layla-live/$REL

echo "== 0/4  Local sanity — the Option B fix must be present in the file we're shipping =="
grep -q "amount match (best-effort)" "$SRC/$REL"                 || { echo "ERROR: best-effort amount guard not found in local file — aborting"; exit 1; }
grep -q "case \"0\":  case \"1\":  case \"10\":" "$SRC/$REL"      || { echo "ERROR: new settlement mapping (0/1/10 scheduled) not found — aborting"; exit 1; }
grep -q "method = {RequestMethod.POST, RequestMethod.GET}" "$SRC/$REL" || { echo "ERROR: POST+GET callback mapping not found — aborting"; exit 1; }
echo "   [OK] Option B fix present locally"

echo "== 1/4  Back up the current prod copy of the controller =="
ssh "$SRV" "cp -a '$REMOTE' '$REMOTE.bak.pretrustfix' && echo '   backup: $REMOTE.bak.pretrustfix'"

echo "== 2/4  Upload the single changed file =="
scp "$SRC/$REL" "$SRV:$REMOTE"

echo "== 3/4  Rebuild + restart backend on the server =="
ssh "$SRV" "cd /opt/layla-live \
  && docker compose -f docker-compose.production.yml build --no-cache backend \
  && docker compose -f docker-compose.production.yml up -d backend"

echo "== 4/4  Wait for boot, then health-check =="
sleep 45
ssh "$SRV" "cd /opt/layla-live \
  && docker compose -f docker-compose.production.yml logs --tail=120 backend | grep -iE 'Started .*Application|APPLICATION FAILED' \
  ; curl -s http://127.0.0.1:8086/actuator/health; echo"

echo "== DONE =="
echo "Rollback: ssh $SRV 'cp -a $REMOTE.bak.pretrustfix $REMOTE && cd /opt/layla-live && docker compose -f docker-compose.production.yml build --no-cache backend && docker compose -f docker-compose.production.yml up -d backend'"
