package com.coachsim.admin;

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

    private static final String[] PACE_BOWLERS = {"J. Bumrah", "Mohammed Shami", "M. Pathirana", "D. Chahar", "U. Yadav"};
    private static final String[] SPIN_BOWLERS = {"R. Jadeja", "Y. Chahal", "K. Yadav", "R. Ashwin"};
    private static final String[] BATTERS_R = {"V. Kohli", "R. Sharma", "S. Iyer", "S. Gill", "M. Dhoni"};
    private static final String[] BATTERS_L = {"R. Jaiswal", "I. Kishan", "T. Stubbs", "S. Tilak Varma"};

    private final TaskScheduler scheduler;
    private final IngestionService ingestion;
    private final InningsRepository innings;
    private final BallRepository balls;

    /** Active autoplay sessions, keyed by matchId. */
    private final Map<Long, ScheduledFuture<?>> running = new ConcurrentHashMap<>();
    private final Map<Long, SimState> state = new ConcurrentHashMap<>();

    public AutoPlaySimulator(TaskScheduler scheduler,
                             IngestionService ingestion,
                             InningsRepository innings,
                             BallRepository balls) {
        this.scheduler = scheduler;
        this.ingestion = ingestion;
        this.innings = innings;
        this.balls = balls;
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
        log.info("AutoPlay starting for match {} from over {}.{} (interval={}s)",
                matchId, init.overNum, init.ballInOver, ballEverySeconds);

        Duration period = Duration.ofSeconds(Math.max(2, ballEverySeconds));
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> safeTick(matchId), period);
        running.put(matchId, task);
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

        if (s.overNum > 20) {
            log.info("AutoPlay finished 20 overs for match {} — stopping", matchId);
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

        // Captain move for the NEXT ball — published every single ball so every
        // submitted fan decision gets resolved & scored within seconds (the user
        // wants per-ball scoring during the live demo, not "wait for the over").
        // We rotate the captain's bowler/field every couple of overs so the
        // tactical merit verdicts vary instead of always agreeing with the fan.
        short nextOver = (short) (s.ballInOver == 6 ? s.overNum + 1 : s.overNum);
        short nextBall = (short) (s.ballInOver == 6 ? 1 : s.ballInOver + 1);
        publishCaptainMoves(matchId, nextOver, nextBall, bowler, bowlerType, hand, s);
    }

    /**
     * Publish one BOWLING_CHANGE and one FIELD_SET for the next ball.
     * The "captain" is intentionally not always identical to the last bowler —
     * we vary by phase + a small randomness so the rules engine produces
     * meaningfully different verdicts each ball.
     */
    private void publishCaptainMoves(long matchId, short nextOver, short nextBall,
                                     String lastBowler, Ball.BowlerType lastType,
                                     Ball.BatterHand hand, SimState s) {
        Ball.OverPhase nextPhase = Ball.phaseForOver(nextOver);
        Ball.BowlerType captainBowlerType;
        String captainBowler;

        // Phase-aware captain choice: pace in powerplay/death, spin in middle.
        // 30% of the time the captain "rotates" (different type) to surprise the fan.
        boolean rotate = s.rng.nextInt(10) < 3;
        boolean wantPace = nextPhase != Ball.OverPhase.MIDDLE;
        if (rotate) wantPace = !wantPace;
        captainBowlerType = wantPace ? Ball.BowlerType.PACE : Ball.BowlerType.SPIN;
        captainBowler = wantPace
                ? PACE_BOWLERS[(s.overNum + s.ballInOver) % PACE_BOWLERS.length]
                : SPIN_BOWLERS[(s.overNum + s.ballInOver) % SPIN_BOWLERS.length];

        ingestion.ingest(MatchEvent.builder()
                .type(MatchEvent.Type.CAPTAIN_MOVE)
                .matchId(matchId)
                .overNum(nextOver)
                .ballInOver(nextBall)
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
        List<String> hotZones = switch (nextPhase) {
            case POWERPLAY -> List.of("COVERS", "POINT", "MID_OFF", "MID_WICKET");
            case MIDDLE    -> List.of("MID_WICKET", "MID_ON", "COVERS", "SQUARE_LEG");
            case DEATH     -> List.of("MID_OFF", "MID_ON", "FINE_LEG", "MID_WICKET", "THIRD_MAN");
        };
        ingestion.ingest(MatchEvent.builder()
                .type(MatchEvent.Type.CAPTAIN_MOVE)
                .matchId(matchId)
                .overNum(nextOver)
                .ballInOver(nextBall)
                .captain(new MatchEvent.CaptainPayload(
                        CaptainMove.MoveType.FIELD_SET,
                        Map.of(
                                "batterHand", hand.name(),
                                "batterTopZones", hotZones,
                                "phase", nextPhase.name()
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
        s.overNum = last.map(Ball::getOverNum).orElse((short) 0);
        s.ballInOver = last.map(Ball::getBallInOver).orElse((short) 0);
        return s;
    }

    private static final class SimState {
        int overNum;
        int ballInOver;
        final Random rng = new Random();
    }
}
