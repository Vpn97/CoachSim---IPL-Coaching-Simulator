import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatchService, MatchSummary } from '../../core/match.service';

@Component({
  selector: 'cs-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>Admin — Mock Match Control</h2>

    <section class="card">
      <h3>Create live match</h3>
      <div class="grid">
        <label>Season <input [(ngModel)]="newMatch.season" placeholder="2026"></label>
        <label>Home team <input [(ngModel)]="newMatch.homeTeam" placeholder="Mumbai Indians"></label>
        <label>Away team <input [(ngModel)]="newMatch.awayTeam" placeholder="Chennai Super Kings"></label>
        <label>Venue <input [(ngModel)]="newMatch.venue" placeholder="Wankhede"></label>
      </div>
      <button (click)="createMatch()">Create LIVE match</button>
      <p class="success" *ngIf="created()">Created match #{{ created() }}</p>
    </section>

    <section class="card" *ngIf="matches().length">
      <h3>Live matches</h3>
      <table>
        <tr><th>Id</th><th>Season</th><th>Home</th><th>Away</th><th>Status</th><th></th></tr>
        <tr *ngFor="let m of matches()">
          <td>{{ m.id }}</td>
          <td>{{ m.season }}</td>
          <td>{{ m.homeTeam }}</td>
          <td>{{ m.awayTeam }}</td>
          <td>{{ m.status }}</td>
          <td><button (click)="select(m)">Control</button></td>
        </tr>
      </table>
    </section>

    <section class="card" *ngIf="selected() as m">
      <h3>Controlling: {{ m.homeTeam }} vs {{ m.awayTeam }} (#{{ m.id }})</h3>

      <h4>Auto-play simulation</h4>
      <p class="muted small">Generates a ball every few seconds and a captain move every couple of overs — perfect for live-feeling demos without a paid ball-by-ball API.</p>
      <div class="grid">
        <label>Ball every (s) <input type="number" min="2" max="60" [(ngModel)]="autoPlay.intervalSeconds"></label>
      </div>
      <button (click)="startAutoPlay(m.id)" [disabled]="autoPlay.running">Start auto-play</button>
      <button (click)="stopAutoPlay(m.id)" [disabled]="!autoPlay.running" style="margin-left:0.5rem; background: var(--panel-2); color: var(--text)">Stop</button>
      <p class="success small" *ngIf="autoPlay.running">Auto-play running for match #{{ m.id }}</p>

      <h4 style="margin-top:1.5rem">Push ball manually</h4>
      <div class="grid">
        <label>Innings <input type="number" min="1" max="2" [(ngModel)]="ball.inningsNumber"></label>
        <label>Over <input type="number" [(ngModel)]="ball.over"></label>
        <label>Ball <input type="number" min="1" max="6" [(ngModel)]="ball.ball"></label>
        <label>Bowler <input [(ngModel)]="ball.bowler"></label>
        <label>Bowler type
          <select [(ngModel)]="ball.bowlerType">
            <option>PACE</option><option>SPIN</option><option>MEDIUM</option>
          </select>
        </label>
        <label>Batter <input [(ngModel)]="ball.batter"></label>
        <label>Batter hand
          <select [(ngModel)]="ball.batterHand">
            <option>RIGHT</option><option>LEFT</option>
          </select>
        </label>
        <label>Runs <input type="number" [(ngModel)]="ball.runs"></label>
        <label>Extras <input type="number" [(ngModel)]="ball.extras"></label>
        <label>Wicket?
          <select [(ngModel)]="ball.wicket"><option [ngValue]="false">No</option><option [ngValue]="true">Yes</option></select>
        </label>
      </div>
      <button (click)="pushBall(m.id)">Push ball</button>

      <h4 style="margin-top:1.5rem">Reveal captain move</h4>
      <div class="grid">
        <label>Move type
          <select [(ngModel)]="capt.moveType">
            <option>BOWLING_CHANGE</option><option>FIELD_SET</option>
          </select>
        </label>
        <label>For over <input type="number" [(ngModel)]="capt.over"></label>
        <label>For ball <input type="number" min="1" max="6" [(ngModel)]="capt.ball"></label>
        <label>Bowler (if change) <input [(ngModel)]="capt.bowler"></label>
        <label>Bowler type
          <select [(ngModel)]="capt.bowlerType">
            <option>PACE</option><option>SPIN</option><option>MEDIUM</option>
          </select>
        </label>
        <label>Batter hand
          <select [(ngModel)]="capt.batterHand">
            <option>RIGHT</option><option>LEFT</option>
          </select>
        </label>
      </div>
      <button (click)="pushCaptainMove(m.id)">Reveal captain's move</button>
    </section>
  `,
  styles: [`
    .card { margin: 1rem 0; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 0.6rem 1rem; margin: 0.75rem 0; }
    label { display: flex; flex-direction: column; color: var(--muted); font-size: 0.85rem; }
    label > input, label > select { margin-top: 0.25rem; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.35rem 0.5rem; border-bottom: 1px solid var(--border); text-align: left; }
    h4 { margin: 0.75rem 0 0.25rem; }
  `]
})
export class AdminComponent implements OnInit {
  private http = inject(HttpClient);
  private matchSvc = inject(MatchService);

  matches = signal<MatchSummary[]>([]);
  selected = signal<MatchSummary | null>(null);
  created = signal<number | null>(null);

  newMatch = { season: '2026', homeTeam: '', awayTeam: '', venue: '' };

  ball = { inningsNumber: 1, over: 1, ball: 1, bowler: '', bowlerType: 'PACE',
           batter: '', batterHand: 'RIGHT', runs: 0, extras: 0, wicket: false };

  capt = { moveType: 'BOWLING_CHANGE', over: 1, ball: 1,
           bowler: '', bowlerType: 'PACE', batterHand: 'RIGHT' };

  autoPlay = { intervalSeconds: 5, running: false };

  ngOnInit(): void { this.reloadMatches(); }

  reloadMatches(): void {
    this.matchSvc.liveMatches().subscribe(ms => this.matches.set(ms));
  }

  createMatch(): void {
    this.http.post<MatchSummary>('/api/admin/matches', this.newMatch)
      .subscribe(m => { this.created.set(m.id); this.reloadMatches(); });
  }

  select(m: MatchSummary): void {
    this.selected.set(m);
    this.http.get<{ matchId: number; running: boolean }>(`/api/admin/matches/${m.id}/auto-play`)
      .subscribe(s => this.autoPlay.running = s.running);
  }

  startAutoPlay(matchId: number): void {
    this.http.post<{ matchId: number; running: boolean }>(
      `/api/admin/matches/${matchId}/auto-play/start`,
      { ballEverySeconds: this.autoPlay.intervalSeconds }
    ).subscribe(s => this.autoPlay.running = s.running);
  }

  stopAutoPlay(matchId: number): void {
    this.http.post<{ matchId: number; running: boolean }>(
      `/api/admin/matches/${matchId}/auto-play/stop`, {}
    ).subscribe(s => this.autoPlay.running = s.running);
  }

  pushBall(matchId: number): void {
    this.http.post('/api/admin/balls', { ...this.ball, matchId }).subscribe(() => {
      this.ball.ball = this.ball.ball >= 6 ? 1 : this.ball.ball + 1;
      if (this.ball.ball === 1) this.ball.over = this.ball.over + 1;
    });
  }

  pushCaptainMove(matchId: number): void {
    const payload: Record<string, unknown> = {
      bowler: this.capt.bowler,
      bowlerType: this.capt.bowlerType,
      batterHand: this.capt.batterHand
    };
    if (this.capt.moveType === 'FIELD_SET') {
      payload['positions'] = Array.from({ length: 9 }, (_, i) => ({
        slot: i + 1, zone: 'COVERS', legSide: false, insideCircle: i < 5
      }));
    }
    this.http.post('/api/admin/captain-move', {
      matchId,
      over: this.capt.over,
      ball: this.capt.ball,
      moveType: this.capt.moveType,
      payload
    }).subscribe();
  }
}
