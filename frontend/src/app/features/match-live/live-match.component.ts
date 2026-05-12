import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, interval } from 'rxjs';
import { MatchService, MatchSummary, LiveState, WindowView } from '../../core/match.service';
import { WebSocketService } from '../../core/websocket.service';
import { DecisionPanelComponent, DecisionPayload } from './decision-panel.component';
import { RevealComponent, RevealResult } from './reveal.component';

interface WindowEvent {
  windowId: number;
  type: 'BOWLING_CHANGE' | 'FIELD_SET';
  over: number;
  ball: number;
  opensAt: string;
  closesAt: string;
  secondsRemaining: number;
}

@Component({
  selector: 'cs-live-match',
  standalone: true,
  imports: [CommonModule, FormsModule, DecisionPanelComponent, RevealComponent],
  template: `
    <div *ngIf="matches().length === 0" class="empty card">
      <h3>No live matches right now</h3>
      <p class="muted">Check back during an IPL fixture, or ask an admin to create a mock match.</p>
    </div>

    <div *ngIf="matches().length > 0" class="layout">
      <aside class="card">
        <h4>Live matches</h4>
        <ul class="match-list">
          <li *ngFor="let m of matches()"
              [class.active]="selectedMatchId() === m.id"
              (click)="select(m.id)">
            <strong>{{ m.homeTeam }}</strong> vs <strong>{{ m.awayTeam }}</strong>
            <span class="muted small">{{ m.venue }}</span>
          </li>
        </ul>
      </aside>

      <section class="main">
        <div class="card scoreboard" *ngIf="state() as s">
          <div class="teams">
            <h2>{{ s.match.homeTeam }} vs {{ s.match.awayTeam }}</h2>
            <span class="muted">{{ s.match.venue }} · {{ s.match.season }}</span>
          </div>
          <div class="scores">
            <div *ngFor="let inn of s.innings" class="innings">
              <div class="team">{{ inn.battingTeam }}</div>
              <div class="runs">
                {{ inn.totalRuns }}/{{ inn.wickets }}
                <span class="muted small">({{ overText(inn.legalBalls) }})</span>
              </div>
              <div *ngIf="inn.lastBall" class="last-ball muted small">
                Last: {{ inn.lastBall.over }}.{{ inn.lastBall.ballInOver }}
                — {{ inn.lastBall.bowler }} to {{ inn.lastBall.batter }},
                {{ inn.lastBall.runs }}{{ inn.lastBall.wicket ? ' W' : '' }}
              </div>
            </div>
          </div>
        </div>

        <cs-decision-panel
          *ngIf="activeWindow() as w"
          [window]="w"
          [secondsRemaining]="secondsRemaining()"
          (submit)="submitDecision(w, $event)">
        </cs-decision-panel>

        <div class="card" *ngIf="!activeWindow()">
          <h3>Waiting for the next decision window…</h3>
          <p class="muted">A new window opens after each ball.</p>
        </div>

        <cs-reveal *ngIf="lastResult() as r" [result]="r"></cs-reveal>
      </section>
    </div>
  `,
  styles: [`
    .empty { text-align: center; padding: 3rem 1rem; }
    .layout { display: grid; grid-template-columns: 280px 1fr; gap: 1rem; }
    @media (max-width: 800px) { .layout { grid-template-columns: 1fr; } }
    .match-list { list-style: none; padding: 0; margin: 0; }
    .match-list li { padding: 0.6rem 0.5rem; border-radius: 8px; cursor: pointer; display: flex; flex-direction: column; }
    .match-list li.active, .match-list li:hover { background: var(--panel-2); }
    .small { font-size: 0.8rem; }
    .scoreboard .teams h2 { margin: 0; }
    .scores { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-top: 0.75rem; }
    .innings .team { color: var(--muted); }
    .innings .runs { font-size: 1.6rem; font-weight: 700; }
    .last-ball { margin-top: 0.35rem; }
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
  lastResult = signal<RevealResult | null>(null);

  private subs: Subscription[] = [];
  private tickerSub?: Subscription;

  ngOnInit(): void {
    this.matchSvc.liveMatches().subscribe(ms => {
      this.matches.set(ms);
      if (ms.length && this.selectedMatchId() === null) this.select(ms[0].id);
    });

    this.subs.push(this.ws.watchMyResults<RevealResult>().subscribe(r => this.lastResult.set(r)));
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    this.tickerSub?.unsubscribe();
  }

  select(matchId: number): void {
    this.selectedMatchId.set(matchId);
    this.refreshState(matchId);

    this.subs.forEach(s => s.unsubscribe());
    this.subs = [];

    this.subs.push(this.ws.watchMatch(matchId).subscribe(() => this.refreshState(matchId)));
    this.subs.push(this.ws.watchWindows<WindowEvent>(matchId).subscribe(w => this.handleWindow(w)));
    this.subs.push(this.ws.watchMyResults<RevealResult>().subscribe(r => this.lastResult.set(r)));

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

  refreshState(matchId: number): void {
    this.matchSvc.matchState(matchId).subscribe(s => this.state.set(s));
  }

  handleWindow(w: WindowEvent): void {
    this.activeWindow.set(w);
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

  submitDecision(window: WindowEvent, payload: DecisionPayload): void {
    this.matchSvc.submitDecision(window.windowId, payload as unknown as Record<string, unknown>)
      .subscribe({ next: () => this.activeWindow.set(null) });
  }

  overText(legalBalls: number): string {
    const o = Math.floor(legalBalls / 6);
    const b = legalBalls % 6;
    return `${o}.${b}`;
  }
}
