# Prod backend deploy — fixed runbook

Stable steps to push a rebuilt backend jar to LIVE (`68.169.55.246`, `/opt/layla-live/`).
Containers: `layla-live-backend`, `layla-live-redis`, `layla-live-mysql`. Backend → `127.0.0.1:8086`.
Same commands every time. Don't improvise.

## 1. Build (on the dev machine)
```bash
cd /mnt/c/Users/kreat/claude/laylanewproject/layla-backend
mvn clean package -DskipTests          # do NOT use -q (it hides failures)
# artifact: target/layla-monolith-1.0.0-SNAPSHOT.jar
```

## 2. Upload to prod (from the dev/Windows terminal that has the SSH key)
```bash
scp /mnt/c/Users/kreat/claude/laylanewproject/layla-backend/target/layla-monolith-1.0.0-SNAPSHOT.jar \
    root@68.169.55.246:/opt/layla-live/app-new.jar
```

## 3. Deploy on the prod box (ssh root@68.169.55.246)
```bash
cd /opt/layla-live
docker cp layla-live-backend:/app/app.jar ./app.jar.bak-$(date +%s)   # backup (rollback safety)
docker cp app-new.jar layla-live-backend:/app/app.jar                 # swap in new jar
docker restart layla-live-backend
```

## 4. Verify it started
```bash
docker logs --tail 30 layla-live-backend 2>&1 | grep -E 'Started RemitzApplication|APPLICATION FAILED'
curl -s -o /dev/null -w 'backend HTTP %{http_code}\n' http://127.0.0.1:8086/actuator/health
```
Want: `Started RemitzApplication` + `HTTP 200`.
(The `wireRepo() @Autowired` line is a harmless pre-existing warning — ignore it.)

## 5. Rollback (only if step 4 fails)
```bash
cd /opt/layla-live
docker cp "$(ls -t app.jar.bak-* | head -1)" layla-live-backend:/app/app.jar
docker restart layla-live-backend
```

---

## MySQL on prod — always use this form
Root has a password; bare `-p` prompts and fails. Read it from the container env instead:
```bash
docker exec layla-live-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" remitz -t -e "<SQL>;"'
```

## Account recovery (password-overwrite bug)
List affected customers (existing users reset to the default password by old pay-in provisioning):
```bash
docker exec layla-live-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" remitz -t -e "
SELECT u.id, u.email, u.first_name, u.phone
FROM users u
JOIN payin_customers p ON p.email = u.email COLLATE utf8mb4_0900_ai_ci
WHERE u.password_change_required = 1 AND u.created_at < p.created_at
ORDER BY u.updated_at DESC;"'
```
For each row:
- Temporary password = FIRSTNAME (first word, UPPERCASE) + first 4 phone digits, e.g. ABDULAZIM7984.
- Clear their lockout so they can sign in:
  ```bash
  docker exec layla-live-redis redis-cli DEL 'login:attempts:<their-email>'
  ```
- They log in with the temp password and are forced to set a new one.
- NEVER re-hash a password_hash to "fix" login (see no-password-resets).
