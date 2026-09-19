#!/usr/bin/env bash
# READ-ONLY diagnosis of why the USI CSV still shows 01/01/1990 on prod.
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/check-dob-prod.sh
# Asks for the server password ONCE. Paste the WHOLE output back.
set -e
SRV=root@213.171.210.135
ssh "$SRV" 'bash -s' <<"EOF"
cd /opt/layla-live
set -a; . ./.env 2>/dev/null; set +a
CT=layla-live-mysql
q() { docker exec -i "$CT" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B remitz -e "$1" 2>/dev/null; }

echo "===== A. Does the legacy staging DB exist on prod? (backfill needs it) ====="
docker exec -i "$CT" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "SHOW DATABASES LIKE 'layla_old';" 2>/dev/null || echo "NONE"
echo "layla_old.user_identity usable DOBs: $(docker exec -i "$CT" mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "SELECT COUNT(*) FROM layla_old.user_identity WHERE TRIM(COALESCE(DOB,'')) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$';" 2>/dev/null || echo NA)"

echo "===== B. Is the old->new user map present on prod? ====="
echo "migration_user_map exists: $(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='remitz' AND table_name='migration_user_map';")"
echo "migration_user_map rows:   $(q "SELECT COUNT(*) FROM migration_user_map;")"

echo "===== C. What does prod actually store for the DoB? ====="
echo "users total:                     $(q "SELECT COUNT(*) FROM users;")"
echo "users with date_of_birth:        $(q "SELECT COUNT(date_of_birth) FROM users;")"
echo "users date_of_birth = 1990-01-01:$(q "SELECT COUNT(*) FROM users WHERE date_of_birth='1990-01-01';")"
echo "users date_of_birth IS NULL:     $(q "SELECT COUNT(*) FROM users WHERE date_of_birth IS NULL;")"
echo "payin_customers dob = 1990-01-01:$(q "SELECT COUNT(*) FROM payin_customers WHERE dob='1990-01-01';")"
echo "payin_customers total:           $(q "SELECT COUNT(*) FROM payin_customers;")"

echo "===== D. Spot-check ABDULAZIM (id 1285) — should become 1973-02-05 ====="
q "SELECT id, first_name, last_name, date_of_birth, uuid FROM users WHERE id=1285;"
echo "  his legacy DoB via map: $(q "SELECT MAX(TRIM(ui.DOB)) FROM layla_old.user_identity ui JOIN migration_user_map mm ON mm.old_user_id=ui.USER_ID WHERE mm.new_user_id=1285 AND TRIM(COALESCE(ui.DOB,'')) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$';")"

echo "===== E. Was the V562 migration applied? (deploy check) ====="
q "SELECT version, description, success FROM flyway_schema_history WHERE version='562';"
echo "payin_customers.dob nullable?: $(q "SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema='remitz' AND table_name='payin_customers' AND column_name='dob';")"

echo "===== F. Backend running which image / since when? ====="
docker inspect -f "started={{.State.StartedAt}} image={{.Config.Image}}" layla-live-backend 2>/dev/null || echo "container not found"
echo "===== END ====="
EOF
