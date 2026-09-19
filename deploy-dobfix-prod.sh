#!/usr/bin/env bash
# =============================================================================
# TARGETED prod deploy — DoB placeholder prevention ONLY.
#
# Ships exactly three changes (nothing else from your working tree):
#   1. migration V562  — make payin_customers.dob nullable + null out 1990-01-01
#   2. PayinCustomerEntity.java      — @Column(nullable=false) -> @Column on dob
#   3. PayinTransactionServiceImpl.java line ~519 — stop writing the 1990-01-01
#      placeholder; store the real DoB (or null)
#
# It edits the two Java files IN PLACE on the server (with .bak.dobfix backups),
# so your other uncommitted changes are NOT shipped. Then it rebuilds + restarts
# only the backend and verifies V562 applied and the app booted.
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/deploy-dobfix-prod.sh
# You'll be asked for the server password a few times.
# =============================================================================
set -e
SRV=root@213.171.210.135
APP=/opt/layla-live
MIG_LOCAL=/mnt/c/Users/umesh/claude/laylanewproject/layla-backend/src/main/resources/db/migration/V562__payin_customer_dob_nullable.sql
MIG_REMOTE=$APP/layla-backend/src/main/resources/db/migration/V562__payin_customer_dob_nullable.sql

echo "== 1/3  Upload migration V562 =="
scp "$MIG_LOCAL" "$SRV:$MIG_REMOTE"

echo "== 2/3  Apply the two in-place source edits (with backups) =="
ssh "$SRV" 'bash -s' <<"EOF"
set -e
BASE=/opt/layla-live/layla-backend/src/main/java/com/remitz/modules/payin
ENT=$BASE/customer/entity/PayinCustomerEntity.java
SVC=$BASE/transaction/service/PayinTransactionServiceImpl.java
cp "$ENT" "$ENT.bak.dobfix"; cp "$SVC" "$SVC.bak.dobfix"

# Entity: make the dob column nullable (only the block right before 'private LocalDate dob;')
perl -0pi -e 's/\@Column\(nullable = false\)\n(\s*private LocalDate dob;)/\@Column\n$1/' "$ENT"

# Service: drop the 1990-01-01 placeholder
perl -pi -e 's/\.dob\(user\.getDateOfBirth\(\) != null \? user\.getDateOfBirth\(\) : java\.time\.LocalDate\.of\(1990, 1, 1\)\)/.dob(user.getDateOfBirth())/' "$SVC"

echo "  entity dob block:"; grep -n -B1 "private LocalDate dob;" "$ENT" | sed 's/^/    /'
echo "  service dob line:"; grep -n "\.dob(user.getDateOfBirth" "$SVC" | sed 's/^/    /'
if grep -q "1990, 1, 1" "$SVC"; then echo "  ERROR: placeholder still present — aborting"; exit 1; fi
echo "  edits OK"
EOF

echo "== 3/3  Rebuild + restart backend, then verify =="
ssh "$SRV" "cd $APP \
  && docker compose -f docker-compose.production.yml build --no-cache backend \
  && docker compose -f docker-compose.production.yml up -d backend"

echo "   waiting 50s for backend to boot..."
sleep 50
ssh "$SRV" "cd $APP \
  && echo '--- boot ---' && docker compose -f docker-compose.production.yml logs --tail=120 backend | grep -iE 'Started RemitzApplication|APPLICATION FAILED|Migrating|V562' \
  && echo '--- flyway V562 ---' && set -a; . ./.env; set +a; docker exec layla-live-mysql mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -B remitz -e \"SELECT version,success FROM flyway_schema_history WHERE version='562';\" \
  && echo '--- payin dob nullable? (expect YES) ---' && docker exec layla-live-mysql mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -B remitz -e \"SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema='remitz' AND table_name='payin_customers' AND column_name='dob';\""

echo "== DONE =="
echo "Rollback (if ever needed): restore *.bak.dobfix on the server and rebuild;"
echo "V562 is idempotent (column already nullable, 1990-01-01 already cleaned)."
