#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — Trust Payments SETTLEMENT SCHEDULER.
#
# Adds a scheduled job that re-checks PENDING card (Trust) transactions and
# advances them PENDING -> PROCESSING when Trust reports settlestatus=100
# (SETTLED). Trust cards only; read-only against Trust; no other status changed.
# Ships FOUR files (3 modified verified to differ from prod ONLY by these
# additions; 1 new). Does NOT touch the live payment/callback flow or frontend.
#
#   1) NEW scheduler/TrustSettlementScheduler.java
#   2) transaction/repository/TransactionRepository.java   (+finder)
#   3) payin/trust/service/TrustStatusService.java          (+isConfigured)
#   4) resources/application.yml                            (+settlement-poll)
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-trust-scheduler-prod.sh
# =============================================================================
set -e

SRV=root@213.171.210.135
SRC=/mnt/c/Users/umesh/claude/laylanewproject
REMOTE_ROOT=/opt/layla-live

SCHED=layla-backend/src/main/java/com/remitz/modules/payin/trust/scheduler/TrustSettlementScheduler.java
REPO=layla-backend/src/main/java/com/remitz/modules/transaction/repository/TransactionRepository.java
SVC=layla-backend/src/main/java/com/remitz/modules/payin/trust/service/TrustStatusService.java
YML=layla-backend/src/main/resources/application.yml

echo "== 0/4  Local sanity — required markers present =="
grep -q "pollPendingCardSettlements"        "$SRC/$SCHED" || { echo "ERROR: scheduler method missing — aborting"; exit 1; }
grep -q "findByStatusAndPaymentMethodType"   "$SRC/$REPO"  || { echo "ERROR: repo finder missing — aborting"; exit 1; }
grep -q "public boolean isConfigured"        "$SRC/$SVC"   || { echo "ERROR: isConfigured() missing — aborting"; exit 1; }
grep -q "settlement-poll"                    "$SRC/$YML"   || { echo "ERROR: settlement-poll config missing — aborting"; exit 1; }
echo "   [OK] all markers present locally"

echo "== 1/4  Back up the 3 modified prod files + create scheduler dir =="
ssh "$SRV" "for f in '$REPO' '$SVC' '$YML'; do p=$REMOTE_ROOT/\$f; [ -f \"\$p\" ] && cp -a \"\$p\" \"\$p.bak.trustsched\" && echo \"   backup: \$p.bak.trustsched\"; done; \
  mkdir -p $REMOTE_ROOT/layla-backend/src/main/java/com/remitz/modules/payin/trust/scheduler"

echo "== 2/4  Upload the four files =="
scp "$SRC/$SCHED" "$SRV:$REMOTE_ROOT/$SCHED"
scp "$SRC/$REPO"  "$SRV:$REMOTE_ROOT/$REPO"
scp "$SRC/$SVC"   "$SRV:$REMOTE_ROOT/$SVC"
scp "$SRC/$YML"   "$SRV:$REMOTE_ROOT/$YML"

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
echo "Rollback: ssh $SRV 'rm -f $REMOTE_ROOT/$SCHED; for f in $REPO $SVC $YML; do p=$REMOTE_ROOT/\$f; [ -f \"\$p.bak.trustsched\" ] && cp -a \"\$p.bak.trustsched\" \"\$p\"; done && cd $REMOTE_ROOT && docker compose -f docker-compose.production.yml build --no-cache backend && docker compose -f docker-compose.production.yml up -d backend'"
