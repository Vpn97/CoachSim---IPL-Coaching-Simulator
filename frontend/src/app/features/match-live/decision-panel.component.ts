import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GroundComponent, GroundFielder, ZoneId, FielderDepth } from './ground.component';

/* ---------- Payload contract (matches backend FieldCoverageRule / HistoricalEconomyRule) ----- */

export type DecisionPayload =
  | { bowler: string; bowlerType: string }
  | { positions: { slot: number; zone: string; legSide: boolean; insideCircle: boolean }[] };

interface WindowInput {
  windowId: number;
  type: 'BOWLING_CHANGE' | 'FIELD_SET';
  over: number;
  ball: number;
}

/* ---------- Bowler quick-pick roster ---------- */

interface BowlerChip {
  name: string;
  short: string;
  type: 'PACE' | 'SPIN' | 'MEDIUM';
}

const BOWLERS: BowlerChip[] = [
  { name: 'J. Bumrah',        short: 'Bumrah',   type: 'PACE'   },
  { name: 'Mohammed Shami',   short: 'Shami',    type: 'PACE'   },
  { name: 'Arshdeep Singh',   short: 'Arshdeep', type: 'PACE'   },
  { name: 'M. Pathirana',     short: 'Pathirana',type: 'PACE'   },
  { name: 'Bhuvneshwar K.',   short: 'Bhuvi',    type: 'MEDIUM' },
  { name: 'D. Chahar',        short: 'Chahar',   type: 'MEDIUM' },
  { name: 'R. Jadeja',        short: 'Jadeja',   type: 'SPIN'   },
  { name: 'Y. Chahal',        short: 'Chahal',   type: 'SPIN'   },
  { name: 'K. Yadav',         short: 'Kuldeep',  type: 'SPIN'   },
  { name: 'R. Ashwin',        short: 'Ashwin',   type: 'SPIN'   },
];

/* ---------- Fielding-formation presets ---------- */

interface Preset { id: string; label: string; subtitle: string; fielders: GroundFielder[]; }

const PRESETS: Preset[] = [
  {
    id: 'powerplay',
    label: 'Powerplay',
    subtitle: '2 outside ring',
    fielders: [
      { zone: 'THIRD_MAN',  depth: 'OUT' },
      { zone: 'FINE_LEG',   depth: 'OUT' },
      { zone: 'COVERS',     depth: 'IN'  },
      { zone: 'POINT',      depth: 'IN'  },
      { zone: 'MID_OFF',    depth: 'IN'  },
      { zone: 'MID_ON',     depth: 'IN'  },
      { zone: 'MID_WICKET', depth: 'IN'  },
      { zone: 'SQUARE_LEG', depth: 'IN'  },
    ],
  },
  {
    id: 'attacking',
    label: 'Attacking',
    subtitle: 'Catchers in',
    fielders: [
      { zone: 'THIRD_MAN',  depth: 'IN' },
      { zone: 'POINT',      depth: 'IN' },
      { zone: 'COVERS',     depth: 'IN' },
      { zone: 'MID_OFF',    depth: 'IN' },
      { zone: 'MID_ON',     depth: 'IN' },
      { zone: 'MID_WICKET', depth: 'IN' },
      { zone: 'SQUARE_LEG', depth: 'IN' },
      { zone: 'FINE_LEG',   depth: 'OUT'},
    ],
  },
  {
    id: 'defensive',
    label: 'Defensive',
    subtitle: 'Spread for singles',
    fielders: [
      { zone: 'THIRD_MAN',  depth: 'OUT' },
      { zone: 'POINT',      depth: 'IN'  },
      { zone: 'COVERS',     depth: 'OUT' },
      { zone: 'MID_OFF',    depth: 'IN'  },
      { zone: 'MID_ON',     depth: 'IN'  },
      { zone: 'MID_WICKET', depth: 'OUT' },
      { zone: 'SQUARE_LEG', depth: 'IN'  },
      { zone: 'FINE_LEG',   depth: 'OUT' },
    ],
  },
  {
    id: 'death',
    label: 'Death overs',
    subtitle: 'Boundary riders',
    fielders: [
      { zone: 'THIRD_MAN',  depth: 'OUT' },
      { zone: 'POINT',      depth: 'OUT' },
      { zone: 'COVERS',     depth: 'OUT' },
      { zone: 'MID_OFF',    depth: 'IN'  },
      { zone: 'MID_ON',     depth: 'IN'  },
      { zone: 'MID_WICKET', depth: 'OUT' },
      { zone: 'SQUARE_LEG', depth: 'IN'  },
      { zone: 'FINE_LEG',   depth: 'OUT' },
    ],
  },
];

