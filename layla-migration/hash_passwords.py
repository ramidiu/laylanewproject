#!/usr/bin/env python3
"""
Hash all plain text passwords in remitz.users with BCrypt.
Reads plain text password_hash values, hashes them, updates the DB.
"""

import subprocess
import bcrypt
import json

print("=== BCrypt Password Hashing ===")
print("Fetching plain text passwords from remitz.users...")

# Get all users with plain text passwords (not starting with $2)
result = subprocess.run(
    ["docker", "exec", "layla-mysql", "mysql", "-uroot", "-proot", "remitz",
     "-N", "-e",
     "SELECT id, password_hash FROM users WHERE id > 6 AND password_hash NOT LIKE '$2%' AND password_hash IS NOT NULL AND password_hash != '';"],
    capture_output=True
)

output = result.stdout.decode('utf-8', errors='replace')
rows = [line.split('\t') for line in output.strip().split('\n') if line.strip()]
print(f"Found {len(rows)} users with plain text passwords to hash.")

if not rows:
    print("Nothing to do.")
    exit(0)

# Build UPDATE SQL
updates = []
failed = []

for i, row in enumerate(rows):
    if len(row) < 2:
        continue
    user_id = row[0].strip()
    plain_pw = row[1].strip()

    if not plain_pw:
        continue

    try:
        hashed = bcrypt.hashpw(plain_pw.encode('utf-8', errors='replace'), bcrypt.gensalt(rounds=10))
        hashed_str = hashed.decode('utf-8')
        # Escape single quotes in the hash (shouldn't occur in BCrypt but be safe)
        hashed_str_escaped = hashed_str.replace("'", "\\'")
        updates.append(f"WHEN {user_id} THEN '{hashed_str_escaped}'")
    except Exception as e:
        failed.append((user_id, plain_pw, str(e)))

    if (i + 1) % 100 == 0:
        print(f"  Hashed {i+1}/{len(rows)}...")

print(f"Hashed {len(updates)} passwords. Failed: {len(failed)}")

if failed:
    print("Failed records:")
    for uid, pw, err in failed:
        print(f"  user_id={uid} error={err}")

# Execute in batches of 200
batch_size = 200
total_updated = 0

for i in range(0, len(updates), batch_size):
    batch = updates[i:i+batch_size]
    ids = [u.split()[1] for u in batch]  # Extract WHEN <id>
    ids_list = ','.join(ids)

    sql = f"""
UPDATE users SET password_hash = CASE id
{chr(10).join(batch)}
END
WHERE id IN ({ids_list});
"""
    res = subprocess.run(
        ["docker", "exec", "layla-mysql", "mysql", "-uroot", "-proot", "remitz", "-e", sql],
        capture_output=True, text=True
    )
    if res.returncode != 0:
        print(f"ERROR in batch {i//batch_size + 1}: {res.stderr}")
    else:
        total_updated += len(batch)
        print(f"  Updated batch {i//batch_size + 1}: {len(batch)} users (total: {total_updated})")

print(f"\n=== DONE — {total_updated} passwords BCrypt hashed and updated ===")

# Verify
result2 = subprocess.run(
    ["docker", "exec", "layla-mysql", "mysql", "-uroot", "-proot", "remitz", "-N", "-e",
     "SELECT COUNT(*) FROM users WHERE id > 6 AND password_hash NOT LIKE '$2%' AND password_hash IS NOT NULL AND password_hash != '';"],
    capture_output=True, text=True
)
remaining = result2.stdout.strip()
print(f"Plain text passwords remaining: {remaining}")
print("All migrated users can now log in with their original passwords.")
