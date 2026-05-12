#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env.dev ]; then
  echo "[up-dev] devops/.env.dev not found — copying from .env.dev.example"
  cp .env.dev.example .env.dev
fi

docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file .env.dev up --build "$@"
