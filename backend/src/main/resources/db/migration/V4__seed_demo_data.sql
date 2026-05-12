-- =============================================================
-- Demo seed data — populates the UI on a fresh DB so the leaderboard,
-- profile pages, and live screen all have content out of the box.
--
-- Passwords are hashed with pgcrypto's BCrypt.
--   All fan accounts:  fan1234
--   Admin account   :  admin123   (seeded in V2__seed_admin.sql)
-- =============================================================

-- Defensive: enable pgcrypto here too (init.sql may not have run on pre-existing volumes).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

------------------------------------------------------------------
-- 1) Fan roster
------------------------------------------------------------------

INSERT INTO users (email, password_hash, display_name, role) VALUES
  ('rohit@coachsim.local',   crypt('fan1234', gen_salt('bf', 10)), 'Rohit S',        'ROLE_USER'),
  ('virat@coachsim.local',   crypt('fan1234', gen_salt('bf', 10)), 'Virat K',        'ROLE_USER'),
  ('msd@coachsim.local',     crypt('fan1234', gen_salt('bf', 10)), 'MS D',           'ROLE_USER'),
  ('hardik@coachsim.local',  crypt('fan1234', gen_salt('bf', 10)), 'Hardik P',       'ROLE_USER'),
  ('shubman@coachsim.local', crypt('fan1234', gen_salt('bf', 10)), 'Shubman G',      'ROLE_USER'),
  ('jadeja@coachsim.local',  crypt('fan1234', gen_salt('bf', 10)), 'Ravindra J',     'ROLE_USER'),
  ('iyer@coachsim.local',    crypt('fan1234', gen_salt('bf', 10)), 'Shreyas I',      'ROLE_USER'),
  ('kuldeep@coachsim.local', crypt('fan1234', gen_salt('bf', 10)), 'Kuldeep Y',      'ROLE_USER'),
  ('bumrah@coachsim.local',  crypt('fan1234', gen_salt('bf', 10)), 'Jasprit B',      'ROLE_USER'),
  ('arshdeep@coachsim.local',crypt('fan1234', gen_salt('bf', 10)), 'Arshdeep S',     'ROLE_USER')
ON CONFLICT (email) DO NOTHING;

------------------------------------------------------------------
-- 2) A completed historical match with full ball-by-ball, captain
--    moves, fan decisions, and merit scores.
------------------------------------------------------------------

INSERT INTO matches (external_id, season, home_team, away_team, venue, status, source, starts_at)
VALUES ('DEMO-COMPLETED-001', '2025', 'Mumbai Indians', 'Chennai Super Kings',
        'Wankhede Stadium', 'COMPLETED', 'MOCK', NOW() - INTERVAL '3 days');

INSERT INTO innings (match_id, number, batting_team, bowling_team)
SELECT id, 1, 'Mumbai Indians', 'Chennai Super Kings' FROM matches WHERE external_id = 'DEMO-COMPLETED-001';
INSERT INTO innings (match_id, number, batting_team, bowling_team)
SELECT id, 2, 'Chennai Super Kings', 'Mumbai Indians' FROM matches WHERE external_id = 'DEMO-COMPLETED-001';

-- Synthetic ball-by-ball, captain moves, decisions, and scores for the
-- completed match. Wrapped in DO so we can grab generated IDs cleanly.
DO $$
DECLARE
  v_match_id BIGINT;
  v_inn1_id  BIGINT;
  v_inn2_id  BIGINT;
  v_move_id  BIGINT;
  v_window_id BIGINT;
  v_decision_id BIGINT;
  v_user_id  BIGINT;
  v_score    INT;
  v_over     INT;
  v_ball     INT;
  v_runs     INT;
