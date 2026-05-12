---
name: IPL Coaching Simulator Plan
overview: "Build a production-ready Coaching Simulator where fans make real-time field/bowling decisions during live IPL matches, scored against the captain's actual moves using a rules-based tactical-merit engine. Stack: Spring Boot + Angular + PostgreSQL, fronted by Traefik, packaged with Docker Compose for both dev and prod."
todos:
  - id: scaffold-repo
    content: "Scaffold monorepo: backend (Spring Boot + Gradle), frontend (Angular CLI), devops/ folder with base docker-compose and traefik subfolder"
    status: completed
  - id: db-migrations
    content: Add Flyway migrations for users, matches, innings, balls, captain_moves, decision_windows, fan_decisions, decision_scores, leaderboard_snapshot
    status: completed
  - id: auth-module
    content: "Implement auth module: register/login REST, Spring Security + JWT, BCrypt, AuthInterceptor on Angular"
    status: completed
  - id: match-module
    content: Implement match + innings + ball entities, repositories, and read endpoints for current match state
    status: completed
  - id: ingestion-pluggable
    content: Implement MatchDataProvider interface with MockMatchDataProvider and ExternalCricketApiProvider, wired by app.ingestion.provider property + IngestionScheduler
    status: completed
  - id: websocket-stomp
    content: Configure STOMP over WebSocket at /ws, /topic/match.{id} broadcasts and /user/queue/decision-result, sticky-session compatible
    status: completed
  - id: decision-module
    content: Decision windows + FanDecision submit endpoint with window validation; emit decision-window events to STOMP
    status: completed
  - id: scoring-engine
    content: Implement TacticalMeritEngine with pluggable Rule classes (exact match, historical economy fit, field coverage) and ScoringStrategy interface
    status: completed
  - id: leaderboard
    content: Implement leaderboard service with scheduled snapshot refresh and per-match / season / all-time endpoints
    status: completed
  - id: admin-panel
    content: Admin REST endpoints for mock match control (advance ball, set captain move) + Angular admin screen gated by ROLE_ADMIN
    status: completed
  - id: angular-live-ui
    content: "Build Angular live-match screen: live score, decision panel (field grid + bowling change), countdown timer, reveal/score view"
    status: completed
  - id: angular-leaderboard-profile
    content: Build Angular leaderboard and profile/history screens
    status: completed
  - id: traefik-routing
    content: Configure Traefik in devops/traefik/ (traefik.yml + dynamic/tls.yml) with HTTP routers for /api, /ws (sticky), and SPA catch-all; Let's Encrypt resolver for prod overlay
    status: completed
  - id: compose-overlays
    content: Finalize devops/docker-compose.yml + dev/prod overlays with healthchecks, .env.*.example templates, Postgres volume, pg_dump backup sidecar, and up-dev/up-prod helper scripts
    status: completed
  - id: observability
    content: Wire Spring Actuator + Micrometer Prometheus endpoint, JSON logback config, basic /actuator/health for healthchecks
    status: completed
  - id: tests
    content: "Backend: JUnit tests for scoring rules and decision window validation; Angular: a few component + service tests; one end-to-end happy path"
    status: completed
  - id: readme-runbook
    content: Write README with dev quickstart (docker compose -f devops/docker-compose.yml -f devops/docker-compose.dev.yml up), prod deploy steps via devops/scripts/deploy.sh, and runbook for swapping mock→external provider
    status: completed
isProject: false
---

## 1. High-Level Architecture

```mermaid
flowchart LR
  Fan[Fan Browser - Angular SPA]
  Admin[Admin Panel]
  Traefik[Traefik Reverse Proxy]
  API[Spring Boot API + STOMP]
  Ingest[Match Ingestion Worker]
  DB[(PostgreSQL)]
  ExtAPI[External Cricket API]
  MockSrc[Mock Match Source]

  Fan -->|HTTPS / WSS| Traefik
  Admin -->|HTTPS| Traefik
  Traefik -->|/api, /ws| API
  Traefik -->|/| Fan
  API <--> DB
  Ingest -->|writes balls + captain moves| DB
  Ingest -->|publishes via STOMP| API
  ExtAPI --> Ingest
  MockSrc --> Ingest
```

Single Spring Boot service exposes REST + STOMP-over-WebSocket. A scheduled component inside the same JAR polls a pluggable `MatchDataProvider`, persists events, and publishes to STOMP topics.

**Why STOMP over WebSocket (vs SSE):** bidirectional, per-user destinations (`/user/queue/decision-result`), idiomatic in Spring (`spring-boot-starter-websocket`), and lets us swap the in-memory simple broker for a RabbitMQ STOMP relay later without changing client code when we need to scale beyond one node.

