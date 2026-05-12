import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface Entry {
  rank: number;
  userId: number;
  displayName: string;
  totalScore: number;
  decisionsCount: number;
  refreshedAt: string;
}

@Component({
  selector: 'cs-leaderboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>Leaderboard</h2>
    <div class="tabs">
      <button (click)="setScope('alltime')" [class.active]="scope() === 'alltime'">All-time</button>
      <button (click)="setScope('season')" [class.active]="scope() === 'season'">This season</button>
    </div>

    <div class="card">
      <table>
        <tr><th>Rank</th><th>Fan</th><th>Total score</th><th>Decisions</th></tr>
        <tr *ngFor="let e of entries()" [class.podium]="e.rank <= 3">
          <td><span class="rank">{{ e.rank }}</span></td>
          <td>{{ e.displayName }}</td>
          <td><strong>{{ e.totalScore }}</strong></td>
          <td>{{ e.decisionsCount }}</td>
        </tr>
        <tr *ngIf="entries().length === 0">
          <td colspan="4" class="muted">No scores yet — be the first to play!</td>
        </tr>
      </table>
    </div>
  `,
  styles: [`
    .tabs { display: flex; gap: 0.5rem; margin: 1rem 0; }
    .tabs button { background: var(--panel); color: var(--text); }
    .tabs button.active { background: var(--accent); color: #1b1305; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.5rem 0.6rem; border-bottom: 1px solid var(--border); text-align: left; }
    .rank { display: inline-block; min-width: 1.6rem; padding: 0.1rem 0.5rem; border-radius: 999px; background: var(--panel-2); }
    .podium .rank { background: linear-gradient(135deg, var(--accent), var(--accent-2)); color: #1b1305; font-weight: 700; }
  `]
})
export class LeaderboardComponent implements OnInit {
  private http = inject(HttpClient);
  scope = signal<'alltime' | 'season'>('alltime');
  entries = signal<Entry[]>([]);

  ngOnInit(): void { this.refresh(); }

  setScope(scope: 'alltime' | 'season'): void {
    this.scope.set(scope);
    this.refresh();
  }

  refresh(): void {
    const url = this.scope() === 'alltime'
      ? '/api/leaderboard/alltime'
      : `/api/leaderboard/season/${new Date().getFullYear()}`;
    this.http.get<Entry[]>(url).subscribe(es => this.entries.set(es));
  }
}
