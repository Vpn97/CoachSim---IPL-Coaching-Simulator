#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env.prod ]; then
  echo "[up-prod] devops/.env.prod missing. Copy .env.prod.example and fill in real values first." >&2
  exit 1
fi

docker compose --profile proxy -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod up -d "$@"
