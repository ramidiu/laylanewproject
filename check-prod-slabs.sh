#!/usr/bin/env bash
# Diagnose the prod rate-slabs 500. Run from WSL:
#   bash /mnt/c/Users/umesh/claude/laylanewproject/check-prod-slabs.sh
# Asks for the server password once. Paste the WHOLE output back.
ssh root@68.169.55.246 'bash -s' <<"EOF"
cd /opt/layla-live; set -a; . ./.env 2>/dev/null; set +a
echo "===== 1. V549 migration file present in uploaded source? ====="
ls -1 /opt/layla-live/layla-backend/src/main/resources/db/migration/ | grep -i 549 || echo "V549 FILE MISSING ON SERVER"

echo "===== 2. corridor_rate_slabs table exists? (1 = yes) ====="
docker exec layla-live-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='remitz' AND table_name='corridor_rate_slabs';" 2>&1

echo "===== 3. Flyway: top 5 + any 549 row ====="
docker exec layla-live-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
"SELECT version, success FROM remitz.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;" 2>&1

echo "===== 4. Backend container started at ====="
docker inspect -f "{{.State.StartedAt}}" layla-live-backend 2>&1

echo "===== 5. Backend log — last rate-slabs error/stack ====="
docker logs layla-live-backend 2>&1 | grep -iE "rate-slabs|corridor_rate_slabs|RateSlab|doesn't exist|Table .* doesn" | tail -8

echo "===== END ====="
EOF
