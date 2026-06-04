#!/usr/bin/env bash
# =============================================================================
# upload.sh — one-shot uploader for the Layla LIVE deployment.
#
# Pushes all artifacts in deploy-live/ + the backend source to the server,
# and creates the required remote directories. Does NOT start anything or
# delete anything on the server — follow DEPLOY-LIVE.md §3 onwards after this.
#
# Usage:
#   ./upload.sh root@1.2.3.4
#   ./upload.sh root@1.2.3.4 -p 2222          # custom SSH port
#   SRV=root@1.2.3.4 ./upload.sh              # or via env var
#
# Re-runnable: rsync resumes large transfers; scp re-copies small files.
# =============================================================================
set -euo pipefail

# --- locate this script's dir and the project root -------------------------
DEPLOY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$(cd "$DEPLOY/.." && pwd)"

# --- target server ----------------------------------------------------------
SRV="${SRV:-${1:-}}"
shift || true                      # drop the SRV arg if it was positional
SSH_OPTS=()
SCP_OPTS=()
RSYNC_SSH="ssh"
# optional -p PORT passthrough
if [[ "${1:-}" == "-p" && -n "${2:-}" ]]; then
    SSH_OPTS=(-p "$2"); SCP_OPTS=(-P "$2"); RSYNC_SSH="ssh -p $2"
fi

if [[ -z "$SRV" ]]; then
    echo "ERROR: no server given."
    echo "Usage: ./upload.sh user@host [-p PORT]   (or set SRV env var)"
    exit 1
fi

# --- remote layout (LIVE — isolated from the /opt/layla test deploy) --------
APP_DIR="/opt/layla-live"
WEB_DIR="/var/www/layla/data/www/laylamoneytransfer.co.uk"
KYC_DIR="/var/www/layla/data/kyc-uploads-live"

# --- preflight: confirm artifacts exist locally -----------------------------
need=(
  "$DEPLOY/laylamoneytransfer-frontend.tar.gz"
  "$DEPLOY/remitz_production.sql.gz"
  "$DEPLOY/kyc-uploads-legacy.tar.gz"
  "$DEPLOY/docker-compose.production.yml"
  "$DEPLOY/.env.production"
  "$DEPLOY/laylamoneytransfer.co.uk.conf"
)
echo ">> Preflight: checking local artifacts..."
missing=0
for f in "${need[@]}"; do
    if [[ -f "$f" ]]; then
        printf "   [ok]      %-40s %s\n" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
    else
        printf "   [MISSING] %s\n" "$f"; missing=1
    fi
done
if [[ ! -d "$SRC/layla-backend" ]]; then
    echo "   [MISSING] backend source dir: $SRC/layla-backend"; missing=1
else
    echo "   [ok]      layla-backend/ (source)"
fi
[[ $missing -eq 0 ]] || { echo "Aborting: produce the missing artifacts first."; exit 1; }

echo ""
echo ">> Target: $SRV   (SSH ${SSH_OPTS[*]:-default port})"
echo ">> You will be prompted for the server password (set up an SSH key to avoid repeats)."
echo ""

# --- 1. create remote directories ------------------------------------------
echo ">> [1/6] Creating remote directories..."
ssh "${SSH_OPTS[@]}" "$SRV" "mkdir -p '$APP_DIR' '$WEB_DIR' '$KYC_DIR'"

# --- 2. backend source (build context) — exclude build output ---------------
echo ">> [2/6] Backend source -> $APP_DIR/layla-backend/"
rsync -avzP -e "$RSYNC_SSH" --exclude 'target/' --exclude '.git/' \
    "$SRC/layla-backend/" "$SRV:$APP_DIR/layla-backend/"

# --- 3. compose + env + nginx config ----------------------------------------
echo ">> [3/6] Compose / env / nginx config -> $APP_DIR and /tmp"
scp "${SCP_OPTS[@]}" "$DEPLOY/docker-compose.production.yml" "$SRV:$APP_DIR/"
scp "${SCP_OPTS[@]}" "$DEPLOY/.env.production"                "$SRV:$APP_DIR/"
scp "${SCP_OPTS[@]}" "$DEPLOY/laylamoneytransfer.co.uk.conf"  "$SRV:/tmp/"

# --- 4. database dump -------------------------------------------------------
echo ">> [4/6] Database dump -> /tmp"
scp "${SCP_OPTS[@]}" "$DEPLOY/remitz_production.sql.gz"       "$SRV:/tmp/"

# --- 5. frontend bundle -----------------------------------------------------
echo ">> [5/6] Frontend bundle -> /tmp"
scp "${SCP_OPTS[@]}" "$DEPLOY/laylamoneytransfer-frontend.tar.gz" "$SRV:/tmp/"

# --- 6. KYC images (large, resumable) ---------------------------------------
echo ">> [6/6] KYC images (~724 MB, resumable) -> /tmp"
rsync -avzP -e "$RSYNC_SSH" "$DEPLOY/kyc-uploads-legacy.tar.gz"   "$SRV:/tmp/"

cat <<EOF

============================================================
  UPLOAD COMPLETE.
------------------------------------------------------------
  Uploaded to:
    $APP_DIR/layla-backend/                (backend source)
    $APP_DIR/docker-compose.production.yml
    $APP_DIR/.env.production
    /tmp/laylamoneytransfer.co.uk.conf
    /tmp/remitz_production.sql.gz
    /tmp/laylamoneytransfer-frontend.tar.gz
    /tmp/kyc-uploads-legacy.tar.gz

  Next, SSH in and continue with DEPLOY-LIVE.md §3:
    ssh $SRV
    cd $APP_DIR
    cp .env.production .env && nano .env     # fill CHANGE_ME secrets
    # then §4 import DB, §5 KYC, §6 build backend, §7 frontend, §8 nginx
============================================================
EOF