## 2. Modules and Key Files (proposed)

Backend (`backend/src/main/java/com/coachsim/`):

- `auth/` — `AuthController`, `JwtService`, `SecurityConfig` (Spring Security + JWT, BCrypt)
- `user/` — `User` entity, `UserRepository`, `ProfileController`
- `match/` — `Match`, `Innings`, `Ball`, `CaptainMove` entities + repos; `MatchController` (list/details)
- `ingestion/`
  - `MatchDataProvider` interface — `Flux<MatchEvent> stream(matchId)` or pull-based `List<MatchEvent> pollSince(...)`
  - `MockMatchDataProvider` (reads scripted timeline from DB, admin-triggered)
  - `ExternalCricketApiProvider` (HTTP polling, mapped to internal model)
  - `IngestionScheduler` (`@Scheduled`) picks impl via `app.ingestion.provider` property
- `decision/` — `FanDecision` entity, `DecisionWindow` (opens before each ball/over for N seconds), `DecisionController` (`POST /api/decisions`), `DecisionService` (validates window, persists, enqueues scoring)
- `scoring/` — `TacticalMeritEngine` (rules v1, see §4), `ScoreCalculatorService`, designed behind a `ScoringStrategy` interface so an ML service can slot in later
- `leaderboard/` — `LeaderboardService` (per-match, per-season, all-time), materialized via scheduled refresh of a `leaderboard_snapshot` table to avoid heavy aggregations on each request
- `realtime/` — `WebSocketConfig` (STOMP, `/ws` endpoint, `/topic/match.{id}` and `/user/queue/...`), `MatchEventPublisher`
- `admin/` — `AdminController` for creating mock matches, advancing balls, setting captain's move (gated by `ROLE_ADMIN`)

Frontend (`frontend/src/app/`):

