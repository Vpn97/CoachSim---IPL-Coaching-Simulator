package com.coachsim.admin;

import com.coachsim.decision.DecisionScoreRepository;
import com.coachsim.decision.DecisionWindow;
import com.coachsim.decision.DecisionWindowRepository;
import com.coachsim.decision.FanDecision;
import com.coachsim.decision.FanDecisionRepository;
import com.coachsim.ingestion.IngestionService;
import com.coachsim.ingestion.MatchEvent;
import com.coachsim.match.Ball;
import com.coachsim.match.BallRepository;
import com.coachsim.match.CaptainMove;
import com.coachsim.match.Innings;
import com.coachsim.match.InningsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Auto-plays a mock match by pushing synthetic ball events at a fixed interval,
 * plus a captain move every couple of overs.
 *
 * <p>Designed for demos and for situations where the configured external API
 * doesn't expose true ball-by-ball (CricAPI free tier, for example).
 */
@Service
public class AutoPlaySimulator {

    private static final Logger log = LoggerFactory.getLogger(AutoPlaySimulator.class);

    /** Hard cap on simulated overs per innings, after which the loop stops. */
    private static final int MAX_OVERS = 20;

    private static final String[] PACE_BOWLERS = {"J. Bumrah", "Mohammed Shami", "M. Pathirana", "D. Chahar", "U. Yadav"};
    private static final String[] SPIN_BOWLERS = {"R. Jadeja", "Y. Chahal", "K. Yadav", "R. Ashwin"};
    private static final String[] BATTERS_R = {"V. Kohli", "R. Sharma", "S. Iyer", "S. Gill", "M. Dhoni"};
    private static final String[] BATTERS_L = {"R. Jaiswal", "I. Kishan", "T. Stubbs", "S. Tilak Varma"};

    private final TaskScheduler scheduler;
    private final IngestionService ingestion;
    private final InningsRepository innings;
    private final BallRepository balls;
    private final DecisionWindowRepository windows;
    private final FanDecisionRepository fanDecisions;
    private final DecisionScoreRepository scores;

    /** Active autoplay sessions, keyed by matchId. */
    private final Map<Long, ScheduledFuture<?>> running = new ConcurrentHashMap<>();
    private final Map<Long, SimState> state = new ConcurrentHashMap<>();

    public AutoPlaySimulator(TaskScheduler scheduler,
                             IngestionService ingestion,
                             InningsRepository innings,
                             BallRepository balls,
                             DecisionWindowRepository windows,
                             FanDecisionRepository fanDecisions,
                             DecisionScoreRepository scores) {
        this.scheduler = scheduler;
        this.ingestion = ingestion;
        this.innings = innings;
        this.balls = balls;
        this.windows = windows;
        this.fanDecisions = fanDecisions;
        this.scores = scores;
    }

    public boolean isRunning(long matchId) {
        return running.containsKey(matchId);
    }

