import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface RevealResult {
  windowId: number;
  score: number;
  captainMoveId: number;
  breakdown: {
    totalPoints: number;
    maxPoints: number;
    normalised: number;
    rules: { rule: string; points: number; maxPoints: number; detail: string }[];
  };
}

@Component({
  selector: 'cs-reveal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="card reveal" *ngIf="result">
      <div class="header">
        <h3>Your tactical merit</h3>
        <div class="score" [ngClass]="band()">{{ result.score }}<span>/100</span></div>
      </div>
      <ul class="rules">
        <li *ngFor="let r of result.breakdown.rules">
          <span class="name">{{ pretty(r.rule) }}</span>
          <span class="pts">{{ r.points }}/{{ r.maxPoints }}</span>
          <span class="detail muted">{{ r.detail }}</span>
        </li>
      </ul>
    </div>
  `,
  styles: [`
    .reveal .header { display: flex; align-items: center; justify-content: space-between; }
    .score { font-size: 2rem; font-weight: 800; padding: 0.25rem 0.75rem; border-radius: 12px; background: var(--panel-2); }
    .score span { font-size: 0.9rem; color: var(--muted); }
    .score.high { color: var(--success); }
    .score.mid { color: var(--accent-2); }
    .score.low { color: var(--danger); }
    .rules { list-style: none; padding: 0; margin: 0.5rem 0 0; display: grid; gap: 0.4rem; }
    .rules li { display: grid; grid-template-columns: 200px 80px 1fr; gap: 0.5rem; padding: 0.4rem 0.6rem; background: var(--panel-2); border-radius: 8px; }
    .name { font-weight: 600; }
    .pts { font-weight: 700; color: var(--accent-2); }
  `]
})
export class RevealComponent {
  @Input() result: RevealResult | null = null;

  band(): string {
    const s = this.result?.score ?? 0;
    if (s >= 70) return 'high';
    if (s >= 40) return 'mid';
    return 'low';
  }

  pretty(id: string): string {
    return id.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }
}