@Component({
  selector: 'cs-decision-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, GroundComponent],
  template: `
    <div class="card panel">
      <!--
        The card uses a sticky top action-bar that holds: the title, the
        live countdown, AND the submit button. Under a 15-second timer the
        most important control (submit) must always be in the user's eye
        line — so we anchor it at the top and let the chips/ground scroll
        below if the viewport is small.
      -->
      <header class="action-bar">
        <div class="title-row">
          <h3>
            {{ window.type === 'BOWLING_CHANGE' ? 'Pick your bowler' : 'Set your field' }}
            <span class="muted small">· over {{ window.over }}.{{ window.ball }}</span>
          </h3>
          <div class="timer-wrap">
            <div class="timer-ring" [class.warn]="secondsRemaining <= 5">
              <svg viewBox="0 0 36 36" class="ring">
                <circle cx="18" cy="18" r="15" class="track"/>
                <circle cx="18" cy="18" r="15" class="fill"
                        [attr.stroke-dasharray]="ringDasharray()"/>
              </svg>
              <span class="seconds">{{ secondsRemaining }}</span>
            </div>
          </div>
        </div>

        <button class="submit-btn top"
                (click)="onSubmit()"
                [disabled]="!canSubmit()">
          {{ submitLabel() }}
        </button>

        <p *ngIf="window.type === 'BOWLING_CHANGE'" class="muted small hint">
          Tap any bowler — one tap commits the choice.
        </p>
        <p *ngIf="window.type === 'FIELD_SET'" class="muted small hint">
          Tap a position on the ground to drop a fielder. Or start from a preset.
        </p>
      </header>

      <!-- ============ BOWLING CHANGE ============ -->
      <ng-container *ngIf="window.type === 'BOWLING_CHANGE'">
        <div class="bowler-grid">
          <button *ngFor="let b of bowlers"
                  type="button"
                  class="bowler-chip"
                  [class.selected]="bowler === b.name"
                  [attr.data-type]="b.type"
                  (click)="pickBowler(b)">
            <span class="bowler-name">{{ b.short }}</span>
            <span class="bowler-type">{{ b.type }}</span>
          </button>

          <button type="button"
                  class="bowler-chip other"
                  [class.selected]="otherOpen()"
                  (click)="toggleOther()">
            <span class="bowler-name">{{ otherOpen() ? 'Cancel' : 'Other…' }}</span>
          </button>
        </div>

        <div *ngIf="otherOpen()" class="other-form">
          <input #o type="text" [(ngModel)]="customName"
                 placeholder="Custom bowler name"
                 (keyup.enter)="pickCustom()">
          <select [(ngModel)]="customType">
            <option value="PACE">Pace</option>
            <option value="SPIN">Spin</option>
            <option value="MEDIUM">Medium</option>
          </select>
          <button type="button" (click)="pickCustom()" [disabled]="!customName.trim()">Use this</button>
        </div>

        <div class="selected-line" *ngIf="bowler">
          <span class="muted">Selected:</span>
          <strong>{{ bowler }}</strong>
          <span class="badge" [attr.data-type]="bowlerType">{{ bowlerType }}</span>
        </div>
      </ng-container>

      <!-- ============ FIELD SET ============ -->
      <ng-container *ngIf="window.type === 'FIELD_SET'">
        <div class="preset-row">
          <button *ngFor="let p of presets"
                  type="button"
                  class="preset-chip"
                  [class.selected]="activePreset() === p.id"
                  (click)="applyPreset(p)">
            <strong>{{ p.label }}</strong>
            <small>{{ p.subtitle }}</small>
          </button>
          <button type="button" class="preset-chip clear" (click)="clearField()">
            <strong>Clear</strong>
            <small>start over</small>
          </button>
        </div>

        <cs-ground
          [fielders]="fielders()"
          [lastBall]="null"
          (zoneTapped)="onZoneTap($event)">
        </cs-ground>

        <div class="field-status">
          <span class="stat">
            <strong>{{ fielders().length }}</strong><span class="muted">/9 fielders</span>
          </span>
          <span class="stat" [class.bad]="legOutCount() > 5">
            <strong>{{ legOutCount() }}</strong>
            <span class="muted">leg-side outside ring (max 5)</span>
          </span>
        </div>
      </ng-container>
    </div>
  `,
  styles: [`
    /* ---------- Layout & header ---------- */
    .panel { display: flex; flex-direction: column; gap: 0.75rem; }
    .action-bar {
      position: sticky;
      top: 0;
      z-index: 5;
      background: var(--panel);
      padding: 0.1rem 0 0.75rem;
      margin-bottom: 0.25rem;
      border-bottom: 1px solid var(--border);
      display: flex; flex-direction: column; gap: 0.5rem;
    }
    .action-bar .hint { margin: 0; }
    .title-row {
      display: flex; align-items: center; justify-content: space-between; gap: 1rem;
    }
    .title-row h3 { margin: 0; }
    .small { font-size: 0.85rem; }

    /* ---------- Circular countdown timer ---------- */
    .timer-wrap { position: relative; width: 64px; height: 64px; flex-shrink: 0; }
    .timer-ring { position: relative; width: 100%; height: 100%; }
    .timer-ring .ring { transform: rotate(-90deg); width: 100%; height: 100%; }
    .timer-ring circle { fill: none; stroke-width: 3.5; }
    .timer-ring .track { stroke: var(--panel-2); }
    .timer-ring .fill  {
      stroke: var(--accent-2);
      stroke-linecap: round;
      transition: stroke-dasharray 0.4s linear, stroke 0.2s ease;
    }
    .timer-ring.warn .fill { stroke: var(--danger); animation: pulse 0.8s ease-in-out infinite; }
    .timer-ring .seconds {
      position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
      font-weight: 800; font-size: 1.2rem; color: var(--text);
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50%      { opacity: 0.55; }
    }

    /* ---------- Bowler chips ---------- */
    .bowler-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
      gap: 0.5rem;
      margin: 0.75rem 0;
    }
    .bowler-chip {
      background: var(--panel-2);
      color: var(--text);
      border: 1.5px solid transparent;
      border-radius: 10px;
      padding: 0.7rem 0.5rem;
      text-align: center;
      display: flex; flex-direction: column; gap: 0.2rem;
      font-weight: 600;
      transition: transform 0.1s ease, border-color 0.15s ease, background 0.15s ease;
    }
    .bowler-chip:hover { transform: translateY(-1px); border-color: var(--accent); }
    .bowler-chip.selected {
      border-color: var(--accent);
      background: linear-gradient(135deg, rgba(255,122,24,0.15), rgba(255,209,102,0.1));
    }
    .bowler-name { font-size: 0.95rem; }
    .bowler-type {
      font-size: 0.7rem; letter-spacing: 1px; opacity: 0.8;
      padding: 2px 6px; border-radius: 999px; align-self: center;
    }
    .bowler-chip[data-type="PACE"]   .bowler-type { background: rgba(248,113,113,0.18); color: #fca5a5; }
    .bowler-chip[data-type="SPIN"]   .bowler-type { background: rgba(74,222,128,0.18); color: #86efac; }
    .bowler-chip[data-type="MEDIUM"] .bowler-type { background: rgba(255,209,102,0.18); color: #ffd166; }
    .bowler-chip.other {
      background: transparent;
      border: 1.5px dashed var(--border);
      color: var(--muted);
    }

    .other-form {
      display: flex; gap: 0.5rem; margin-bottom: 0.5rem;
      flex-wrap: wrap;
    }
    .other-form input { flex: 1 1 200px; }
    .other-form select { flex: 0 0 110px; }

    .selected-line {
      display: flex; align-items: center; gap: 0.5rem;
      padding: 0.5rem 0.75rem; background: var(--panel-2);
      border-radius: 8px; margin-bottom: 0.5rem;
    }
    .badge {
      font-size: 0.7rem; letter-spacing: 1px; padding: 2px 8px; border-radius: 999px;
      font-weight: 700;
    }
    .badge[data-type="PACE"]   { background: rgba(248,113,113,0.2); color: #fca5a5; }
    .badge[data-type="SPIN"]   { background: rgba(74,222,128,0.2); color: #86efac; }
    .badge[data-type="MEDIUM"] { background: rgba(255,209,102,0.2); color: #ffd166; }

    /* ---------- Formation presets ---------- */
    .preset-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
      gap: 0.5rem;
      margin: 0.5rem 0 0.75rem;
    }
    .preset-chip {
      background: var(--panel-2);
      color: var(--text);
      border: 1.5px solid transparent;
      border-radius: 10px;
      padding: 0.5rem 0.6rem;
      display: flex; flex-direction: column; gap: 0.1rem;
      text-align: left;
      transition: all 0.15s ease;
    }
    .preset-chip strong { font-size: 0.9rem; }
    .preset-chip small  { color: var(--muted); font-size: 0.72rem; }
    .preset-chip:hover { transform: translateY(-1px); border-color: var(--accent); }
    .preset-chip.selected {
      border-color: var(--accent);
      background: linear-gradient(135deg, rgba(255,122,24,0.18), rgba(255,209,102,0.08));
    }
    .preset-chip.clear { border: 1.5px dashed var(--border); background: transparent; color: var(--muted); }

    /* ---------- Field status footer ---------- */
    .field-status {
      display: flex; gap: 1rem; justify-content: center; flex-wrap: wrap;
      margin: 0.5rem 0 0.25rem;
      font-size: 0.85rem;
    }
    .field-status .stat strong { color: var(--accent-2); font-size: 1.1rem; margin-right: 0.25rem; }
    .field-status .stat.bad strong { color: var(--danger); }
    .field-status .stat.bad { color: var(--danger); }

    /* ---------- Submit ---------- */
    .submit-btn {
      width: 100%;
      padding: 0.85rem 1rem;
      font-size: 1rem;
      background: linear-gradient(90deg, var(--accent), var(--accent-2));
      color: #1b1305;
      box-shadow: 0 2px 10px rgba(255, 122, 24, 0.25);
    }
    .submit-btn.top { margin: 0; }
    .submit-btn:disabled {
      background: var(--panel-2); color: var(--muted); box-shadow: none;
    }
  `]
})
export class DecisionPanelComponent {
  @Input({ required: true }) window!: WindowInput;
  @Input({ required: true }) secondsRemaining = 0;
  @Input() windowLengthSeconds = 20;
  @Output() submit = new EventEmitter<DecisionPayload>();

