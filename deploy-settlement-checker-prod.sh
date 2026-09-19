#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — Trust Payments on-demand SETTLEMENT STATUS CHECKER.
#
# Ships exactly THREE changed files on top of the current prod backend source,
# then rebuilds the backend. Read-only feature: adds POST /api/trust-payment/
# settlement-status (TRANSACTIONQUERY by transactionreference, AUTH-record
# preferred, settlement mapping, on-demand PROCESSING->FUNDS_RECEIVED reconcile).
# Does NOT touch the live payment/callback flow or the frontend.
#
#   1) service/TrustStatusService.java        (adds queryByTransactionReference)
#   2) controller/TrustStatusController.java   (adds /settlement-status endpoint)
#   3) resources/application.yml               (TRUST_PAYMENTS_* env + timeout-ms)
#
# The whole-file application.yml ship is safe: verified the ONLY diff vs prod is
# the trust.ws block. Trust Webservices creds remain unset until added to .env;
# until then the endpoint returns a safe "credentials not configured" response.
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-settlement-checker-prod.sh
# =============================================================================
set -e

SRV=root@213.171.210.135
SRC=/mnt/c/Users/umesh/claude/laylanewproject
BE=layla-backend/src/main/java/com/remitz/modules/payin/trust
SVC=$BE/service/TrustStatusService.java
CTL=$BE/controller/TrustStatusController.java
YML=layla-backend/src/main/resources/application.yml
REMOTE_ROOT=/opt/layla-live

echo "== 0/4  Local sanity — the new code must be present in the files we ship =="
grep -q "queryByTransactionReference" "$SRC/$SVC" || { echo "ERROR: queryByTransactionReference missing in service — aborting"; exit 1; }
grep -q "record SettlementView"       "$SRC/$SVC" || { echo "ERROR: SettlementView missing in service — aborting"; exit 1; }
grep -q "/settlement-status"          "$SRC/$CTL" || { echo "ERROR: settlement-status endpoint missing in controller — aborting"; exit 1; }
grep -q "TRUST_PAYMENTS_USERNAME"     "$SRC/$YML" || { echo "ERROR: TRUST_PAYMENTS_* env wiring missing in application.yml — aborting"; exit 1; }
echo "   [OK] all markers present locally"

echo "== 1/4  Back up the current prod copies =="
ssh "$SRV" "for f in '$BE/service/TrustStatusService.java' '$BE/controller/TrustStatusController.java' '$YML'; do \
  p=$REMOTE_ROOT/\$f; [ -f \"\$p\" ] && cp -a \"\$p\" \"\$p.bak.presettlement\" && echo \"   backup: \$p.bak.presettlement\"; done"

echo "== 2/4  Upload the three changed files =="
scp "$SRC/$SVC" "$SRV:$REMOTE_ROOT/$BE/service/TrustStatusService.java"
scp "$SRC/$CTL" "$SRV:$REMOTE_ROOT/$BE/controller/TrustStatusController.java"
scp "$SRC/$YML" "$SRV:$REMOTE_ROOT/$YML"

echo "== 3/4  Rebuild + restart backend on the server =="
ssh "$SRV" "cd $REMOTE_ROOT \
  && docker compose -f docker-compose.production.yml build --no-cache backend \
  && docker compose -f docker-compose.production.yml up -d backend"

echo "== 4/4  Wait for boot, then health-check + secured-endpoint probe =="
sleep 45
ssh "$SRV" "cd $REMOTE_ROOT \
  && docker compose -f docker-compose.production.yml logs --tail=120 backend | grep -iE 'Started .*Application|APPLICATION FAILED' \
  ; echo -n 'health: '; curl -s http://127.0.0.1:8086/actuator/health; echo \
  ; echo -n 'settlement-status unauth probe (expect 401/403): '; \
    curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8086/api/trust-payment/settlement-status -H 'Content-Type: application/json' -d '{}'"

echo "== DONE =="
echo "Rollback: ssh $SRV 'for f in $BE/service/TrustStatusService.java $BE/controller/TrustStatusController.java $YML; do p=$REMOTE_ROOT/\$f; [ -f \"\$p.bak.presettlement\" ] && cp -a \"\$p.bak.presettlement\" \"\$p\"; done && cd $REMOTE_ROOT && docker compose -f docker-compose.production.yml build --no-cache backend && docker compose -f docker-compose.production.yml up -d backend'"
