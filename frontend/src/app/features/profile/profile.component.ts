import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';

interface HistoryEntry {
  decisionId: number;
  windowId: number;
  matchId: number;
  type: 'BOWLING_CHANGE' | 'FIELD_SET';
  over: number;
  ball: number;
  score: number | null;
  breakdown: Record<string, unknown> | null;
  myPayload: Record<string, unknown>;
  submittedAt: string;
}

@Component({
  selector: 'cs-profile',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>{{ auth.displayName() }}</h2>

    <div class="stats">
      <div class="card stat">
        <div class="label">Decisions</div>
        <div class="value">{{ history().length }}</div>
      </div>
      <div class="card stat">
        <div class="label">Avg tactical merit</div>
        <div class="value">{{ average() }}</div>
      </div>
      <div class="card stat">
        <div class="label">Best score</div>
        <div class="value">{{ best() }}</div>
      </div>
    </div>

    <h3>Recent decisions</h3>
    <div class="card">
      <table>
        <tr><th>When</th><th>Type</th><th>Over</th><th>Score</th></tr>
        <tr *ngFor="let h of history()">
          <td class="muted small">{{ h.submittedAt | date:'medium' }}</td>
          <td>{{ h.type }}</td>
          <td>{{ h.over }}.{{ h.ball }}</td>
          <td>
            <span *ngIf="h.score === null" class="muted">pending</span>
            <strong *ngIf="h.score !== null">{{ h.score }}/100</strong>
          </td>
        </tr>
        <tr *ngIf="history().length === 0">
          <td colspan="4" class="muted">No decisions yet. Head to the live page.</td>
        </tr>
      </table>
    </div>
  `,
  styles: [`
    .stats { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 1rem; margin: 1rem 0; }
    .stat .label { color: var(--muted); font-size: 0.85rem; }
    .stat .value { font-size: 1.8rem; font-weight: 800; color: var(--accent-2); }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.5rem 0.6rem; border-bottom: 1px solid var(--border); text-align: left; }
    .small { font-size: 0.85rem; }
  `]
})
export class ProfileComponent implements OnInit {
  auth = inject(AuthService);
  private http = inject(HttpClient);

  history = signal<HistoryEntry[]>([]);

  average = computed(() => {
    const scored = this.history().filter(h => h.score !== null);
    if (!scored.length) return 0;
    return Math.round(scored.reduce((s, h) => s + (h.score ?? 0), 0) / scored.length);
  });

  best = computed(() => {
    const scored = this.history().filter(h => h.score !== null);
    if (!scored.length) return 0;
    return Math.max(...scored.map(h => h.score ?? 0));
  });

  ngOnInit(): void {
    this.http.get<HistoryEntry[]>('/api/decisions/history').subscribe(h => this.history.set(h));
  }
}
