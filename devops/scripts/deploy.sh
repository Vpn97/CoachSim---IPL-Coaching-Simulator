#!/usr/bin/env bash
# Production deploy: pull latest, rebuild backend + frontend, roll services,
# wait for /actuator/health to flip to UP, then smoke-test a public endpoint.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env.prod ]; then
  echo "[deploy] .env.prod missing" >&2
  exit 1
fi
set -a; source .env.prod; set +a

COMPOSE="docker compose --profile proxy -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod"

echo "[deploy] pulling latest source (caller's responsibility for VCS)"
echo "[deploy] building images..."
$COMPOSE build backend frontend

echo "[deploy] rolling postgres..."
$COMPOSE up -d postgres
sleep 5

echo "[deploy] rolling backend with no-downtime restart..."
$COMPOSE up -d --no-deps --remove-orphans backend

echo "[deploy] rolling frontend..."
$COMPOSE up -d --no-deps frontend

echo "[deploy] rolling traefik..."
$COMPOSE up -d --no-deps traefik

echo "[deploy] waiting for backend health..."
for i in $(seq 1 30); do
  if $COMPOSE exec -T backend curl -fsS http://localhost:8080/actuator/health/readiness > /dev/null 2>&1; then
    echo "[deploy] backend is READY"
    break
  fi
  sleep 2
done

echo "[deploy] smoke test against https://${PUBLIC_HOST}/actuator/health/liveness"
curl -fsS "https://${PUBLIC_HOST}/actuator/health/liveness" || {
  echo "[deploy] smoke test FAILED"; exit 1;
}

echo "[deploy] done"
