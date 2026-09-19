#!/bin/bash
# =============================================================================
# STEP 7 — Backfill Date of Birth for migrated users
#
# The original migration (Phase 2 of 02_migration_procedure.sql) inserted user
# records WITHOUT date_of_birth, so ~900 migrated remitters have a blank DoB in
# the USI CSV export. This script backfills users.date_of_birth from the legacy
# `user` table, joined through the migration_user_map bridge (old -> new id).
#
# It is SAFE and RE-RUNNABLE:
#   * only fills rows where users.date_of_birth IS NULL (never overwrites a real
#     value a customer entered during KYC)
#   * ignores zero-dates ('0000-00-00'), empty strings and obviously invalid DoB
#
# Usage:
#   bash 07_backfill_dob.sh "/mnt/c/Users/umesh/Downloads/layla1 (1).sql"
#   bash 07_backfill_dob.sh            # if layla_old is already imported
# =============================================================================

set -e

SQL_FILE="${1:-}"
CONTAINER="layla-mysql"
DB_OLD="layla_old"
DB_NEW="remitz"
MYSQL_USER="root"
MYSQL_PASS="root"

mysql_new() { docker exec -i "$CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -N -B "$DB_NEW" 2>/dev/null; }
mysql_raw() { docker exec -i "$CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -N -B 2>/dev/null; }

echo ""
echo "============================================================"
echo "  LAYLA — Backfill Date of Birth for migrated users"
echo "============================================================"

# 1. Ensure the legacy staging DB exists; import the dump if it doesn't.
HAS_OLD=$(echo "SHOW DATABASES LIKE '$DB_OLD';" | mysql_raw | wc -l)
if [ "$HAS_OLD" -eq 0 ]; then
    if [ -z "$SQL_FILE" ] || [ ! -f "$SQL_FILE" ]; then
        echo "ERROR: '$DB_OLD' is not present and no valid dump path was given."
        echo "       Pass the legacy SQL dump as the first argument, e.g.:"
        echo "       bash 07_backfill_dob.sh \"/mnt/c/Users/umesh/Downloads/layla1 (1).sql\""
        exit 1
    fi
    echo ">> Importing legacy dump into $DB_OLD (this may take a minute)..."
    echo "DROP DATABASE IF EXISTS $DB_OLD; CREATE DATABASE $DB_OLD CHARACTER SET latin1 COLLATE latin1_swedish_ci;" | mysql_raw
    sed 's/DEFINER=[^ ]* //' "$SQL_FILE" | docker exec -i "$CONTAINER" mysql \
        -u"$MYSQL_USER" -p"$MYSQL_PASS" --default-character-set=latin1 \
        --init-command="SET SESSION sql_mode=''; SET foreign_key_checks=0;" "$DB_OLD" 2>/dev/null
    echo "[OK] Dump imported into $DB_OLD"
else
    echo "[OK] Legacy database '$DB_OLD' already present — reusing it."
fi

# 2. DoB source: the legacy DoB lives on `user_identity.DOB` (the ID-document
#    record), keyed by USER_ID. The `customer` table also has a DOB column but
#    is empty in the dump. Values are stored as 'YYYY-MM-DD' varchars; a few are
#    junk ('-', blank), so we accept only strictly-formatted dates. A user can
#    have several ID docs — take MAX() so we get one deterministic DoB per user.
DOB_SRC="(SELECT USER_ID, MAX(TRIM(DOB)) dob
          FROM $DB_OLD.user_identity
          WHERE TRIM(COALESCE(DOB,'')) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$'
          GROUP BY USER_ID)"

HAS_SRC=$(echo "SELECT COUNT(*) FROM $DB_OLD.user_identity
    WHERE TRIM(COALESCE(DOB,'')) REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}\$';" | mysql_raw)
if [ "${HAS_SRC:-0}" -eq 0 ]; then
    echo "ERROR: No usable DOB values found in $DB_OLD.user_identity. Nothing to backfill."
    exit 1
fi
echo "[OK] Legacy DoB source: user_identity.DOB ($HAS_SRC usable values)"

# 3. Dry-run — how many blank-DoB migrated users can we fill?
echo ""
echo ">> Dry-run: counting fillable rows..."
DRY=$(cat <<SQL | mysql_new
SELECT COUNT(*)
FROM $DB_NEW.users nu
JOIN $DB_NEW.migration_user_map m ON m.new_user_id = nu.id
JOIN $DOB_SRC o ON o.USER_ID = m.old_user_id
WHERE nu.date_of_birth IS NULL
  AND STR_TO_DATE(o.dob, '%Y-%m-%d') BETWEEN '1900-01-01' AND CURDATE();
SQL
)
echo "   -> $DRY migrated users will get a Date of Birth."

if [ "${DRY:-0}" -eq 0 ]; then
    echo "Nothing to backfill. Exiting."
    exit 0
fi

read -r -p "Apply the backfill to $DRY rows? [y/N] " ans
if [ "$ans" != "y" ] && [ "$ans" != "Y" ]; then
    echo "Aborted — no changes made."
    exit 0
fi

# 4. Apply. (sql_mode relaxed so a stray value can never abort the whole UPDATE.)
cat <<SQL | mysql_new
SET SESSION sql_mode='';
UPDATE $DB_NEW.users nu
JOIN $DB_NEW.migration_user_map m ON m.new_user_id = nu.id
JOIN $DOB_SRC o ON o.USER_ID = m.old_user_id
SET nu.date_of_birth = STR_TO_DATE(o.dob, '%Y-%m-%d')
WHERE nu.date_of_birth IS NULL
  AND STR_TO_DATE(o.dob, '%Y-%m-%d') BETWEEN '1900-01-01' AND CURDATE();
SQL

FILLED=$(echo "SELECT COUNT(date_of_birth) FROM users;" | mysql_new)
echo ""
echo "[DONE] users.date_of_birth is now populated for $FILLED users."
echo "       Re-download the USI CSV to see the backfilled dates."
