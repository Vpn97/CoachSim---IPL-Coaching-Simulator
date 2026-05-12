import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MyScore, ScoreBreakdown } from '../../core/match.service';

/**
 * Sticky banner anchored to the top of the live page that shows the fan's
 * running tactical merit *for the currently selected match*.
 *
 *   - Total points  (sum across all scored decisions in this match)
 *   - Decisions     (scored / pending breakdown)
 *   - Average       (per-decision)
 *   - Last delta    (the score from the most-recently resolved window)
 *
 * It is driven by two inputs:
 *   1. an initial snapshot loaded over HTTP (so the panel is populated even
 *      if the user joined mid-match);
 *   2. per-ball deltas pushed via STOMP `/user/queue/decision-result` — the
 *      parent merges those into the snapshot and re-passes it in.
 *
 * The banner animates the "+N" delta whenever a new score lands so the user
 * sees their tactical merit ticking up ball-by-ball.
 */
@Component({
  selector: 'cs-live-score-banner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="score-banner card"
         [class.has-data]="score && score.decisionsScored > 0">

      <div class="left">
        <span class="label muted">Your tactical merit</span>
        <div class="total-row">
          <div class="total" [class.high]="band() === 'high'"
                            [class.mid]="band() === 'mid'"
                            [class.low]="band() === 'low'">
            {{ score?.totalScore ?? 0 }}
            <span class="unit">pts</span>
          </div>
          <div *ngIf="flashDelta() as d" class="delta" [class.positive]="d > 0">
            +{{ d }}
          </div>
        </div>
        <div class="meta">
          <span class="chip">
            <strong>{{ score?.decisionsScored ?? 0 }}</strong>
            <span class="muted">scored</span>
          </span>
          <span *ngIf="(score?.decisionsPending ?? 0) > 0" class="chip pending">
            <strong>{{ score?.decisionsPending }}</strong>
            <span class="muted">awaiting</span>
          </span>
          <span class="chip">
            avg <strong>{{ avgText() }}</strong>
          </span>
          <span class="chip" *ngIf="(score?.bestScore ?? 0) > 0">
            best <strong>{{ score?.bestScore }}</strong>
          </span>
        </div>
      </div>

      <div class="right" *ngIf="score?.lastBreakdown as b">
        <span class="label muted">Last ball</span>
        <div class="last-score" [ngClass]="bandFor(score?.lastScore ?? 0)">
          {{ score?.lastScore }}<span class="unit">/100</span>
        </div>
        <ul class="mini-rules">
          <li *ngFor="let r of topRules(b)" [title]="r.detail">
            <span class="r-name">{{ pretty(r.rule) }}</span>
            <span class="r-pts">{{ r.points }}/{{ r.maxPoints }}</span>
          </li>
        </ul>
        <button class="why-btn" (click)="toggleDetails()">
          {{ showDetails() ? 'Hide breakdown' : 'Why?' }}
        </button>
      </div>

      <div class="right empty" *ngIf="!score || score.decisionsScored === 0">
        <span class="label muted">Waiting for your first scored ball</span>
        <p class="hint">Pick a bowler or set a field — the captain reveals after
          the next ball lands.</p>
      </div>
    </div>

    <!-- Optional expanded breakdown — kept out of the banner so it doesn't
         push other content down unless the fan asks for it. -->
    <div *ngIf="showDetails() && score?.lastBreakdown as b" class="card details">
      <h4>How your last decision scored</h4>
      <ul class="rules">
        <li *ngFor="let r of b.rules" [class.zero]="r.maxPoints === 0">
          <span class="name">{{ pretty(r.rule) }}</span>
          <span class="pts">{{ r.points }}/{{ r.maxPoints }}</span>
          <span class="detail muted">{{ r.detail }}</span>
        </li>
      </ul>
    </div>
  `,
  styles: [`
    /*
      Non-sticky: an earlier version pinned this with position:sticky; top:0
      to keep the score visible while scrolling. That collided with the global
      topbar (also sticky at top:0, z-index 10) and the banner ended up hidden
      behind the topbar — making the page appear empty on some screen heights.
      Anchoring as a normal block at the top of the routed view is enough,
      and the topbar already stays visible on its own.
    */
    .score-banner {
      display: grid;
      grid-template-columns: 1fr 1.1fr;
      gap: 1rem;
      align-items: stretch;
      border: 1px solid var(--border);
      background: linear-gradient(135deg, rgba(255,122,24,0.08), rgba(20,27,51,0.95) 70%);
    }
    .score-banner:not(.has-data) {
      background: var(--panel);
    }
    @media (max-width: 720px) {
      .score-banner { grid-template-columns: 1fr; }
    }

    .label { font-size: 0.7rem; letter-spacing: 1.5px; text-transform: uppercase; }

    /* ---------- Left: running total ---------- */
    .left { display: flex; flex-direction: column; gap: 0.4rem; }
    .total-row { display: flex; align-items: baseline; gap: 0.75rem; }
    .total {
      font-size: 2.8rem; font-weight: 900; line-height: 1;
      color: var(--accent-2);
      letter-spacing: -1px;
    }
    .total .unit { font-size: 0.85rem; font-weight: 600; color: var(--muted); margin-left: 0.25rem; letter-spacing: 1px; }
    .total.high { color: var(--success); }
    .total.mid  { color: var(--accent-2); }
    .total.low  { color: var(--danger); }

    .delta {
      font-size: 1.2rem; font-weight: 800;
      padding: 0.15rem 0.55rem; border-radius: 8px;
      background: rgba(74,222,128,0.18); color: var(--success);
      animation: pop 1.2s ease-out;
    }
    .delta.positive::before { content: ''; }
    @keyframes pop {
      0%   { transform: translateY(6px) scale(0.85); opacity: 0; }
      40%  { transform: translateY(-3px) scale(1.15); opacity: 1; }
      100% { transform: translateY(0) scale(1); opacity: 1; }
    }

    .meta { display: flex; gap: 0.45rem; flex-wrap: wrap; }
    .meta .chip {
      background: var(--panel-2);
      padding: 0.25rem 0.6rem;
      border-radius: 999px;
      font-size: 0.78rem;
      display: inline-flex; gap: 0.3rem; align-items: center;
    }
    .meta .chip strong { color: var(--accent-2); }
    .meta .chip.pending strong { color: var(--accent); }

    /* ---------- Right: last ball ---------- */
    .right { display: flex; flex-direction: column; gap: 0.35rem; }
    .right.empty .hint { margin: 0.25rem 0 0; font-size: 0.85rem; color: var(--muted); }
    .last-score {
      font-size: 2rem; font-weight: 800; line-height: 1;
      padding: 0.15rem 0.5rem; border-radius: 10px;
      background: var(--panel-2); align-self: flex-start;
    }
    .last-score .unit { font-size: 0.75rem; font-weight: 600; color: var(--muted); margin-left: 0.15rem; }
    .last-score.high { color: var(--success); }
    .last-score.mid  { color: var(--accent-2); }
    .last-score.low  { color: var(--danger); }

    .mini-rules { list-style: none; padding: 0; margin: 0.25rem 0 0; display: flex; flex-direction: column; gap: 0.2rem; }
    .mini-rules li {
      display: flex; justify-content: space-between; gap: 0.5rem;
      font-size: 0.78rem;
      padding: 0.2rem 0.5rem; background: var(--panel-2); border-radius: 6px;
    }
    .r-name { font-weight: 600; }
    .r-pts { color: var(--accent-2); font-weight: 700; }

    .why-btn {
      align-self: flex-start;
      margin-top: 0.25rem;
      background: transparent;
      color: var(--accent-2);
      border: 1px solid var(--accent);
      padding: 0.3rem 0.7rem;
      font-size: 0.78rem;
    }
    .why-btn:hover { background: rgba(255,122,24,0.1); transform: none; }

    /* ---------- Expanded breakdown card ---------- */
    .details { margin-top: 0.5rem; }
    .details h4 { margin: 0 0 0.5rem; }
    .details .rules { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.3rem; }
    .details .rules li {
      display: grid;
      grid-template-columns: 180px 80px 1fr;
      gap: 0.5rem; align-items: center;
      padding: 0.4rem 0.65rem; background: var(--panel-2); border-radius: 8px;
    }
    .details .rules li.zero { opacity: 0.5; }
    .details .rules .name { font-weight: 600; }
    .details .rules .pts { font-weight: 700; color: var(--accent-2); }
    .details .rules .detail { font-size: 0.85rem; }
  `]
})
export class LiveScoreBannerComponent {
  /**
   * Latest snapshot for this match. Parent should:
   *   1. seed from `match.service#myScore(matchId)` on match select;
   *   2. patch in WS `decision-result` events as they arrive (and call
   *      `flashDelta()` to trigger the +N animation).
   */
  @Input() score: MyScore | null = null;

  /**
   * When the parent receives a new score result, it sets this transient delta
   * so the banner animates a "+N" pill for ~1.2s. Parent then clears it.
   */
  @Input() set delta(v: number | null) {
    this.flashDelta.set(v);
    if (v != null) {
      // Auto-clear so a stale value doesn't stick around if the parent forgets.
      setTimeout(() => {
        if (this.flashDelta() === v) this.flashDelta.set(null);
      }, 1500);
    }
  }

  flashDelta = signal<number | null>(null);
  showDetails = signal(false);

  toggleDetails(): void {
    this.showDetails.update(v => !v);
  }

  band(): 'high' | 'mid' | 'low' {
    const avg = this.score?.averageScore ?? 0;
    if (avg >= 65) return 'high';
    if (avg >= 35) return 'mid';
    return 'low';
  }

  bandFor(score: number): string {
    if (score >= 70) return 'high';
    if (score >= 40) return 'mid';
    return 'low';
  }

  avgText(): string {
    const a = this.score?.averageScore ?? 0;
    return a ? a.toFixed(1) : '—';
  }

  /** Top 2 most-impactful rules for the compact mini-list. */
  topRules(b: ScoreBreakdown): ScoreBreakdown['rules'] {
    return [...b.rules]
      .filter(r => r.maxPoints > 0)
      .sort((a, c) => c.points - a.points)
      .slice(0, 2);
  }

  pretty(id: string): string {
    return id.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }
}
