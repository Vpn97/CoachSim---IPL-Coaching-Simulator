-- Seed a small synthetic historical-stats match so the scoring engine
-- has data to compute economy/strike-rate aggregates against from day one.
-- This is a tiny synthetic seed; real production seeds a multi-season dump.

INSERT INTO matches (external_id, season, home_team, away_team, venue, status, source)
VALUES ('HIST-001', '2024', 'Mumbai Indians', 'Chennai Super Kings', 'Wankhede', 'COMPLETED', 'MOCK');

INSERT INTO innings (match_id, number, batting_team, bowling_team)
SELECT id, 1, 'Chennai Super Kings', 'Mumbai Indians' FROM matches WHERE external_id = 'HIST-001';

INSERT INTO innings (match_id, number, batting_team, bowling_team)
SELECT id, 2, 'Mumbai Indians', 'Chennai Super Kings' FROM matches WHERE external_id = 'HIST-001';

-- A pinch of historical balls per phase x bowler type x batter hand so the
-- TacticalMeritEngine has non-empty aggregates. Real load comes via ingestion.
DO $$
DECLARE
  inn_id BIGINT;
  i INT;
BEGIN
  SELECT id INTO inn_id FROM innings WHERE match_id = (SELECT id FROM matches WHERE external_id = 'HIST-001') AND number = 1;

  FOR i IN 1..6 LOOP
    INSERT INTO balls (innings_id, over_num, ball_in_over, bowler, bowler_type, batter, batter_hand, runs, over_phase)
    VALUES (inn_id, 1, i, 'J. Bumrah', 'PACE', 'R. Gaikwad', 'RIGHT', (i % 3), 'POWERPLAY');
  END LOOP;

  FOR i IN 1..6 LOOP
    INSERT INTO balls (innings_id, over_num, ball_in_over, bowler, bowler_type, batter, batter_hand, runs, over_phase)
    VALUES (inn_id, 10, i, 'P. Chawla', 'SPIN', 'R. Gaikwad', 'RIGHT', (i % 4), 'MIDDLE');
  END LOOP;

  FOR i IN 1..6 LOOP
    INSERT INTO balls (innings_id, over_num, ball_in_over, bowler, bowler_type, batter, batter_hand, runs, over_phase)
    VALUES (inn_id, 18, i, 'J. Bumrah', 'PACE', 'M. Dhoni', 'RIGHT', (i % 5) + 1, 'DEATH');
  END LOOP;
END $$;