  bowlers = BOWLERS;
  presets = PRESETS;

  /* ---- Bowling state ---- */
  bowler = '';
  bowlerType: 'PACE' | 'SPIN' | 'MEDIUM' = 'PACE';
  otherOpen = signal(false);
  customName = '';
  customType: 'PACE' | 'SPIN' | 'MEDIUM' = 'PACE';

  /* ---- Field state ---- */
  fielders = signal<GroundFielder[]>(PRESETS[0].fielders);
  activePreset = signal<string | null>(PRESETS[0].id);

  legOutCount = computed(() =>
    this.fielders()
      .filter(f => f.depth === 'OUT')
      .filter(f => this.isLegSide(f.zone))
      .length
  );

  /* ---- Bowler interactions ---- */
  pickBowler(b: BowlerChip): void {
    this.bowler = b.name;
    this.bowlerType = b.type;
    this.otherOpen.set(false);
  }
  toggleOther(): void {
    this.otherOpen.update(v => !v);
    if (!this.otherOpen()) this.customName = '';
  }
  pickCustom(): void {
    if (!this.customName.trim()) return;
    this.bowler = this.customName.trim();
    this.bowlerType = this.customType;
    this.otherOpen.set(false);
  }

  /* ---- Field interactions ---- */
  applyPreset(p: { id: string; fielders: GroundFielder[] }): void {
    this.fielders.set(p.fielders.map(f => ({ ...f })));
    this.activePreset.set(p.id);
  }

