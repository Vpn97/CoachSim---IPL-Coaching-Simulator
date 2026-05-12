# CoachSim — IPL Coaching Simulator

A real-time fan-engagement platform that turns watching cricket into a tactical
co-pilot experience. During a live IPL match, fans make the same calls the
captain has to make — **bowling changes** between balls and **field placements**
around the ground — submit them inside a short decision window, and within
seconds get a per-ball score that rates the tactical merit of their choice
against the captain's actual move and historical match data.

Goal: surface the most knowledgeable cricket minds in the country and reward
them on a leaderboard, while giving casual fans a "what would I have done?"
loop on every single ball.

---

## 🔴 Live demo

| What             | URL                                                   |
| ---------------- | ----------------------------------------------------- |
| **App**          | https://coachsim.apkzube.com                          |
| Health           | https://coachsim.apkzube.com/actuator/health          |

> Demo accounts (seeded by Flyway on first boot):
>
> | Role  | Email                       | Password   |
> | ----- | --------------------------- | ---------- |
> | Admin | `admin@coachsim.local`      | `admin123` |
> | Fan   | `rohit@coachsim.local`      | `fan1234`  |
> | Fan   | `virat@coachsim.local`      | `fan1234`  |
> | Fan   | `msd@coachsim.local`        | `fan1234`  |
> | Fan   | `bumrah@coachsim.local`     | `fan1234`  |
>
> Eleven fan accounts in total — pick any of `rohit / virat / msd / hardik / shubman / jadeja / iyer / kuldeep / bumrah / arshdeep` at `@coachsim.local` with password `fan1234`.

---

## How the demo runs (no real IPL fixture required)

The demo doesn't depend on there being an actual live IPL match at the time of
viewing — an **auto-play simulator** baked into the backend can drive a seeded
"LIVE" match end-to-end. The simulator emits a synthetic ball every few
seconds plus a captain move between balls, so the live page behaves exactly
like a real broadcast.

Two simultaneous browser windows tell the full story: the admin drives the
simulation, the fan plays along.

### 🎬 Admin flow (drive the simulation)

1. Open the live URL and sign in as **`admin@coachsim.local` / `admin123`**.
2. Click **Admin** in the top nav. The page shows every LIVE match with an
   **Auto-play** badge that auto-polls the backend every 5 s — so you can see
   at a glance which matches are already simulating (no risk of starting two
   loops on the same match).
3. Pick a live match (e.g. **RCB vs GT** or **MI vs Chennai**) and click
   **Control**.
4. Under *Auto-play simulation* set **Ball every (s)** (5 s is a good demo
   pace — gives fans enough time to tap their decision) and hit
   **Start auto-play**. The badge flips to **RUNNING**.
5. The simulator now, every `ballEverySeconds`:
   - Publishes the **captain's move** for the upcoming ball — bowling change
     and field set, picked phase-aware (pace in powerplay/death, spin in
     middle), with a 30 % "surprise" rotation so the verdict varies.
   - Ingests the synthetic **ball** (weighted dot/single/four/six
     distribution, occasional wickets). This opens a fresh decision window
     for the *next* ball, giving every connected fan one full tick to react.
6. If you ever want to replay a match from scratch, hit **Reset cursor** —
   it wipes the ball history, decision windows, fan decisions and scores
   for that match in FK-safe order so the scoreboard restarts at 0/0 over 1.
7. Click **Stop** to pause simulation at any time.

### 🏏 Fan flow (play along)

In a second window (incognito or another browser), sign in as any fan
account, e.g. **`rohit@coachsim.local` / `fan1234`**.

1. The **Live** page auto-selects the running match. The left sidebar lists
   every live fixture; the main column shows the scoreboard, the last ball,
   and a *Recent balls* strip (last six balls with run/wicket chips).
2. A **sticky score banner** at the top pins your running tactical merit for
   this match (total, decisions scored / pending, average, best, last score
   + rule breakdown). The number flashes a green **+N** the instant a new
   score arrives over WebSocket.
