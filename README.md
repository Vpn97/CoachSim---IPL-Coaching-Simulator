# CoachSim — IPL Coaching Simulator

A production-ready real-time fan-engagement platform where viewers make field-placement and
bowling-change decisions during live IPL matches and are scored against the captain's actual
moves using a rules-based tactical-merit engine.

> **Stack**: Spring Boot 3 (Java 21) · Angular 17 · PostgreSQL 16 · Traefik v3 · Docker Compose

---

## Highlights

- **Real-time** ball-by-ball events via STOMP-over-WebSocket (`/topic/match.{id}`).
- **Pluggable ingestion** — swap `mock` (admin-driven) and `external` (HTTP polled cricket API) via a single env var.
- **Explainable scoring** — every score comes with a per-rule breakdown stored in JSON.
- **Materialised leaderboard** — match · season · all-time, refreshed on a schedule.
- **JWT auth** with Spring Security, BCrypt, and admin-gated mock-control endpoints.
- **Observability** — Spring Actuator + Micrometer Prometheus, JSON logs in prod.
- **One-command dev** via `docker compose`. TLS-on-by-default prod overlay via Traefik + Let's Encrypt.

---

## Architecture

```
PROD                                  DEV
[ Fan Browser ]                       [ Fan Browser ]
      |                                     |
      | HTTPS/WSS                           | HTTP
      v                                     v
[ Traefik ] -> /api,/ws -> [ Spring ]  [ Frontend nginx ] -> /api,/ws -> [ Spring ]
      \-----> /         -> [ Frontend]        \-----> /         (SPA)
                              \-> [ Postgres ]                          \-> [ Postgres ]
```

- **Prod** is fronted by Traefik (TLS, sticky cookie for WS, security headers).
- **Dev** skips Traefik entirely — the frontend's nginx is the single host entrypoint and proxies `/api` + `/ws` to the backend container over the compose network. The SPA uses relative paths, so client code is identical in dev and prod.

See [.cursor/plans/ipl_coaching_simulator_plan_f3c962ce.plan.md](.cursor/plans/ipl_coaching_simulator_plan_f3c962ce.plan.md) for the full design.

---

## Repository layout

```
backend/   Spring Boot (Java 21, Gradle)
frontend/  Angular 17 SPA
devops/    All docker, traefik, env, and deploy scripts
```

---

## Dev quickstart

Prereqs: Docker Desktop (or Docker Engine + Compose v2).

```bash
# 1. one-time env setup (auto-copied on first run too)
cp devops/.env.dev.example devops/.env.dev

# 2. start the whole stack
docker compose -f devops/docker-compose.yml -f devops/docker-compose.dev.yml \
               --env-file devops/.env.dev up --build
```

Or, from the repo root:

- bash:       `bash devops/scripts/up-dev.sh`
- PowerShell: `pwsh devops/scripts/up-dev.ps1`

Then open:

| What           | URL                                            |
| -------------- | ---------------------------------------------- |
| App            | http://localhost                               |
| API (via SPA proxy) | http://localhost/api                      |
| API (direct, for curl/Postman) | http://localhost:8080/api      |
| Health         | http://localhost/actuator/health               |
| Prometheus     | http://localhost/actuator/prometheus           |
| Postgres       | `localhost:5432`  user `coachsim` / `coachsim` |

> If something on your machine is already using port 80, 8080 or 5432, edit
> `devops/.env.dev` and set `FRONTEND_HOST_PORT` / `BACKEND_HOST_PORT` /
> `POSTGRES_HOST_PORT` to free values — the SPA uses relative URLs so picking
> a different frontend port "just works".

### Demo accounts

Seeded by Flyway migrations [`V2__seed_admin.sql`](backend/src/main/resources/db/migration/V2__seed_admin.sql) and [`V4__seed_demo_data.sql`](backend/src/main/resources/db/migration/V4__seed_demo_data.sql).

