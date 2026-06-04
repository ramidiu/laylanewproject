#!/bin/bash
# =============================================================================
# MASTER RUNNER — Executes all migration steps in order.
#
# Usage:
#   bash run_migration.sh setup         # Step 0: import old SQL into layla_old
#   bash run_migration.sh infrastructure # Step 1: create support tables
#   bash run_migration.sh procedure      # Step 2: create stored procedure
#   bash run_migration.sh dryrun         # Step 3: dry-run (ROLLBACK)
#   bash run_migration.sh live           # Step 4: live commit
#   bash run_migration.sh validate       # Step 5: post-migration validation
#   bash run_migration.sh rollback       # Step 6: undo migration (CAUTION!)
#
# Full recommended sequence:
#   bash run_migration.sh setup
#   bash run_migration.sh infrastructure
#   bash run_migration.sh procedure
#   bash run_migration.sh dryrun         ← review output carefully
#   bash run_migration.sh live           ← only after dryrun looks good
#   bash run_migration.sh validate       ← confirm everything is correct
# =============================================================================

set -e

CONTAINER="layla-mysql"
MYSQL_USER="root"
MYSQL_PASS="root"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mysql_remitz() {
    docker exec -i "$CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" remitz 2>/dev/null
}

header() {
    echo ""
    echo "============================================================"
    echo "  $1"
    echo "============================================================"
    echo ""
}

case "$1" in

  setup)
    header "Step 0: Setting up staging database (layla_old)"
    bash "$SCRIPT_DIR/00_setup_staging.sh"
    ;;

  infrastructure)
    header "Step 1: Creating migration infrastructure tables"
    mysql_remitz < "$SCRIPT_DIR/01_infrastructure.sql"
    echo "[OK] Infrastructure tables created"
    ;;

  procedure)
    header "Step 2: Loading migration stored procedure"
    mysql_remitz < "$SCRIPT_DIR/02_migration_procedure.sql"
    echo "[OK] run_migration() procedure loaded"
    ;;

  dryrun)
    header "Step 3: DRY RUN — simulate migration (no data committed)"
    mysql_remitz < "$SCRIPT_DIR/03_run_dryrun.sql"
    echo ""
    echo ">>> DRY RUN complete. Review the report above."
    echo ">>> If all counts look correct, run:  bash run_migration.sh live"
    ;;

  live)
    header "Step 4: LIVE MIGRATION — committing data permanently"
    read -p "Are you sure? Type YES to proceed: " confirm
    if [ "$confirm" != "YES" ]; then
        echo "Aborted."
        exit 0
    fi
    mysql_remitz < "$SCRIPT_DIR/04_run_live.sql"
    echo ""
    echo ">>> Migration committed. Now run:  bash run_migration.sh validate"
    ;;

  validate)
    header "Step 5: Post-migration validation"
    mysql_remitz < "$SCRIPT_DIR/05_validation.sql"
    ;;

  rollback)
    header "Step 6: ROLLBACK — removing migrated data"
    echo "WARNING: This will delete all migrated data from remitz."
    read -p "Type ROLLBACK to confirm: " confirm
    if [ "$confirm" != "ROLLBACK" ]; then
        echo "Aborted."
        exit 0
    fi
    mysql_remitz < "$SCRIPT_DIR/06_rollback.sql"
    ;;

  *)
    echo "Usage: bash run_migration.sh <step>"
    echo ""
    echo "Steps:"
    echo "  setup          Import old SQL into layla_old staging DB"
    echo "  infrastructure Create support tables in remitz"
    echo "  procedure      Load the run_migration() stored procedure"
    echo "  dryrun         Simulate migration, show report, rollback"
    echo "  live           Execute migration and commit (IRREVERSIBLE)"
    echo "  validate       Run post-migration validation queries"
    echo "  rollback       Remove all migrated data (use with caution)"
    echo ""
    echo "Recommended order: setup → infrastructure → procedure → dryrun → live → validate"
    ;;

esac