BEGIN
  SELECT id INTO v_match_id FROM matches WHERE external_id = 'DEMO-COMPLETED-001';
  SELECT id INTO v_inn1_id  FROM innings WHERE match_id = v_match_id AND number = 1;
  SELECT id INTO v_inn2_id  FROM innings WHERE match_id = v_match_id AND number = 2;

  ----------------------------------------------------------------
  -- Powerplay (overs 1-6, innings 1) — pace bowlers
  ----------------------------------------------------------------
  FOR v_over IN 1..6 LOOP
    FOR v_ball IN 1..6 LOOP
      v_runs := (v_over * 13 + v_ball * 7) % 7;  -- deterministic-looking variation
      INSERT INTO balls (innings_id, over_num, ball_in_over, bowler, bowler_type,
                         batter, batter_hand, runs, extras, over_phase)
      VALUES (v_inn1_id, v_over, v_ball,
              CASE WHEN v_over % 2 = 0 THEN 'D. Chahar' ELSE 'M. Pathirana' END, 'PACE',
              CASE WHEN v_ball <= 3 THEN 'R. Sharma' ELSE 'I. Kishan' END, 'RIGHT',
              v_runs, 0, 'POWERPLAY');
    END LOOP;
  END LOOP;

  ----------------------------------------------------------------
  -- Middle overs (7-15) — spin
  ----------------------------------------------------------------
  FOR v_over IN 7..15 LOOP
    FOR v_ball IN 1..6 LOOP
      v_runs := (v_over + v_ball * 2) % 5;
      INSERT INTO balls (innings_id, over_num, ball_in_over, bowler, bowler_type,
                         batter, batter_hand, runs, extras, over_phase, wicket)
      VALUES (v_inn1_id, v_over, v_ball,
              CASE WHEN v_over % 2 = 0 THEN 'R. Jadeja' ELSE 'M. Ali' END, 'SPIN',
              CASE WHEN v_ball <= 4 THEN 'S. Tilak Varma' ELSE 'T. Stubbs' END, 'LEFT',
              v_runs, 0, 'MIDDLE',
              (v_over = 13 AND v_ball = 4));
    END LOOP;
  END LOOP;

  ----------------------------------------------------------------
  -- Death overs (16-20) — pace
  ----------------------------------------------------------------
  FOR v_over IN 16..20 LOOP
    FOR v_ball IN 1..6 LOOP
      v_runs := (v_over * 3 + v_ball) % 7;
      INSERT INTO balls (innings_id, over_num, ball_in_over, bowler, bowler_type,
                         batter, batter_hand, runs, extras, over_phase)
      VALUES (v_inn1_id, v_over, v_ball, 'M. Pathirana', 'PACE',
              CASE WHEN v_ball <= 3 THEN 'H. Pandya' ELSE 'T. David' END, 'RIGHT',
              v_runs, 0, 'DEATH');
    END LOOP;
  END LOOP;

  ----------------------------------------------------------------
  -- Captain moves: bowling change before over 7 (spin intro) and before over 16 (death pacer)
  ----------------------------------------------------------------
  INSERT INTO captain_moves (match_id, move_type, before_over, before_ball, payload_json)
  VALUES (v_match_id, 'BOWLING_CHANGE', 7, 1,
          jsonb_build_object('bowler', 'R. Jadeja', 'bowlerType', 'SPIN', 'batterHand', 'LEFT'))
  RETURNING id INTO v_move_id;

  -- Decision window for that move
  INSERT INTO decision_windows (match_id, target_type, target_over, target_ball,
                                opens_at, closes_at, captain_move_id, status)
  VALUES (v_match_id, 'BOWLING_CHANGE', 7, 1,
          NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days' + INTERVAL '15 seconds',
          v_move_id, 'RESOLVED')
  RETURNING id INTO v_window_id;

  -- A subset of fans submitted decisions for this window
  FOR v_user_id IN
    SELECT id FROM users WHERE role = 'ROLE_USER' ORDER BY id LIMIT 8
  LOOP
    INSERT INTO fan_decisions (user_id, window_id, payload_json)
    VALUES (v_user_id, v_window_id,
            jsonb_build_object(
              'bowler', CASE WHEN v_user_id % 2 = 0 THEN 'R. Jadeja' ELSE 'M. Ali' END,
              'bowlerType', 'SPIN'))
    RETURNING id INTO v_decision_id;

    -- Approximate score: exact-match bowler (50) + spin matches (30) = 80, else 55
    v_score := CASE WHEN v_user_id % 2 = 0 THEN 80 ELSE 55 END;

    INSERT INTO decision_scores (fan_decision_id, captain_move_id, merit_score, breakdown_json)
    VALUES (v_decision_id, v_move_id, v_score,
            jsonb_build_object(
              'totalPoints', v_score,
              'maxPoints', 100,
              'normalised', v_score,
              'rules', jsonb_build_array(
                jsonb_build_object('rule','exact_match','points',CASE WHEN v_score = 80 THEN 50 ELSE 25 END,'maxPoints',50,'detail','Demo seed'),
                jsonb_build_object('rule','historical_economy','points',30,'maxPoints',30,'detail','Spin matched captain in middle overs')
              )));
  END LOOP;

  ----------------------------------------------------------------
  -- Second captain move: pace at death (over 16)
  ----------------------------------------------------------------
  INSERT INTO captain_moves (match_id, move_type, before_over, before_ball, payload_json)
  VALUES (v_match_id, 'BOWLING_CHANGE', 16, 1,
          jsonb_build_object('bowler', 'M. Pathirana', 'bowlerType', 'PACE', 'batterHand', 'RIGHT'))
  RETURNING id INTO v_move_id;

  INSERT INTO decision_windows (match_id, target_type, target_over, target_ball,
                                opens_at, closes_at, captain_move_id, status)
  VALUES (v_match_id, 'BOWLING_CHANGE', 16, 1,
          NOW() - INTERVAL '3 days' + INTERVAL '90 minutes',
          NOW() - INTERVAL '3 days' + INTERVAL '90 minutes 15 seconds',
          v_move_id, 'RESOLVED')
  RETURNING id INTO v_window_id;

  FOR v_user_id IN
    SELECT id FROM users WHERE role = 'ROLE_USER' ORDER BY id LIMIT 7
  LOOP
    INSERT INTO fan_decisions (user_id, window_id, payload_json)
    VALUES (v_user_id, v_window_id,
            jsonb_build_object(
              'bowler', CASE WHEN v_user_id % 3 = 0 THEN 'M. Pathirana' ELSE 'D. Chahar' END,
              'bowlerType', 'PACE'))
    RETURNING id INTO v_decision_id;

    v_score := CASE WHEN v_user_id % 3 = 0 THEN 90 ELSE 60 END;

    INSERT INTO decision_scores (fan_decision_id, captain_move_id, merit_score, breakdown_json)
    VALUES (v_decision_id, v_move_id, v_score,
            jsonb_build_object(
              'totalPoints', v_score, 'maxPoints', 100, 'normalised', v_score,
              'rules', jsonb_build_array(
                jsonb_build_object('rule','exact_match','points',CASE WHEN v_score = 90 THEN 50 ELSE 25 END,'maxPoints',50,'detail','Demo seed'),
                jsonb_build_object('rule','historical_economy','points',30,'maxPoints',30,'detail','Pace matched captain at death')
              )));
  END LOOP;