| Role  | Email                       | Password   | Notes                                        |
| ----- | --------------------------- | ---------- | -------------------------------------------- |
| Admin | `admin@coachsim.local`      | `admin123` | Sees the Admin nav link, can run auto-play   |
| Fan   | `rohit@coachsim.local`      | `fan1234`  | Has historical decisions in their profile    |
| Fan   | `virat@coachsim.local`      | `fan1234`  |                                              |
| Fan   | `msd@coachsim.local`        | `fan1234`  |                                              |
| Fan   | `hardik@coachsim.local`     | `fan1234`  |                                              |
| Fan   | `shubman@coachsim.local`    | `fan1234`  |                                              |
| Fan   | `jadeja@coachsim.local`     | `fan1234`  |                                              |
| Fan   | `iyer@coachsim.local`       | `fan1234`  |                                              |
| Fan   | `kuldeep@coachsim.local`    | `fan1234`  |                                              |
| Fan   | `bumrah@coachsim.local`     | `fan1234`  |                                              |
| Fan   | `arshdeep@coachsim.local`   | `fan1234`  |                                              |

> Rotate or remove these in production.

### Seeded matches

- **`DEMO-COMPLETED-001`** — MI vs CSK (completed). Full 20-over innings, captain moves, fan decisions, and merit scores. Drives the **Leaderboard** + **Profile** pages on first boot.
- **`DEMO-LIVE-001`** — RCB vs GT (LIVE). One over already bowled — perfect target for the auto-play simulator.
- **`HIST-001`** — small synthetic historical sample used by `HistoricalEconomyRule`.

### Driving a live demo (three paths)

**Path A — One-click auto-play (easiest):**

1. Log in as `admin@coachsim.local`.
2. Open **Admin** → **Live matches** → select *RCB vs GT*.
3. Under *Auto-play simulation* hit **Start auto-play**.
4. In a second browser window, log in as `rohit@coachsim.local` and open **Live** — you'll see ball events tick in, decision windows open, and reveals arrive over STOMP every couple of overs.

**Path B — Manual mock:** create your own match in the Admin screen, push individual balls + captain moves.

**Path C — Real CricAPI integration:** flip `INGESTION_PROVIDER=external` (see below) — the ingestion scheduler will sync IPL fixtures from cricapi.com and create LIVE matches automatically.

---

## Production deploy

```bash
# 1. fill in real secrets
cp devops/.env.prod.example devops/.env.prod
vim devops/.env.prod   # PUBLIC_HOST, ACME_EMAIL, secrets, vendor API keys

# 2. bring it up
bash devops/scripts/deploy.sh
```

The deploy script:

1. builds backend + frontend images,
2. rolls services (postgres → backend → frontend → traefik),
3. waits for `/actuator/health/readiness` to flip to `UP`,
4. runs a smoke check against `https://${PUBLIC_HOST}/actuator/health/liveness`.

Traefik issues Let's Encrypt certs automatically on first run (HTTP-01 challenge).

### Horizontal scale

Bump backend replicas:

```env
# devops/.env.prod
BACKEND_REPLICAS=4
```

WebSocket affinity is already handled — Traefik sticky cookie `coachsim_ws_sticky` keeps each
fan pinned to the backend pod that holds their STOMP session. For multi-node scale beyond
sticky sessions, swap the `enableSimpleBroker(...)` call in [`WebSocketConfig.java`](backend/src/main/java/com/coachsim/realtime/WebSocketConfig.java) for `enableStompBrokerRelay(...)` (RabbitMQ STOMP) — no client changes needed.

### Backups

The `pg-backup` sidecar in `docker-compose.prod.yml` runs `pg_dump` nightly into the `pg_backups`
volume, retaining the most recent 14 days. Restore with:

```bash
docker compose -f devops/docker-compose.yml -f devops/docker-compose.prod.yml \
  exec postgres sh -c "gunzip -c /backups/coachsim-XXXXXX.sql.gz | psql -U $POSTGRES_USER $POSTGRES_DB"
```

---

## Runbook: switch from mock → real CricAPI

