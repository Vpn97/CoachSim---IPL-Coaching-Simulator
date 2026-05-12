#!/bin/sh
# Nightly pg_dump backup written to /backups with timestamp.
# Keeps last 14 daily backups; deletes anything older.
set -eu

TS=$(date -u +%Y%m%dT%H%M%SZ)
OUT="/backups/coachsim-${TS}.sql.gz"

echo "[pg-backup] dumping ${PGDATABASE} -> ${OUT}"
pg_dump --no-owner --no-privileges "${PGDATABASE}" | gzip > "${OUT}"

# Retention: keep the 14 most recent dumps.
ls -1t /backups/coachsim-*.sql.gz 2>/dev/null | tail -n +15 | xargs -r rm -f

echo "[pg-backup] done"
