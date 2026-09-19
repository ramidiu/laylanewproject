#!/usr/bin/env bash
# Diagnose the prod KYC "already under review" issue.
# Run from WSL:  bash /mnt/c/Users/umesh/claude/laylanewproject/check-prod.sh
# Asks for the server password ONCE. Paste the whole output back.
ssh root@213.171.210.135 'bash -s' <<"EOF"
echo "===== 1. Backend SOURCE has the fix?  (expect: 1) ====="
grep -c "This document is already under review" /opt/layla-live/layla-backend/src/main/java/com/remitz/modules/user/service/KycService.java 2>/dev/null || echo "MISSING_FILE"

echo "===== 2. Backend container — when was it (re)started? ====="
docker inspect -f "started={{.State.StartedAt}}  image={{.Config.Image}}" layla-live-backend 2>/dev/null || echo "container not found"

echo "===== 3. Frontend bundle on server has the _BACK fix? ====="
grep -rl "_BACK" /var/www/layla/data/www/laylamoneytransfer.co.uk/*.js 2>/dev/null | head -1 || echo "NO_BACK_IN_BUNDLE"

echo "===== 4. KYC docs for reragox374@soppat.com ====="
cd /opt/layla-live; set -a; . ./.env 2>/dev/null; set +a
docker exec layla-live-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
"SELECT id,document_type,document_number,status,superseded_at FROM remitz.kyc_documents d JOIN remitz.users u ON u.id=d.user_id WHERE u.email='reragox374@soppat.com' ORDER BY d.created_at DESC;" 2>&1
echo "===== END ====="
EOF
