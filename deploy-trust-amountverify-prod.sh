#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — Trust settlement AMOUNT VERIFICATION.
#
# Adds fail-closed amount verification to the settlement checker + scheduler:
# a settled Trust payment is only advanced when the TRANSACTIONQUERY baseamount
# EQUALS the transaction's total_debit_amount. Ships THREE modified files
# (verified to differ from prod ONLY by these additions). Does NOT touch the
# live payment/callback flow or frontend.
#
#   1) service/TrustStatusService.java      (SettlementView.baseAmount + baseAmountToMajor)
#   2) scheduler/TrustSettlementScheduler.java (amount check before PENDING->PROCESSING)
#   3) controller/TrustStatusController.java   (amount check before PROCESSING->FUNDS_RECEIVED)
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-trust-amountverify-prod.sh
# =============================================================================
set -e

SRV=root@213.171.210.135
SRC=/mnt/c/Users/umesh/claude/laylanewproject
REMOTE_ROOT=/opt/layla-live

SVC=layla-backend/src/main/java/com/remitz/modules/payin/trust/service/TrustStatusService.java
SCHED=layla-backend/src/main/java/com/remitz/modules/payin/trust/scheduler/TrustSettlementScheduler.java
CTL=layla-backend/src/main/java/com/remitz/modules/payin/trust/controller/TrustStatusController.java

echo "== 0/4  Local sanity — amount-verification markers present =="
grep -q "baseAmountToMajor" "$SRC/$SVC"   || { echo "ERROR: baseAmountToMajor missing in service — aborting"; exit 1; }
grep -q "amount mismatch"   "$SRC/$SCHED" || { echo "ERROR: amount check missing in scheduler — aborting"; exit 1; }
grep -q "amountVerified"    "$SRC/$CTL"   || { echo "ERROR: amountVerified missing in controller — aborting"; exit 1; }
echo "   [OK] all markers present locally"

echo "== 1/4  Back up the 3 prod files =="
ssh "$SRV" "for f in '$SVC' '$SCHED' '$CTL'; do p=$REMOTE_ROOT/\$f; [ -f \"\$p\" ] && cp -a \"\$p\" \"\$p.bak.amountverify\" && echo \"   backup: \$p.bak.amountverify\"; done"

echo "== 2/4  Upload the three files =="
scp "$SRC/$SVC"   "$SRV:$REMOTE_ROOT/$SVC"
scp "$SRC/$SCHED" "$SRV:$REMOTE_ROOT/$SCHED"
scp "$SRC/$CTL"   "$SRV:$REMOTE_ROOT/$CTL"

echo "== 3/4  Rebuild + restart backend on the server =="
ssh "$SRV" "cd $REMOTE_ROOT \
  && docker compose -f docker-compose.production.yml build --no-cache backend \
  && docker compose -f docker-compose.production.yml up -d backend"

echo "== 4/4  Wait for boot, then health-check + scheduler log =="
sleep 45
ssh "$SRV" "cd $REMOTE_ROOT \
  && docker compose -f docker-compose.production.yml logs --tail=200 backend | grep -iE 'Started .*Application|APPLICATION FAILED|Trust settlement poll' \
  ; echo -n 'health: '; curl -s http://127.0.0.1:8086/actuator/health; echo"

echo "== DONE =="
echo "Rollback: ssh $SRV 'for f in $SVC $SCHED $CTL; do p=$REMOTE_ROOT/\$f; [ -f \"\$p.bak.amountverify\" ] && cp -a \"\$p.bak.amountverify\" \"\$p\"; done && cd $REMOTE_ROOT && docker compose -f docker-compose.production.yml build --no-cache backend && docker compose -f docker-compose.production.yml up -d backend'"