- `core/` — `AuthService`, `AuthInterceptor`, `WebSocketService` (uses `@stomp/rx-stomp`)
- `features/auth/` — login, register
- `features/match-live/` — `LiveMatchComponent` (score, current ball), `DecisionPanelComponent` (field placement grid + bowling change dropdown, countdown timer), `RevealComponent` (your move vs captain's + score)
- `features/leaderboard/`
- `features/profile/` — decision history, tactical-merit trend
- `features/admin/` — match control panel (only shown for admin role)

## 3. Data Model (core tables)

- `users(id, email, password_hash, display_name, role, created_at)`
- `matches(id, external_id, season, home_team, away_team, venue, status, source)`
- `innings(id, match_id, batting_team, bowling_team, number)`
- `balls(id, innings_id, over, ball_in_over, bowler_id, batter_id, runs, wicket, extras, created_at)`
- `captain_moves(id, match_id, type [BOWLING_CHANGE|FIELD_SET], before_over, before_ball, payload_json, created_at)`
- `decision_windows(id, match_id, opens_at, closes_at, target_type, target_ref)`
- `fan_decisions(id, user_id, window_id, payload_json, submitted_at)`
- `decision_scores(id, fan_decision_id, captain_move_id, merit_score, breakdown_json, computed_at)`
- `leaderboard_snapshot(scope [MATCH|SEASON|ALLTIME], scope_ref, user_id, total_score, rank, refreshed_at)`

JPA L2 cache (ehcache local) enabled on read-mostly reference tables: `users`, `matches`, `captain_moves`.

## 4. Tactical-Merit Scoring (Rules v1)

Each fan decision scored 0–100 along weighted dimensions, breakdown stored in `breakdown_json` for explainability:

- **Exact match with captain** (+50)
- **Historical economy/strike-rate fit**: lookup aggregate from `balls` table for `(bowler_type, batter_hand, over_phase)` — if fan's chosen bowler type has better historical economy in this phase than captain's, partial credit (+0–30)
- **Field placement coverage**: for chosen field, compute % of batter's historical wagon-wheel zones covered (+0–20)
- **Penalty** for illegal configs (e.g., >5 fielders on leg side outside circle) — reject at submit time

Rules live in `scoring/rules/` as small Java classes implementing `Rule { Score apply(Context ctx) }`, summed by `TacticalMeritEngine`. Easy to unit test and to swap with `MlScoringStrategy` later.

## 5. Real-Time Decision Flow

```mermaid
sequenceDiagram
  participant Ingest as Ingestion
  participant API as Spring Boot
  participant Fan as Angular Client
  Ingest->>API: New ball event + open decision window for next ball
  API->>Fan: STOMP /topic/match.{id} (window opened, T-15s)
  Fan->>API: POST /api/decisions (payload)
  API->>API: validate window, persist
  Ingest->>API: captain_move recorded for that ball
  API->>API: TacticalMeritEngine.score(fanDecision, captainMove)
  API->>Fan: STOMP /user/queue/decision-result (score + breakdown)
  API->>API: update leaderboard snapshot (async)
```

## 6. Deployment Strategy

All Docker and deployment configuration is consolidated under a single top-level `devops/` folder. Application `Dockerfile`s stay co-located with their source (`backend/Dockerfile`, `frontend/Dockerfile`) so the build context stays minimal — compose files in `devops/` reference them via `build.context: ../backend` etc.

Compose files (base + overlays):

- `devops/docker-compose.yml` (base): `postgres`, `backend`, `frontend`, `traefik`
- `devops/docker-compose.dev.yml`: hot-reload via Spring DevTools + `ng serve` proxied through Traefik; Traefik dashboard exposed; provider=`mock`
- `devops/docker-compose.prod.yml`: built JAR + nginx-served Angular bundle; Traefik with Let's Encrypt (`certificatesresolvers.le.acme`); provider=`external`; restart policies; healthchecks; resource limits

Run commands (from repo root):

```bash
docker compose -f devops/docker-compose.yml -f devops/docker-compose.dev.yml --env-file devops/.env.dev up
docker compose -f devops/docker-compose.yml -f devops/docker-compose.prod.yml --env-file devops/.env.prod up -d
```

Traefik routing (labels on services in compose files, dynamic TLS in `devops/traefik/dynamic/`):

- `Host(\`coachsim.example.com\`) && PathPrefix(\`/api\`)` → backend
- `Host(\`coachsim.example.com\`) && PathPrefix(\`/ws\`)`→ backend (sticky session via`loadbalancer.sticky.cookie`)
- `Host(\`coachsim.example.com\`)` → frontend (catch-all)

Operational concerns:

- **Migrations**: Flyway in backend (`db/migration/V1__init.sql`, etc.) — runs on startup.
- **Config**: Spring profiles `dev` / `prod`; secrets (DB pwd, JWT signing key, external API key) injected via env files (`devops/.env.dev`, `devops/.env.prod`, gitignored) with `.example` templates checked in.
- **Observability**: Spring Boot Actuator + Micrometer Prometheus endpoint; logs to stdout (JSON via Logback) for Docker log driver.
- **Healthchecks**: `/actuator/health` for backend, `pg_isready` for db, Traefik `ping` for itself.
- **Backups**: nightly `pg_dump` sidecar (`devops/backups/pg-dump.sh`) writing to a mounted volume.
- **Helper scripts**: `devops/scripts/up-dev.{sh,ps1}`, `up-prod.{sh,ps1}`, `deploy.sh` wrap the long `docker compose` invocations.
- **Horizontal scale path**: bump backend replicas in compose; switch STOMP simple-broker to RabbitMQ STOMP relay (already abstracted in `WebSocketConfig`); Traefik sticky cookie handles WS affinity.

## 7. Repository Layout

```
/
├─ backend/                          Spring Boot (Java 21, Gradle)
│  ├─ src/main/java/com/coachsim/...
│  ├─ src/main/resources/db/migration/
│  └─ Dockerfile                     (kept next to source for clean build context)
├─ frontend/                         Angular 17+ (standalone components)
│  ├─ src/app/...
│  └─ Dockerfile                     (multi-stage build -> nginx)
├─ devops/                           ALL docker + deployment config lives here
│  ├─ docker-compose.yml             base stack
│  ├─ docker-compose.dev.yml         dev overlay (hot reload, mock provider, dashboard)
│  ├─ docker-compose.prod.yml        prod overlay (Let's Encrypt, healthchecks, limits)
│  ├─ .env.dev.example               template for dev env vars
│  ├─ .env.prod.example              template for prod env vars
│  ├─ traefik/
│  │  ├─ traefik.yml                 static config (entrypoints, providers, ACME)
│  │  └─ dynamic/
│  │     └─ tls.yml                  TLS options + middlewares (headers, rate limit)
│  ├─ postgres/
│  │  └─ init.sql                    optional bootstrap (roles, extensions)
│  ├─ backups/
│  │  └─ pg-dump.sh                  nightly pg_dump sidecar script
│  └─ scripts/
│     ├─ up-dev.sh / up-dev.ps1      wrap `docker compose -f ... up`
│     ├─ up-prod.sh / up-prod.ps1
│     └─ deploy.sh                   pull, migrate, rollout, smoke-check
└─ README.md
```

## 8. Out of Scope for v1 (called out so we don't bloat MVP)

- ML-based scoring (interface ready, impl later)
- Push notifications / mobile apps
- Payment / cash rewards (leaderboard + badges only)
- Multi-region HA, Kubernetes