  clearField(): void {
    this.fielders.set([]);
    this.activePreset.set(null);
  }

  onZoneTap(e: { zone: ZoneId; depth: FielderDepth }): void {
    this.activePreset.set(null);
    const list = this.fielders();
    const idx = list.findIndex(f => f.zone === e.zone && f.depth === e.depth);
    if (idx >= 0) {
      this.fielders.set(list.filter((_, i) => i !== idx));
      return;
    }
    const other = list.findIndex(f => f.zone === e.zone);
    if (other >= 0) {
      const next = [...list];
      next[other] = { zone: e.zone, depth: e.depth };
      this.fielders.set(next);
      return;
    }
    if (list.length >= 9) return;
    this.fielders.set([...list, { zone: e.zone, depth: e.depth }]);
  }

  /* ---- Validation & submit ---- */
  canSubmit(): boolean {
    if (this.secondsRemaining <= 0) return false;
    if (this.window.type === 'BOWLING_CHANGE') return !!this.bowler.trim();
    return this.fielders().length >= 4 && this.legOutCount() <= 5;
  }

  submitLabel(): string {
    if (this.secondsRemaining <= 0) return 'Window closed';
    if (this.window.type === 'BOWLING_CHANGE') {
      return this.bowler ? `Submit · ${this.bowler}` : 'Pick a bowler';
    }
    if (this.fielders().length < 4) return `Place at least 4 (${this.fielders().length}/9)`;
    if (this.legOutCount() > 5) return 'Too many leg-side deep — fix to submit';
    return `Submit · ${this.fielders().length} fielders`;
  }

  onSubmit(): void {
    if (!this.canSubmit()) return;
    if (this.window.type === 'BOWLING_CHANGE') {
      this.submit.emit({ bowler: this.bowler.trim(), bowlerType: this.bowlerType });
      return;
    }
    const positions = this.fielders().map((f, i) => ({
      slot: i + 1,
      zone: f.zone,
      legSide: this.isLegSide(f.zone),
      insideCircle: f.depth === 'IN',
    }));
    this.submit.emit({ positions });
  }

  ringDasharray(): string {
    const total = Math.max(1, this.windowLengthSeconds);
    const frac = Math.max(0, Math.min(1, this.secondsRemaining / total));
    const circumference = 2 * Math.PI * 15;
    const filled = circumference * frac;
    return `${filled} ${circumference}`;
  }

  private isLegSide(zone: ZoneId): boolean {
    return zone === 'MID_ON' || zone === 'MID_WICKET' || zone === 'SQUARE_LEG' || zone === 'FINE_LEG';
  }
}