3. When the simulator opens a decision window, the **Decision Panel** card
   appears below the scoreboard with:
   - A circular **SVG countdown timer**.
   - A row of one-tap **bowler chips** (named IPL bowlers, pace/spin/medium
     icons) for a **bowling change**.
   - A visual **cricket ground** for a **field set** — tap any zone (covers,
     point, mid-off, mid-wicket, …) to place a fielder; quick-select
     formations (Powerplay / Attacking / Defensive / Death) drop in 8
     fielders in one tap, then tweak.
   - A big sticky **Submit** button at the top of the card so it never
     scrolls out of view under time pressure.
4. Hit **Submit before the timer runs out**. The decision is `POST`ed to
   `/api/decisions` and stored against the open window.
5. On the next simulator tick (≈ `ballEverySeconds` later), the captain's
   move for that ball is published → the window closes → the rules-based
   scoring engine compares your decision to the captain's move and to
   historical match data, producing a score with a transparent per-rule
   breakdown:
   - **Exact match** (up to 50 pts) — same bowler / same bowler type.
   - **Historical economy** (up to 30 pts) — does your bowler type
     historically beat this batter-hand × phase combo?
   - **Field coverage** (up to 20 pts) — how many of the captain's
     "hot zones" did your field plug?
6. The result lands instantly on the running banner (no page refresh) and
   in the **Reveal** card below.
7. Repeat every ball. Your aggregate climbs in real time and you (or someone
   else) appears on the **Leaderboard** page (match / season / all-time).

### What the simulator avoids that real demos usually break on

- **Stale windows after a replay** — Reset wipes the full decision graph,
  not just balls, so the 20-over cap can be re-played indefinitely.
- **Captain move closing windows instantly** — the captain move for ball
  *N+1* is published in the *next* tick (not the same tick as ball *N*), so
  each window stays open for the entire countdown the fan sees.
- **Session lapses** — `/api/matches/{id}/state` is fully public, so the
  scoreboard renders even before sign-in or with an expired JWT; protected
  endpoints (401) auto-redirect to `/login`.

---

## Tech stack

| Layer            | Technology                                                                 |
| ---------------- | -------------------------------------------------------------------------- |
| **Backend**      | Spring Boot 3 · Java 21 · Gradle 8                                         |
| Persistence      | Spring Data JPA + Hibernate · Flyway · PostgreSQL 16 · Ehcache (JPA L2)    |
| Realtime         | Spring WebSocket + STOMP (`/ws`, topics `/topic/match.{id}`, user queues)  |
| AuthN/Z          | Spring Security · JWT (JJWT) · BCrypt · `ROLE_ADMIN` method-level guards   |
| Ingestion        | Pluggable `MatchDataProvider`: `mock` · `external` (CricAPI v2) · `auto-play` simulator |
| Scoring          | Rules engine — `ExactMatchRule` · `HistoricalEconomyRule` · `FieldCoverageRule` |
| Observability    | Spring Actuator · Micrometer + Prometheus · Logstash JSON logs             |
| **Frontend**     | Angular 17 (standalone components, signals) · TypeScript                   |
| UI               | Custom SVG cricket ground · sticky live banner · STOMP via `@stomp/rx-stomp` |
| **Infra**        | Docker · Docker Compose (base + dev / prod overlays)                       |
| Edge (prod)      | Traefik v2 (external instance) · Let's Encrypt HTTP-01 · sticky cookies for WS · inline rate-limit + security headers |
| Edge (dev)       | Frontend `nginx` is the single host entrypoint — proxies `/api` and `/ws` to the backend over the compose network |

---

## Architecture

