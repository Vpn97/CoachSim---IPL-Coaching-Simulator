import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { MatchService, MatchSummary, LiveState, WindowView, BallView, MyScore } from '../../core/match.service';
import { WebSocketService } from '../../core/websocket.service';
import { DecisionPanelComponent, DecisionPayload } from './decision-panel.component';
import { RevealComponent, RevealResult } from './reveal.component';
import { GroundComponent } from './ground.component';
import { LiveScoreBannerComponent } from './live-score-banner.component';

interface WindowEvent {
  windowId: number;
  type: 'BOWLING_CHANGE' | 'FIELD_SET';
  over: number;
  ball: number;
  opensAt: string;
  closesAt: string;
  secondsRemaining: number;
}

/** Most recent N balls per innings for the live "ball strip" footer of the scoreboard. */
interface BallChip {
  over: number;
  ball: number;
  runs: number;
  wicket: boolean;
}

@Component({
  selector: 'cs-live-match',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink,
    DecisionPanelComponent, RevealComponent, GroundComponent, LiveScoreBannerComponent,
  ],
  template: `
    <!-- Skeleton while the first match list is loading. Without it the page
         would be momentarily blank on slow connections / cold backend
         startup, which the demo reported as "the live page isn't showing". -->
    <div *ngIf="loading()" class="skeleton card">
      <div class="bar lg"></div>
      <div class="bar sm"></div>
      <div class="bar md"></div>
    </div>

    <!-- Visible failure card instead of an empty page when the backend
         answers with anything other than success. Has a retry CTA so users
         can self-recover without a full page reload. -->
    <div *ngIf="!loading() && loadError() as err" class="error card">
      <div class="error-emoji">⚠️</div>
      <h3>Couldn't load the live match</h3>
      <p class="muted">{{ err }}</p>
      <button (click)="reloadLiveMatches()">Try again</button>
    </div>

    <!-- Empty state -->
    <div *ngIf="!loading() && !loadError() && matches().length === 0" class="empty card">
      <div class="empty-emoji">🏏</div>
      <h3>No live matches right now</h3>
      <p class="muted">Check back during an IPL fixture, or ask an admin to spin up a mock match from the
        <a routerLink="/admin">Admin panel</a>.</p>
    </div>

    <div *ngIf="!loading() && !loadError() && matches().length > 0" class="layout">

      <!-- =================== Sidebar: match picker =================== -->
      <aside class="card sidebar">
        <h4 class="sidebar-title">Live matches</h4>
        <ul class="match-list">
          <li *ngFor="let m of matches()"
              [class.active]="selectedMatchId() === m.id"
              (click)="select(m.id)">
            <div class="teams-row">
              <strong>{{ m.homeTeam }}</strong>
              <span class="vs">vs</span>
              <strong>{{ m.awayTeam }}</strong>
            </div>
            <span class="muted small">
              <span class="dot" [class.live]="m.status === 'LIVE'"></span>
              {{ m.venue || 'Neutral venue' }}
            </span>
          </li>
        </ul>
      </aside>

      <!-- =================== Main column =================== -->
      <section class="main">

        <!-- Transient submit-failure toast (409 = window closed, 400 = bad
             payload, etc.). Auto-dismisses after a few seconds. -->
        <div class="toast" *ngIf="submitToast() as t">{{ t }}</div>

        <!-- Sticky running-score banner (the user-facing summary the demo
             wants pinned at the top of the live match page). -->
        <cs-live-score-banner
          [score]="myScore()"
          [delta]="lastDelta()">
        </cs-live-score-banner>

        <!-- Scoreboard -->
        <div class="card scoreboard" *ngIf="state() as s">
          <div class="header">
            <div>
              <h2>{{ s.match.homeTeam }} <span class="muted">vs</span> {{ s.match.awayTeam }}</h2>
              <span class="muted small">{{ s.match.venue }} · {{ s.match.season }}</span>
            </div>
            <span class="status-pill" [attr.data-status]="s.match.status">{{ s.match.status }}</span>
          </div>

          <div class="innings-grid">
            <div *ngFor="let inn of s.innings" class="innings-card">
              <div class="inn-team muted">{{ inn.battingTeam }}</div>
              <div class="inn-runs">
                {{ inn.totalRuns }}<span class="slash">/</span><span class="wkts">{{ inn.wickets }}</span>
                <span class="inn-overs muted">({{ overText(inn.legalBalls) }})</span>
              </div>
              <div *ngIf="inn.lastBall as lb" class="inn-last">
                <span class="muted">Last:</span>
                <strong>{{ lb.over }}.{{ lb.ballInOver }}</strong>
                <span>{{ lb.bowler }} → {{ lb.batter }}</span>
                <span class="runs-chip" [attr.data-runs]="lb.runs" [class.wicket]="lb.wicket">
                  {{ lb.wicket ? 'W' : lb.runs }}
                </span>
              </div>
            </div>
          </div>

          <!-- Ball strip: last 6 balls of the current innings -->
          <div class="ball-strip" *ngIf="lastBalls().length">
            <span class="strip-label muted small">Recent balls</span>
            <div class="chips">
              <span *ngFor="let b of lastBalls()"
                    class="ball-chip"
                    [class.wicket]="b.wicket"
                    [attr.data-runs]="b.runs"
                    [title]="b.over + '.' + b.ball">
                {{ b.wicket ? 'W' : b.runs }}
              </span>
            </div>
          </div>
        </div>

        <!-- Decision panel (active window) -->
        <cs-decision-panel
          *ngIf="activeWindow() as w"
          [window]="w"
          [secondsRemaining]="secondsRemaining()"
          [windowLengthSeconds]="windowLengthSeconds()"
          (submit)="submitDecision(w, $event)">
        </cs-decision-panel>

        <!-- Idle: show the cricket ground in read-only mode with last-ball hint -->
        <div class="card waiting" *ngIf="!activeWindow()">
          <div class="waiting-head">
            <h3>Waiting for the next decision window…</h3>
            <p class="muted small">A new window opens after the next ball.</p>
          </div>
          <cs-ground [readOnly]="true" [fielders]="[]"></cs-ground>
        </div>

        <!-- Result reveal -->
        <cs-reveal *ngIf="lastResult() as r" [result]="r"></cs-reveal>
      </section>
    </div>
  `,
  styles: [`
    /* ---------- Empty / error / skeleton ---------- */
    .empty, .error { text-align: center; padding: 3rem 1rem; }
    .empty-emoji, .error-emoji { font-size: 3rem; margin-bottom: 0.5rem; }
    .error { border: 1px solid rgba(248,113,113,0.4); }
    .error button { margin-top: 0.75rem; }

    .skeleton { padding: 2rem; }
    .skeleton .bar {
      height: 14px; border-radius: 7px; margin: 0.8rem 0;
      background: linear-gradient(90deg, var(--panel-2) 0%, var(--panel) 50%, var(--panel-2) 100%);
      background-size: 200% 100%;
      animation: shimmer 1.2s infinite linear;
    }
    .skeleton .bar.lg { width: 60%; height: 22px; }
    .skeleton .bar.md { width: 45%; }
    .skeleton .bar.sm { width: 30%; }
    @keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

    /* ---------- Layout ---------- */
    .layout {
      display: grid;
      grid-template-columns: 280px 1fr;
      gap: 1rem;
      align-items: start;
    }
    @media (max-width: 900px) { .layout { grid-template-columns: 1fr; } }

    .main { display: flex; flex-direction: column; gap: 1rem; min-width: 0; }

    /* ---------- Sidebar ---------- */
    .sidebar-title { margin-top: 0; margin-bottom: 0.6rem; }
    .match-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 0.4rem; }
    .match-list li {
      padding: 0.6rem 0.75rem; border-radius: 10px; cursor: pointer;
      display: flex; flex-direction: column; gap: 0.2rem;
      border: 1.5px solid transparent;
      transition: background 0.15s ease, border-color 0.15s ease;
    }
    .match-list li:hover { background: var(--panel-2); }
    .match-list li.active {
      background: linear-gradient(135deg, rgba(255,122,24,0.12), rgba(255,209,102,0.05));
      border-color: var(--accent);
    }
    .teams-row { display: flex; align-items: center; gap: 0.35rem; flex-wrap: wrap; }
    .teams-row .vs { color: var(--muted); font-size: 0.75rem; }
    .small { font-size: 0.78rem; }
    .dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--muted); margin-right: 0.35rem; vertical-align: middle; }
    .dot.live { background: var(--danger); box-shadow: 0 0 6px var(--danger); animation: pulse 1.4s infinite; }

    @keyframes pulse { 0%,100%{opacity:1;} 50%{opacity:0.4;} }

    /* ---------- Scoreboard ---------- */
    .scoreboard .header {
      display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; flex-wrap: wrap;
    }
    .scoreboard h2 { margin: 0; font-size: 1.4rem; }
    .scoreboard h2 .muted { font-weight: 500; }
    .status-pill {
      padding: 0.25rem 0.7rem; border-radius: 999px; font-size: 0.7rem; font-weight: 700;
      letter-spacing: 1px; background: var(--panel-2); color: var(--muted);
    }
    .status-pill[data-status="LIVE"] { background: rgba(248,113,113,0.15); color: #fca5a5; }
    .status-pill[data-status="COMPLETED"] { background: rgba(74,222,128,0.15); color: #86efac; }

    .innings-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 0.75rem;
      margin-top: 1rem;
    }
    .innings-card {
      background: var(--panel-2);
      border-radius: 10px;
      padding: 0.75rem 1rem;
      display: flex; flex-direction: column; gap: 0.25rem;
    }
    .inn-team { font-size: 0.85rem; letter-spacing: 0.5px; text-transform: uppercase; }
    .inn-runs { font-size: 1.8rem; font-weight: 800; line-height: 1; display: flex; align-items: baseline; gap: 0.15rem; }
    .inn-runs .slash { color: var(--muted); }
    .inn-runs .wkts { color: var(--danger); }
    .inn-runs .inn-overs { font-size: 0.85rem; font-weight: 500; margin-left: 0.4rem; }
    .inn-last { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; font-size: 0.85rem; margin-top: 0.3rem; }
    .runs-chip {
      min-width: 22px; height: 22px; padding: 0 6px; border-radius: 6px;
      display: inline-flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 0.8rem;
      background: var(--panel); border: 1px solid var(--border);
    }
    .runs-chip[data-runs="0"] { color: var(--muted); }
    .runs-chip[data-runs="4"] { background: rgba(74,222,128,0.2); border-color: var(--success); color: var(--success); }
    .runs-chip[data-runs="6"] { background: rgba(255,122,24,0.25); border-color: var(--accent); color: var(--accent-2); }
    .runs-chip.wicket { background: rgba(248,113,113,0.25); border-color: var(--danger); color: #fca5a5; }

    .ball-strip {
      margin-top: 0.85rem;
      display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;
    }
    .ball-strip .chips { display: flex; gap: 0.3rem; }
    .ball-chip {
      width: 26px; height: 26px; border-radius: 50%;
      display: inline-flex; align-items: center; justify-content: center;
      background: var(--panel-2); border: 1px solid var(--border);
      font-size: 0.78rem; font-weight: 700;
    }
    .ball-chip[data-runs="0"] { color: var(--muted); }
    .ball-chip[data-runs="4"] { background: rgba(74,222,128,0.25); border-color: var(--success); color: var(--success); }
    .ball-chip[data-runs="6"] { background: rgba(255,122,24,0.3);  border-color: var(--accent); color: var(--accent-2); }
    .ball-chip.wicket { background: rgba(248,113,113,0.3); border-color: var(--danger); color: #fca5a5; }

    /* ---------- Waiting card ---------- */
    .waiting { display: flex; flex-direction: column; gap: 0.75rem; align-items: stretch; }
    .waiting-head h3 { margin: 0; }

    /* ---------- Submit-failure toast ---------- */
    .toast {
      padding: 0.6rem 0.9rem;
      border-radius: 10px;
      background: rgba(248,113,113,0.15);
      border: 1px solid rgba(248,113,113,0.4);
      color: #fca5a5;
      font-size: 0.9rem;
      animation: toastIn 0.25s ease-out;
    }
    @keyframes toastIn {
      from { opacity: 0; transform: translateY(-6px); }
      to   { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class LiveMatchComponent implements OnInit, OnDestroy {
  private matchSvc = inject(MatchService);
  private ws = inject(WebSocketService);

  matches = signal<MatchSummary[]>([]);
  selectedMatchId = signal<number | null>(null);
  state = signal<LiveState | null>(null);
  activeWindow = signal<WindowEvent | null>(null);
  secondsRemaining = signal(0);
  windowLengthSeconds = signal(20);
  lastResult = signal<RevealResult | null>(null);

  /**
   * Surfaces problems on the live page that previously produced silent
   * "blank scoreboard" symptoms: live-list fetch failing, state fetch
   * failing (e.g. quiet 401), or the match-list arriving empty.
   */
  loading = signal(true);
  loadError = signal<string | null>(null);

  /** Running per-match tactical merit for the current user. */
  myScore = signal<MyScore | null>(null);

  /** Transient delta (the "+N" that flashes on the banner after each score). */
  lastDelta = signal<number | null>(null);

  /** Last six balls in the most-recent innings, for the ball strip. */
  lastBalls = computed<BallChip[]>(() => {
    const s = this.state();
    if (!s || !s.innings.length) return [];
    const inn = s.innings[s.innings.length - 1];
    if (!inn.lastBall) return [];
    // Backend only returns the most recent ball in `lastBall`. We accumulate
    // a small rolling window client-side as new balls arrive.
    return this.rolling;
  });

  private rolling: BallChip[] = [];
  private subs: Subscription[] = [];
  private tickerSub?: Subscription;

  ngOnInit(): void {
    this.reloadLiveMatches();

    // Global subscription kept across match switches: react to every per-user
    // decision-result push by both showing the reveal AND folding it into the
    // running banner (which lives at the top of the page).
    this.subs.push(this.ws.watchMyResults<RevealResult>().subscribe(r => this.handleResult(r)));
  }

  /**
   * Pulls the current live-match list. Always sets `loading` to false at the
   * end so the template can stop showing the skeleton even if the request
   * fails — that failure produces a visible retry banner instead of a blank
   * page.
   */
  reloadLiveMatches(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.matchSvc.liveMatches().subscribe({
      next: ms => {
        this.matches.set(ms);
        if (ms.length && this.selectedMatchId() === null) this.select(ms[0].id);
        this.loading.set(false);
      },
      error: e => {
        this.loadError.set(this.formatError(e, 'live matches'));
        this.loading.set(false);
      },
    });
  }

  private formatError(e: unknown, what: string): string {
    const status = (e as { status?: number })?.status;
    if (status === 0) return `Can't reach the server. Check your connection and try again.`;
    if (status === 401) return `Your session expired — please sign in again.`;
    return `Couldn't load ${what} (HTTP ${status ?? '???'}). Try refreshing.`;
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.tickerSub?.unsubscribe();
  }

  select(matchId: number): void {
    this.selectedMatchId.set(matchId);
    this.rolling = [];
    this.refreshState(matchId);
    this.refreshMyScore(matchId);

    this.subs.forEach(s => s.unsubscribe());
    this.subs = [];

    this.subs.push(this.ws.watchMatch(matchId).subscribe(() => this.refreshState(matchId)));
    this.subs.push(this.ws.watchWindows<WindowEvent>(matchId).subscribe(w => this.handleWindow(w)));
    this.subs.push(this.ws.watchMyResults<RevealResult>().subscribe(r => this.handleResult(r)));

    this.matchSvc.openWindows(matchId).subscribe((ws: WindowView[]) => {
      if (ws.length) {
        const w = ws[0];
        this.handleWindow({
          windowId: w.id, type: w.type, over: w.over, ball: w.ball,
          opensAt: w.opensAt, closesAt: w.closesAt,
          secondsRemaining: Math.max(0, Math.round((+new Date(w.closesAt) - Date.now()) / 1000))
        });
      }
    });
  }

  /** Seed the banner from the server's authoritative aggregate. */
  private refreshMyScore(matchId: number): void {
    this.matchSvc.myScore(matchId).subscribe({
      next: s => this.myScore.set(s),
      // Anonymous mid-load — don't blow up the page, just leave the banner empty.
      error: () => this.myScore.set(null),
    });
  }

  /**
   * Fold a per-user reveal into both the reveal card AND the running banner.
   * The banner total is incremented optimistically (so the user sees the "+N"
   * pulse the instant the WS message lands, instead of waiting for the
   * leaderboard service to refresh).
   */
  private handleResult(r: RevealResult): void {
    this.lastResult.set(r);

    const prev = this.myScore();
    const matchId = this.selectedMatchId();
    if (!matchId) return;

    const merged: MyScore = prev ? {
      ...prev,
      totalScore: prev.totalScore + r.score,
      decisionsScored: prev.decisionsScored + 1,
      decisionsPending: Math.max(0, prev.decisionsPending - 1),
      averageScore: (prev.totalScore + r.score) / (prev.decisionsScored + 1),
      bestScore: Math.max(prev.bestScore, r.score),
      lastScore: r.score,
      lastBreakdown: r.breakdown,
      lastScoredAt: new Date().toISOString(),
    } : {
      matchId,
      totalScore: r.score,
      decisionsScored: 1,
      decisionsPending: 0,
      averageScore: r.score,
      bestScore: r.score,
      lastScore: r.score,
      lastBreakdown: r.breakdown,
      lastScoredAt: new Date().toISOString(),
    };
    this.myScore.set(merged);
    this.lastDelta.set(r.score);
  }

  refreshState(matchId: number): void {
    this.matchSvc.matchState(matchId).subscribe({
      next: s => {
        this.state.set(s);
        this.appendBall(s);
        this.loadError.set(null);
      },
      // Surface state-fetch failures (e.g. backend restart, network blip) so
      // the user gets a clear retry CTA instead of a perpetually empty page.
      error: e => this.loadError.set(this.formatError(e, 'this match')),
    });
  }

  private appendBall(s: LiveState): void {
    if (!s.innings.length) return;
    const inn = s.innings[s.innings.length - 1];
    const lb = inn.lastBall;
    if (!lb) return;
    const chip: BallChip = { over: lb.over, ball: lb.ballInOver, runs: lb.runs, wicket: lb.wicket };
    const last = this.rolling[this.rolling.length - 1];
    if (last && last.over === chip.over && last.ball === chip.ball) return;
    this.rolling = [...this.rolling, chip].slice(-6);
  }

  handleWindow(w: WindowEvent): void {
    this.activeWindow.set(w);
    const total = Math.max(1, Math.round((+new Date(w.closesAt) - +new Date(w.opensAt)) / 1000));
    this.windowLengthSeconds.set(total);
    this.secondsRemaining.set(w.secondsRemaining);
    this.tickerSub?.unsubscribe();
    this.tickerSub = interval(1000).subscribe(() => {
      const left = Math.max(0, Math.round((+new Date(w.closesAt) - Date.now()) / 1000));
      this.secondsRemaining.set(left);
      if (left === 0) {
        this.tickerSub?.unsubscribe();
        this.activeWindow.set(null);
      }
    });
  }

  /** Transient toast for submit failures so 409/4xx don't disappear silently. */
  submitToast = signal<string | null>(null);
  private submitToastTimer?: ReturnType<typeof setTimeout>;

  submitDecision(window: WindowEvent, payload: DecisionPayload): void {
    this.matchSvc.submitDecision(window.windowId, payload as unknown as Record<string, unknown>)
      .subscribe({
        next: () => this.activeWindow.set(null),
        error: e => {
          // 409 = window already closed (captain move came in / window expired).
          // 400 = payload validation failure (e.g. <4 fielders for FIELD_SET).
          // Either way, surface a brief toast so the user knows the click did
          // something — and clear the active window so the page returns to
          // "waiting for next decision".
          const status = (e as { status?: number })?.status;
          let msg = `Submit failed (HTTP ${status ?? '???'}).`;
          if (status === 409) msg = `Too late! The captain already made the call — wait for the next ball.`;
          else if (status === 400) msg = `Invalid decision: ${(e as { error?: { message?: string } })?.error?.message ?? 'check your selections.'}`;
          else if (status === 401) msg = `Please sign in to submit decisions.`;
          this.flashToast(msg);
          if (status === 409) this.activeWindow.set(null);
        }
      });
  }

  private flashToast(msg: string): void {
    this.submitToast.set(msg);
    clearTimeout(this.submitToastTimer);
    this.submitToastTimer = setTimeout(() => this.submitToast.set(null), 4000);
  }

  overText(legalBalls: number): string {
    const o = Math.floor(legalBalls / 6);
    const b = legalBalls % 6;
    return `${o}.${b}`;
  }
}