END $$;

------------------------------------------------------------------
-- 3) A currently LIVE demo match so the live screen has content.
--    Six balls already bowled; the auto-play simulator can push more.
------------------------------------------------------------------

INSERT INTO matches (external_id, season, home_team, away_team, venue, status, source, starts_at)
VALUES ('DEMO-LIVE-001', '2026', 'Royal Challengers Bengaluru', 'Gujarat Titans',
        'M. Chinnaswamy Stadium', 'LIVE', 'MOCK', NOW() - INTERVAL '15 minutes');

INSERT INTO innings (match_id, number, batting_team, bowling_team)
SELECT id, 1, 'Royal Challengers Bengaluru', 'Gujarat Titans' FROM matches WHERE external_id = 'DEMO-LIVE-001';
INSERT INTO innings (match_id, number, batting_team, bowling_team)
SELECT id, 2, 'Gujarat Titans', 'Royal Challengers Bengaluru' FROM matches WHERE external_id = 'DEMO-LIVE-001';

DO $$
DECLARE
  v_inn_id BIGINT;
BEGIN
  SELECT i.id INTO v_inn_id
  FROM innings i JOIN matches m ON m.id = i.match_id
  WHERE m.external_id = 'DEMO-LIVE-001' AND i.number = 1;

  INSERT INTO balls (innings_id, over_num, ball_in_over, bowler, bowler_type, batter, batter_hand, runs, extras, over_phase) VALUES
    (v_inn_id, 1, 1, 'Mohammed Shami', 'PACE', 'F. du Plessis', 'RIGHT', 1, 0, 'POWERPLAY'),
    (v_inn_id, 1, 2, 'Mohammed Shami', 'PACE', 'V. Kohli',      'RIGHT', 4, 0, 'POWERPLAY'),
    (v_inn_id, 1, 3, 'Mohammed Shami', 'PACE', 'V. Kohli',      'RIGHT', 0, 0, 'POWERPLAY'),
    (v_inn_id, 1, 4, 'Mohammed Shami', 'PACE', 'V. Kohli',      'RIGHT', 2, 0, 'POWERPLAY'),
    (v_inn_id, 1, 5, 'Mohammed Shami', 'PACE', 'V. Kohli',      'RIGHT', 6, 0, 'POWERPLAY'),
    (v_inn_id, 1, 6, 'Mohammed Shami', 'PACE', 'V. Kohli',      'RIGHT', 1, 0, 'POWERPLAY');
END $$;

------------------------------------------------------------------
-- 4) Force an immediate leaderboard refresh by leaving the snapshot
--    table empty — LeaderboardService re-populates on its next tick (60s).
------------------------------------------------------------------