CoachSim ships with a real, working adapter for [**cricapi.com**](https://cricapi.com/) v2.

1. Sign up at https://cricapi.com/ for a free API key (100 requests/day on the free tier).
2. Set in `devops/.env.dev` (or `.env.prod`):

   ```env
   INGESTION_PROVIDER=external
   EXTERNAL_API_BASE_URL=https://api.cricapi.com/v1
   EXTERNAL_API_KEY=your-cricapi-key
   INGESTION_POLL_INTERVAL_MS=60000     # 60s — respects free-tier 100 req/day
   ```

3. Roll the backend:

   ```bash
   bash devops/scripts/deploy.sh        # prod
   # or, dev:
   docker compose -f devops/docker-compose.yml -f devops/docker-compose.dev.yml restart backend
   ```

4. On startup you'll see:

   ```
   Ingestion providers registered: [mock, external] | active = 'external'
   ```

What the adapter does:

- Polls `GET /v1/currentMatches` → upserts T20/IPL fixtures as `Match` rows (`source=EXTERNAL`, `status=LIVE|COMPLETED|SCHEDULED`).
- Polls `GET /v1/match_scorecard?id=…` for each LIVE external match → derives coarse `BALL` events from innings-total deltas.

**Free-tier limitation**: CricAPI doesn't expose true ball-by-ball commentary on the free plan, so the BALL events are coarse (one synthetic event per total-runs delta). For per-ball commentary, swap the adapter for **SportMonks Cricket** or **RapidAPI Cricbuzz**, or use the **auto-play simulator** for demos — both options are documented in [`ExternalCricketApiProvider`](backend/src/main/java/com/coachsim/ingestion/ExternalCricketApiProvider.java) and [`AutoPlaySimulator`](backend/src/main/java/com/coachsim/admin/AutoPlaySimulator.java).

### Auto-play simulator (for free demos)

Even with `INGESTION_PROVIDER=mock`, the admin panel can drive a realistic live match without manual clicking:

- `POST /api/admin/matches/{id}/auto-play/start` — body `{ "ballEverySeconds": 5 }`
- `POST /api/admin/matches/{id}/auto-play/stop`
- `GET  /api/admin/matches/{id}/auto-play`

The simulator generates weighted dot/single/four/six distributions, occasional wickets, and a captain bowling change every ~12 balls. Phase-aware: pace in powerplay & death, spin in middle overs.

---

## Testing

Backend:

```bash
cd backend
./gradlew test    # or: docker run --rm -v "$PWD:/app" -w /app gradle:8.10-jdk21 gradle test
```

Frontend:

```bash
cd frontend
npm install
npm test
```

---

## API surface (highlights)

| Method | Path                                  | Auth        | Description                          |
| ------ | ------------------------------------- | ----------- | ------------------------------------ |
| POST   | `/api/auth/register`                  | public      | Register a new fan                   |
| POST   | `/api/auth/login`                     | public      | Issue JWT                            |
| GET    | `/api/matches/live`                   | public      | All LIVE matches                     |
| GET    | `/api/matches/{id}/state`             | public      | Score + last ball per innings        |
| GET    | `/api/decisions/windows/open?matchId` | public      | Open decision windows for a match    |
| POST   | `/api/decisions`                      | JWT         | Submit a decision                    |
| GET    | `/api/decisions/history`              | JWT         | Logged-in fan's history              |
| GET    | `/api/leaderboard/alltime`            | public      | All-time leaderboard                 |
| GET    | `/api/leaderboard/season/{season}`    | public      | Per-season leaderboard               |
| GET    | `/api/leaderboard/match/{matchId}`    | public      | Per-match leaderboard                |
| POST   | `/api/admin/matches`                  | ROLE_ADMIN  | Create a mock LIVE match             |
| POST   | `/api/admin/balls`                    | ROLE_ADMIN  | Push a synthetic ball                |
| POST   | `/api/admin/captain-move`             | ROLE_ADMIN  | Reveal captain's move (resolves windows) |

WebSocket destinations (STOMP at `/ws`):

| Destination                       | Description                                |
| --------------------------------- | ------------------------------------------ |
| `/topic/match.{id}`               | Ball + captain-move events                 |
| `/topic/match.{id}.windows`       | Decision-window open/close events          |
| `/user/queue/decision-result`     | This fan's score + breakdown reveal        |

---

## License

MIT — see [LICENSE](LICENSE) if/when added.
