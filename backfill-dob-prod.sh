#!/usr/bin/env bash
# =============================================================================
# Backfill Date of Birth for migrated users — PRODUCTION.
#
# Mirrors what we did locally: the legacy DoB lives in the old system's
# `user_identity.DOB` table. We import the legacy dump into a throwaway
# `layla_old` DB on the prod MySQL container, then fill remitz.users.date_of_birth
# through prod's OWN migration_user_map (old USER_ID -> new users.id).
#
# SAFE:
#   * only fills users where date_of_birth IS NULL (never overwrites a real value)
#   * `layla_old` is an isolated staging DB — it does not touch prod tables
#   * prints a dry-run count and asks for confirmation before writing
#
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/backfill-dob-prod.sh
# You'll be asked for the server password (scp once, ssh once).
# =============================================================================
set -e

SRV=root@213.171.210.135
DUMP="/mnt/c/Users/umesh/Downloads/layla1 (2).sql"
REMOTE_DUMP=/tmp/layla_legacy_dob.sql

if [ ! -f "$DUMP" ]; then
    echo "ERROR: legacy dump not found at: $DUMP"
    exit 1
fi

echo "== 1/2  Uploading legacy dump to prod ($(du -h "$DUMP" | cut -f1)) =="
scp "$DUMP" "$SRV:$REMOTE_DUMP"

echo "== 2/2  Importing + backfilling on prod =="
ssh "$SRV" "REMOTE_DUMP='$REMOTE_DUMP' bash -s" <<"EOF"
set -e
cd /opt/layla-live
set -a; . ./.env 2>/dev/null; set +a
CT=layla-live-mysql
PW="$MYSQL_ROOT_PASSWORD"
m() { docker exec -i "$CT" mysql -uroot -p"$PW" -N -B "$@" 2>/dev/null; }

echo "---- pre-checks ----"
HAS_MAP=$(m remitz -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='remitz' AND table_name='migration_user_map';")
if [ "$HAS_MAP" != "1" ]; then
    echo "ABORT: remitz.migration_user_map not found on prod — cannot map old users."
    exit 1
fi
echo "migration_user_map rows: $(m remitz -e 'SELECT COUNT(*) FROM migration_user_map;')"
echo "users blank DoB (before): $(m remitz -e 'SELECT COUNT(*) FROM users WHERE date_of_birth IS NULL;')"

echo "---- importing legacy dump into layla_old ----"
m -e "DROP DATABASE IF EXISTS layla_old; CREATE DATABASE layla_old CHARACTER SET latin1 COLLATE latin1_swedish_ci;"
sed 's/DEFINER=[^ ]* //' "$REMOTE_DUMP" \
  | docker exec -i "$CT" mysql -uroot -p"$PW" --default-character-set=latin1 \
        --init-command="SET SESSION sql_mode=''; SET foreign_key_checks=0;" layla_old 2>/dev/null
echo "usable legacy DOB values: $(m layla_old -e "SELECT COUNT(*) FROM user_identity WHERE TRIM(COALESCE(DOB,'')) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$';")"

SRC="(SELECT USER_ID, MAX(TRIM(DOB)) dob FROM layla_old.user_identity
      WHERE TRIM(COALESCE(DOB,'')) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$' GROUP BY USER_ID)"

# We fix BOTH problems in one pass:
#   1. blank DoB  (date_of_birth IS NULL)
#   2. the fake placeholder that shows as 01/01/1990 for many different people
#      (date_of_birth = '1990-01-01')
# ...but ONLY when the legacy ID record has a REAL date, and we never touch a
# genuine KYC-entered date (any value other than NULL / 1990-01-01 is left alone).
TARGET="(nu.date_of_birth IS NULL OR nu.date_of_birth = '1990-01-01')"

echo "placeholder 1990-01-01 rows (before): $(m remitz -e "SELECT COUNT(*) FROM users WHERE date_of_birth='1990-01-01';")"
echo "blank DoB rows (before):              $(m remitz -e 'SELECT COUNT(*) FROM users WHERE date_of_birth IS NULL;')"

echo "---- dry-run ----"
DRY=$(m remitz -e "
SELECT COUNT(*) FROM users nu
JOIN migration_user_map mm ON mm.new_user_id = nu.id
JOIN $SRC o ON o.USER_ID = mm.old_user_id
WHERE $TARGET
  AND STR_TO_DATE(o.dob,'%Y-%m-%d') BETWEEN '1900-01-01' AND CURDATE();")
echo "fixable users (blank + placeholder): $DRY"

if [ "${DRY:-0}" -eq 0 ]; then echo "Nothing to backfill. Done."; exit 0; fi

echo "---- applying backfill ----"
m remitz -e "
SET SESSION sql_mode='';
UPDATE users nu
JOIN migration_user_map mm ON mm.new_user_id = nu.id
JOIN $SRC o ON o.USER_ID = mm.old_user_id
SET nu.date_of_birth = STR_TO_DATE(o.dob,'%Y-%m-%d')
WHERE $TARGET
  AND STR_TO_DATE(o.dob,'%Y-%m-%d') BETWEEN '1900-01-01' AND CURDATE();"

echo "users WITH DoB (after):               $(m remitz -e 'SELECT COUNT(date_of_birth) FROM users;')"
echo "placeholder 1990-01-01 rows (after):  $(m remitz -e "SELECT COUNT(*) FROM users WHERE date_of_birth='1990-01-01';")"
echo "blank DoB rows (after):               $(m remitz -e 'SELECT COUNT(*) FROM users WHERE date_of_birth IS NULL;')"
echo "== DONE — re-download the USI CSV from prod to confirm. =="
EOF
