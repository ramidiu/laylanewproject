#!/usr/bin/env bash
# Get the rate-slabs feature working on prod: ensure V549 reaches the server,
# rebuild the backend so Flyway creates the table, then verify.
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/fix-slabs.sh
# Enter the server password whenever prompted (a few times).
SRV=root@68.169.55.246
SRC=/mnt/c/Users/umesh/claude/laylanewproject
MIGDIR=/opt/layla-live/layla-backend/src/main/resources/db/migration
V549=src/main/resources/db/migration/V549__create_corridor_rate_slabs.sql

echo "== 1. rsync backend source (in-file, cannot wrap) =="
rsync -avz --exclude 'target/' --exclude '.git/' "$SRC/layla-backend/" "$SRV:/opt/layla-live/layla-backend/" \
  || echo "   rsync returned non-zero (continuing — the explicit scp below is the guarantee)"

echo "== 2. scp the V549 file explicitly =="
scp "$SRC/layla-backend/$V549" "$SRV:$MIGDIR/"

echo "== 3. confirm V549 is now on the server =="
ssh "$SRV" "ls $MIGDIR/ | grep 549 || echo 'STILL-MISSING'"

echo "== 4. rebuild backend (no cache) + restart =="
ssh "$SRV" 'cd /opt/layla-live && docker compose -f docker-compose.production.yml build --no-cache backend && docker compose -f docker-compose.production.yml up -d backend'

echo "== 5. wait 50s for boot, then verify Flyway + table =="
sleep 50
ssh "$SRV" 'cd /opt/layla-live; set -a; . ./.env 2>/dev/null; set +a; docker exec layla-live-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT version,success FROM remitz.flyway_schema_history WHERE version=549; SHOW TABLES LIKE \"corridor_rate_slabs\";"'

echo "== DONE (want: '549  1' and 'corridor_rate_slabs') =="