    public synchronized void start(long matchId, int ballEverySeconds) {
        if (running.containsKey(matchId)) {
            log.info("AutoPlay already running for match {}", matchId);
            return;
        }

        List<Innings> matchInnings = innings.findByMatchIdOrderByNumberAsc(matchId);
        if (matchInnings.isEmpty()) {
            throw new IllegalArgumentException("Match " + matchId + " has no innings");
        }

        SimState init = state.computeIfAbsent(matchId, id -> resumeFromDb(matchId, matchInnings.get(0)));

        // Auto-wrap when the resume cursor is at or past the cap so the demo is
        // endlessly replayable. Without this, repeated demos accumulate balls
        // past over 20 in the DB and every subsequent Start would resume past
        // the cap, fire one tick, see overNum > MAX_OVERS, and immediately stop
        // — producing the "click does nothing, status stays idle" symptom.
        //
        // Wrapping wipes both the existing balls (to avoid the
        // (innings_id, over_num, ball_in_over) unique constraint blowing up
        // on every replayed ball) and the entire decision window / fan
        // decision / score history for the match (otherwise stale OPEN
        // windows from prior runs short-circuit user submissions with a 409
        // and previously-scored fan_decisions trigger duplicate-key errors
        // when their window is re-resolved).
        if (init.overNum >= MAX_OVERS) {
            int deletedBalls = balls.deleteByInningsId(matchInnings.get(0).getId());
            int deletedWindows = wipeDecisionGraphForMatch(matchId);
            log.info("AutoPlay state for match {} was at over {}.{} (past {}-over cap); wiped {} balls + {} windows and wrapping to 1.1 for replay",
                    matchId, init.overNum, init.ballInOver, MAX_OVERS, deletedBalls, deletedWindows);
            init.overNum = 1;
            init.ballInOver = 0;
        }

        log.info("AutoPlay starting for match {} from over {}.{} (interval={}s)",
                matchId, init.overNum, init.ballInOver, ballEverySeconds);

        Duration period = Duration.ofSeconds(Math.max(2, ballEverySeconds));
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> safeTick(matchId), period);
        running.put(matchId, task);
    }

    /**
     * Clears in-memory simulator state AND wipes the entire per-match demo
     * graph so the next Start replays from a fresh 0/0 scoreboard:
     *   - balls in innings 1 (otherwise unique-constraint collisions),
     *   - decision_scores (otherwise duplicate-key on fan_decision_id),
     *   - fan_decisions, and
     *   - decision_windows (otherwise stale OPEN windows whose closesAt is
     *     in the past hand back 409 Conflict on every fan submission).
     */
    public synchronized void resetState(long matchId) {
        state.remove(matchId);
        List<Innings> matchInnings = innings.findByMatchIdOrderByNumberAsc(matchId);
        if (matchInnings.isEmpty()) {
            log.info("AutoPlay in-memory state cleared for match {} (no innings to wipe)", matchId);
            return;
        }
        int deletedBalls = balls.deleteByInningsId(matchInnings.get(0).getId());
        int deletedWindows = wipeDecisionGraphForMatch(matchId);
        log.info("AutoPlay reset for match {}: cleared in-memory state + wiped {} balls + {} windows (and dependents)",
                matchId, deletedBalls, deletedWindows);
    }

    /**
     * Cascade-deletes decision_scores -> fan_decisions -> decision_windows
     * for one match in FK-safe order. Returns the number of windows removed.
     */
    private int wipeDecisionGraphForMatch(long matchId) {
        List<DecisionWindow> matchWindows = windows.findByMatchId(matchId);
        if (matchWindows.isEmpty()) return 0;
        List<Long> windowIds = matchWindows.stream().map(DecisionWindow::getId).toList();

        List<FanDecision> matchDecisions = windowIds.stream()
                .flatMap(id -> fanDecisions.findByWindowId(id).stream())
                .toList();
        if (!matchDecisions.isEmpty()) {
            List<Long> decisionIds = matchDecisions.stream().map(FanDecision::getId).toList();
            scores.deleteByFanDecisionIdIn(decisionIds);
            fanDecisions.deleteByWindowIdIn(windowIds);
        }
        return windows.deleteByMatchId(matchId);
    }

    public synchronized void stop(long matchId) {
        ScheduledFuture<?> task = running.remove(matchId);
        if (task != null) {
            task.cancel(false);
            log.info("AutoPlay stopped for match {}", matchId);
        }
    }

    private void safeTick(long matchId) {
        try {
            tick(matchId);
        } catch (Exception ex) {
            log.error("AutoPlay tick failed for match {}: {}", matchId, ex.getMessage(), ex);
        }
    }

    private void tick(long matchId) {
        SimState s = state.get(matchId);
        if (s == null) return;

        // Roll forward: next ball
        s.ballInOver++;
        if (s.ballInOver > 6) {
            s.ballInOver = 1;
            s.overNum++;
        }

        if (s.overNum > MAX_OVERS) {
            log.info("AutoPlay finished {} overs for match {} — stopping", MAX_OVERS, matchId);
            stop(matchId);
            return;
        }

        Ball.OverPhase phase = Ball.phaseForOver(s.overNum);
        boolean usePace = phase != Ball.OverPhase.MIDDLE;
        Ball.BowlerType bowlerType = usePace ? Ball.BowlerType.PACE : Ball.BowlerType.SPIN;
        String bowler = usePace
                ? PACE_BOWLERS[s.overNum % PACE_BOWLERS.length]
                : SPIN_BOWLERS[s.overNum % SPIN_BOWLERS.length];
        boolean leftHand = s.ballInOver % 3 == 0;
        Ball.BatterHand hand = leftHand ? Ball.BatterHand.LEFT : Ball.BatterHand.RIGHT;
        String batter = (leftHand ? BATTERS_L : BATTERS_R)[s.ballInOver % (leftHand ? BATTERS_L.length : BATTERS_R.length)];

        int runs = chooseRuns(s);
        boolean wicket = s.rng.nextInt(60) == 0;
        if (wicket) runs = 0;

        // ORDER MATTERS — captain move comes BEFORE the ball it relates to.
        //
        // Previously both the ball and the next ball's captain move were
        // ingested in the same tick: the ball opened a window for (over, ball+1)
        // and the captain move immediately CLOSED that same window — leaving
        // every window the user saw on the UI already closed in the DB, so
        // every submission returned 409 Conflict.
        //
        // The correct cricket model is: between balls the captain sets the
        // field/bowler, then the bowler bowls. So we resolve the previously-
        // opened window FIRST (publishing the captain move for THIS ball),
        // and then ingest THIS ball — which opens the next window for the user
        // to consider until the next tick fires. Net effect: each window stays
        // open for one full `ballEverySeconds` interval, which is exactly the
        // time budget the user expects from the on-screen countdown.
        //
        // The very first ball of a fresh simulation has no prior window, so
        // skip publishing a captain move for it.
        boolean isFirstBall = s.overNum == 1 && s.ballInOver == 1;
        if (!isFirstBall) {
            publishCaptainMoves(matchId, (short) s.overNum, (short) s.ballInOver, bowler, bowlerType, hand, s);
        }

        ingestion.ingest(MatchEvent.builder()
                .type(MatchEvent.Type.BALL)
                .matchId(matchId)
                .inningsNumber((short) 1)
                .overNum((short) s.overNum)
                .ballInOver((short) s.ballInOver)
                .ball(new MatchEvent.BallPayload(
                        bowler, bowlerType,
                        batter, hand,
                        runs, 0, wicket, wicket ? "BOWLED" : null))
                .build());
    }

    /**
     * Publish one BOWLING_CHANGE and one FIELD_SET captain move for the
     * given (over, ball) — this is the ball that's ABOUT to be bowled. The
     * caller is expected to ingest the actual ball event immediately after,
     * inside the same tick. The captain move resolves the decision window
     * the user opened during the previous tick.
     *
     * The "captain" is intentionally not always identical to what the bowler
     * actually was in the previous ball — we rotate by phase + a small random
     * chance so the rules engine produces meaningfully different verdicts
     * across consecutive deliveries (instead of always agreeing with the fan).
     */
    private void publishCaptainMoves(long matchId, short over, short ball,
                                     String upcomingBowler, Ball.BowlerType upcomingType,
                                     Ball.BatterHand hand, SimState s) {
        Ball.OverPhase phase = Ball.phaseForOver(over);
        Ball.BowlerType captainBowlerType;
        String captainBowler;

        // Phase-aware captain choice: pace in powerplay/death, spin in middle.
        // 30% of the time the captain "rotates" (different type) to surprise the fan.
        boolean rotate = s.rng.nextInt(10) < 3;
        boolean wantPace = phase != Ball.OverPhase.MIDDLE;
        if (rotate) wantPace = !wantPace;
        captainBowlerType = wantPace ? Ball.BowlerType.PACE : Ball.BowlerType.SPIN;
        captainBowler = wantPace
                ? PACE_BOWLERS[(over + ball) % PACE_BOWLERS.length]
                : SPIN_BOWLERS[(over + ball) % SPIN_BOWLERS.length];

        ingestion.ingest(MatchEvent.builder()
                .type(MatchEvent.Type.CAPTAIN_MOVE)
                .matchId(matchId)
                .overNum(over)
                .ballInOver(ball)
                .captain(new MatchEvent.CaptainPayload(
                        CaptainMove.MoveType.BOWLING_CHANGE,
                        Map.of(
                                "bowler", captainBowler,
                                "bowlerType", captainBowlerType.name(),
                                "batterHand", hand.name()
                        )))
                .build());

        // Captain's field — a small set of "hot zones" the rules engine can
        // compare fan coverage against. Phase-aware: powerplay defends covers/point,
        // middle defends mid-wicket/long-on, death defends straight + leg-deep.
        List<String> hotZones = switch (phase) {
            case POWERPLAY -> List.of("COVERS", "POINT", "MID_OFF", "MID_WICKET");
            case MIDDLE    -> List.of("MID_WICKET", "MID_ON", "COVERS", "SQUARE_LEG");
            case DEATH     -> List.of("MID_OFF", "MID_ON", "FINE_LEG", "MID_WICKET", "THIRD_MAN");
        };
        ingestion.ingest(MatchEvent.builder()
                .type(MatchEvent.Type.CAPTAIN_MOVE)
                .matchId(matchId)
                .overNum(over)
                .ballInOver(ball)
                .captain(new MatchEvent.CaptainPayload(
                        CaptainMove.MoveType.FIELD_SET,
                        Map.of(
                                "batterHand", hand.name(),
                                "batterTopZones", hotZones,
                                "phase", phase.name()
                        )))
                .build());
    }

    private int chooseRuns(SimState s) {
        // Weighted dot/single/four/six distribution
        int roll = s.rng.nextInt(100);
        if (roll < 35) return 0;
        if (roll < 70) return 1;
        if (roll < 80) return 2;
        if (roll < 92) return 4;
        if (roll < 98) return 6;
        return 3;
    }

    private SimState resumeFromDb(long matchId, Innings firstInnings) {
        SimState s = new SimState();
        Optional<Ball> last = balls.findFirstByInningsIdOrderByOverNumDescBallInOverDesc(firstInnings.getId());
        // For an empty innings we default the cursor to (over=1, ball=0) so
        // the very first tick increments to (1, 1) — a real cricket over.
        // Previously the (0, 0) default produced balls labelled "over 0.X"
        // which surfaced on the scoreboard.
        s.overNum = last.map(Ball::getOverNum).orElse((short) 1);
        s.ballInOver = last.map(Ball::getBallInOver).orElse((short) 0);
        return s;
    }

    private static final class SimState {
        int overNum;
        int ballInOver;
        final Random rng = new Random();
    }
}