```
                    ┌────────────────────────────────────────────────────┐
                    │                    Browser (Angular)                │
                    │  Live Page  Admin Page  Profile  Leaderboard       │
                    │     │           │          │          │             │
                    └─────┼───────────┼──────────┼──────────┼─────────────┘
                          │ HTTPS     │ HTTPS    │ HTTPS    │ WSS (STOMP)
                          ▼           ▼          ▼          ▼
                    ┌────────────────────────────────────────────────────┐
                    │                Traefik (prod)                       │
                    │     Host(coachsim.apkzube.com) · TLS · sticky WS    │
                    │  ─ Let's Encrypt · rate-limit · security headers ─  │
                    └────────────────────────────────────────────────────┘
                                       │
                ┌──────────────────────┼──────────────────────┐
                │                      │                      │
                ▼                      ▼                      ▼
       ┌───────────────┐   ┌────────────────────┐   ┌────────────────┐
       │  Frontend     │   │   Spring Boot      │   │   Spring Boot  │
       │  (nginx +     │   │   Backend  ×N      │   │   /ws  (STOMP) │
       │   Angular)    │   │   /api · /actuator │   │   simpleBroker │
       └───────────────┘   └─────────┬──────────┘   └────────┬───────┘
                                     │                       │
                                     ▼                       ▼
                          ┌─────────────────────────────────────────┐
                          │            PostgreSQL 16                 │
                          │  matches · innings · balls · captain_    │
                          │  moves · decision_windows · fan_decisions│
                          │  · decision_scores · leaderboard_*       │
                          └─────────────────────────────────────────┘
                                     ▲
                                     │ scheduled poll
                                     │ (when INGESTION_PROVIDER=external)
                          ┌─────────────────────────┐
                          │   CricAPI v2 / mock /   │
                          │   Auto-play simulator   │
                          └─────────────────────────┘
```

### Decision-window lifecycle (one ball)

```
   Tick T  ────────────────────────────────────────────────►  Tick T+ballEverySeconds
   │                                                          │
   │ ingest(ball X.Y)                                         │ captain move for X.Y+1
   │   └─► IngestionService                                   │   ├─► resolveWindow(X.Y+1)
   │         ├─► save Ball                                    │   ├─► async score against
   │         ├─► publish "BALL" to /topic/match.{id}          │   │      every fan_decision
   │         └─► openWindowForNextBall(X.Y+1)                 │   │      via rules engine
   │              ├─► save 2 DecisionWindow rows              │   ├─► save DecisionScore
   │              │      (BOWLING_CHANGE + FIELD_SET)         │   └─► publish per-user
   │              └─► publish "window-open" event             │          /user/queue/decision-result
   │                                                          │
   ▼                                                          ▼
   Fan sees countdown → places bowler/field → POST /api/decisions
   ─────────────────────────────────────────────────────────►
```

Key properties:

- **Per-ball scoring** — every ball produces a captain move, every captain
  move resolves the open window, every fan decision in that window is
  scored within seconds. No "wait until end of over".
- **Idempotent scoring** — replays don't double-score; `decision_scores` is
  unique-per-`fan_decision`, with a check before insert.
- **Sticky WebSocket sessions** — Traefik pins each fan to one backend
  replica for the lifetime of their STOMP session. Swap `simpleBroker` for
  a `stompBrokerRelay` (RabbitMQ) to break the stickiness limit at multi-node
  scale; no client changes required.
- **Replayable demos** — admin **Reset cursor** wipes balls + windows +
  decisions + scores for one match in FK-safe order so the same demo match
  can be replayed end-to-end as many times as needed.

### Deployment topology

- **Dev** — single host, three containers (`postgres`, `backend`,
  `frontend`). Frontend's nginx is the only thing bound to host port 80 and
  proxies `/api` + `/ws` to the backend over the compose network.
- **Prod** — same three containers + a `pg-backup` sidecar. Plugs into an
  **already-running Traefik v2** stack on the host via the external
  `traefik-network`. All TLS, ACME, redirects, rate-limits and security
  headers are handled in compose labels — no custom Traefik config files
  required on the host.
