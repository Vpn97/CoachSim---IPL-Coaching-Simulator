-- =============================================================
-- CoachSim — initial schema
-- =============================================================

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    role            VARCHAR(32)  NOT NULL DEFAULT 'ROLE_USER',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- -------------------------------------------------------------
-- Matches / Innings / Balls
-- -------------------------------------------------------------

CREATE TABLE matches (
    id              BIGSERIAL PRIMARY KEY,
    external_id     VARCHAR(64),
    season          VARCHAR(16)  NOT NULL,
    home_team       VARCHAR(64)  NOT NULL,
    away_team       VARCHAR(64)  NOT NULL,
    venue           VARCHAR(128),
    status          VARCHAR(32)  NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED | LIVE | COMPLETED
    source          VARCHAR(32)  NOT NULL DEFAULT 'MOCK',      -- MOCK | EXTERNAL
    starts_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (source, external_id)
);

CREATE INDEX idx_matches_status ON matches(status);
CREATE INDEX idx_matches_season ON matches(season);

CREATE TABLE innings (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    number          SMALLINT NOT NULL CHECK (number IN (1, 2)),
    batting_team    VARCHAR(64) NOT NULL,
    bowling_team    VARCHAR(64) NOT NULL,
    UNIQUE (match_id, number)
);

CREATE INDEX idx_innings_match ON innings(match_id);

CREATE TABLE balls (
    id              BIGSERIAL PRIMARY KEY,
    innings_id      BIGINT NOT NULL REFERENCES innings(id) ON DELETE CASCADE,
    over_num        SMALLINT NOT NULL,
    ball_in_over    SMALLINT NOT NULL,
    bowler          VARCHAR(120) NOT NULL,
    bowler_type     VARCHAR(32),                   -- PACE | SPIN | MEDIUM
    batter          VARCHAR(120) NOT NULL,
    batter_hand     VARCHAR(8),                    -- LEFT | RIGHT
    runs            SMALLINT NOT NULL DEFAULT 0,
    extras          SMALLINT NOT NULL DEFAULT 0,
    wicket          BOOLEAN  NOT NULL DEFAULT FALSE,
    wicket_type     VARCHAR(32),
    over_phase      VARCHAR(16),                   -- POWERPLAY | MIDDLE | DEATH
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (innings_id, over_num, ball_in_over)
);

CREATE INDEX idx_balls_innings ON balls(innings_id);
CREATE INDEX idx_balls_phase_bowler ON balls(over_phase, bowler_type);
CREATE INDEX idx_balls_phase_batter ON balls(over_phase, batter_hand);

-- -------------------------------------------------------------
-- Captain moves
-- -------------------------------------------------------------

CREATE TABLE captain_moves (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    move_type       VARCHAR(32) NOT NULL,          -- BOWLING_CHANGE | FIELD_SET
    before_over     SMALLINT NOT NULL,
    before_ball     SMALLINT NOT NULL,
    payload_json    JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_captain_moves_match ON captain_moves(match_id);
CREATE INDEX idx_captain_moves_position ON captain_moves(match_id, before_over, before_ball);

-- -------------------------------------------------------------
-- Decision windows + Fan decisions + Scores
-- -------------------------------------------------------------

CREATE TABLE decision_windows (
    id              BIGSERIAL PRIMARY KEY,
    match_id        BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    target_type     VARCHAR(32) NOT NULL,          -- BOWLING_CHANGE | FIELD_SET
    target_over     SMALLINT NOT NULL,
    target_ball     SMALLINT NOT NULL,
    opens_at        TIMESTAMPTZ NOT NULL,
    closes_at       TIMESTAMPTZ NOT NULL,
    captain_move_id BIGINT REFERENCES captain_moves(id) ON DELETE SET NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN | CLOSED | RESOLVED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (match_id, target_type, target_over, target_ball)
);

CREATE INDEX idx_windows_match ON decision_windows(match_id);
CREATE INDEX idx_windows_status ON decision_windows(status);

CREATE TABLE fan_decisions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    window_id       BIGINT NOT NULL REFERENCES decision_windows(id) ON DELETE CASCADE,
    payload_json    JSONB NOT NULL,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, window_id)
);

CREATE INDEX idx_fan_decisions_window ON fan_decisions(window_id);
CREATE INDEX idx_fan_decisions_user ON fan_decisions(user_id);

CREATE TABLE decision_scores (
    id                  BIGSERIAL PRIMARY KEY,
    fan_decision_id     BIGINT NOT NULL UNIQUE REFERENCES fan_decisions(id) ON DELETE CASCADE,
    captain_move_id     BIGINT REFERENCES captain_moves(id) ON DELETE SET NULL,
    merit_score         INTEGER NOT NULL,
    breakdown_json      JSONB NOT NULL,
    computed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_decision_scores_decision ON decision_scores(fan_decision_id);

-- -------------------------------------------------------------
-- Leaderboard snapshot (materialized)
-- -------------------------------------------------------------

CREATE TABLE leaderboard_snapshot (
    id              BIGSERIAL PRIMARY KEY,
    scope           VARCHAR(16) NOT NULL,          -- MATCH | SEASON | ALLTIME
    scope_ref       VARCHAR(64) NOT NULL,          -- match_id | season | 'ALL'
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name    VARCHAR(120) NOT NULL,
    total_score     BIGINT NOT NULL,
    decisions_count INTEGER NOT NULL,
    rank            INTEGER NOT NULL,
    refreshed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (scope, scope_ref, user_id)
);

CREATE INDEX idx_leaderboard_scope ON leaderboard_snapshot(scope, scope_ref, rank);
