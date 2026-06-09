# One-off: transliterate existing Arabic names in the LIVE DB

Converts Arabic names already stored in production to Latin, using the **same**
logic as the app (`transliterate-names.js` mirrors the `appLatinName` directive).

**Safety design**
- Full backup of the affected tables first.
- We only ever generate **guarded** per-row UPDATEs (`... WHERE id=? AND col='<original>'`),
  wrapped in a single transaction (all-or-nothing). A row that changed since export is skipped.
- A human-readable `*.review.tsv` (id, original, converted) is produced for you to
  eyeball **before** anything is written.
- Nothing is applied until Phase 3.

Server: `root@68.169.55.246`  ·  DB container: `layla-live-mysql`  ·  schema: `remitz`

---

## Phase 0 — Backup (on the server)

```bash
ssh root@68.169.55.246
PW=$(grep -E '^MYSQL_ROOT_PASSWORD=' /opt/layla-live/.env | cut -d= -f2- | tr -d '"')
STAMP=$(date +%F_%H%M%S)
mkdir -p /var/www/layla/backups
docker exec layla-live-mysql sh -c "mysqldump -uroot -p'$PW' --single-transaction \
  --default-character-set=utf8mb4 remitz beneficiaries payin_beneficiaries users transactions" \
  | gzip > /var/www/layla/backups/names_pre_translit_$STAMP.sql.gz
ls -lh /var/www/layla/backups/names_pre_translit_$STAMP.sql.gz   # confirm it's non-empty
```

## Phase 1 — Export the Arabic rows (on the server)

```bash
PW=$(grep -E '^MYSQL_ROOT_PASSWORD=' /opt/layla-live/.env | cut -d= -f2- | tr -d '"')
Q(){ docker exec layla-live-mysql mysql -uroot -p"$PW" remitz -N --batch \
       --default-character-set=utf8mb4 -e "$1"; }

Q "SELECT id, full_name FROM beneficiaries WHERE full_name REGEXP '\\\\p{Arabic}'" > /tmp/ben.tsv
Q "SELECT id, name FROM payin_beneficiaries WHERE name REGEXP '\\\\p{Arabic}'"     > /tmp/pben.tsv
Q "SELECT id, first_name FROM users WHERE first_name REGEXP '\\\\p{Arabic}'"        > /tmp/ufn.tsv
Q "SELECT id, last_name  FROM users WHERE last_name  REGEXP '\\\\p{Arabic}'"        > /tmp/uln.tsv
Q "SELECT id, sender_name FROM transactions WHERE sender_name REGEXP '\\\\p{Arabic}'" > /tmp/tsn.tsv

wc -l /tmp/ben.tsv /tmp/pben.tsv /tmp/ufn.tsv /tmp/uln.tsv /tmp/tsn.tsv
exit
```

## Phase 2 — Generate + review UPDATEs (in WSL)

```bash
cd /mnt/c/Users/kreat/claude/laylanewproject/deploy-live/translit
scp root@68.169.55.246:"/tmp/ben.tsv /tmp/pben.tsv /tmp/ufn.tsv /tmp/uln.tsv /tmp/tsn.tsv" .

node transliterate-names.js beneficiaries      id full_name   ben.tsv  ben.sql  ben.review.tsv
node transliterate-names.js payin_beneficiaries id name       pben.tsv pben.sql pben.review.tsv
node transliterate-names.js users              id first_name  ufn.tsv  ufn.sql  ufn.review.tsv
node transliterate-names.js users              id last_name   uln.tsv  uln.sql  uln.review.tsv
node transliterate-names.js transactions       id sender_name tsn.tsv  tsn.sql  tsn.review.tsv

# REVIEW these — every line is  id <TAB> original <TAB> converted
column -t -s $'\t' ben.review.tsv | less
cat *.review.tsv | wc -l        # total rows that will change
```

➡️ **Stop and read the review files.** If any conversion looks wrong, tell me the
original + what it should be and I'll extend the name dictionary, then re-run Phase 2.

## Phase 3 — Apply (only after you're happy with the review)

```bash
scp ben.sql pben.sql ufn.sql uln.sql tsn.sql root@68.169.55.246:/tmp/
ssh root@68.169.55.246
PW=$(grep -E '^MYSQL_ROOT_PASSWORD=' /opt/layla-live/.env | cut -d= -f2- | tr -d '"')
for f in ben pben ufn uln tsn; do
  echo "== applying $f =="
  docker exec -i layla-live-mysql mysql -uroot -p"$PW" --default-character-set=utf8mb4 remitz < /tmp/$f.sql
done
```

## Phase 4 — Verify (on the server)

```bash
PW=$(grep -E '^MYSQL_ROOT_PASSWORD=' /opt/layla-live/.env | cut -d= -f2- | tr -d '"')
docker exec layla-live-mysql mysql -uroot -p"$PW" remitz --default-character-set=utf8mb4 -e "
 SELECT 'beneficiaries' t, COUNT(*) arabic_left FROM beneficiaries WHERE full_name REGEXP '\\\\p{Arabic}'
 UNION ALL SELECT 'payin_ben', COUNT(*) FROM payin_beneficiaries WHERE name REGEXP '\\\\p{Arabic}'
 UNION ALL SELECT 'users_fn', COUNT(*) FROM users WHERE first_name REGEXP '\\\\p{Arabic}'
 UNION ALL SELECT 'users_ln', COUNT(*) FROM users WHERE last_name  REGEXP '\\\\p{Arabic}'
 UNION ALL SELECT 'txn_sender', COUNT(*) FROM transactions WHERE sender_name REGEXP '\\\\p{Arabic}';"
exit
```
Counts should drop to ~0 (a few may remain if a name had only unmappable characters).

## Rollback (if anything looks wrong)

```bash
ssh root@68.169.55.246
PW=$(grep -E '^MYSQL_ROOT_PASSWORD=' /opt/layla-live/.env | cut -d= -f2- | tr -d '"')
zcat /var/www/layla/backups/names_pre_translit_<STAMP>.sql.gz \
  | docker exec -i layla-live-mysql mysql -uroot -p"$PW" --default-character-set=utf8mb4 remitz
```
