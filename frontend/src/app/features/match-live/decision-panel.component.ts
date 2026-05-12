import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export type DecisionPayload =
  | { bowler: string; bowlerType: string }
  | { positions: FieldPosition[] };

export interface FieldPosition {
  slot: number;
  zone: string;
  legSide: boolean;
  insideCircle: boolean;
}

interface WindowInput {
  windowId: number;
  type: 'BOWLING_CHANGE' | 'FIELD_SET';
  over: number;
  ball: number;
}

const ZONES = ['COVERS', 'POINT', 'THIRD_MAN', 'FINE_LEG', 'SQUARE_LEG', 'MID_WICKET', 'MID_ON', 'MID_OFF'];
const LEG_SIDE_ZONES = new Set(['FINE_LEG', 'SQUARE_LEG', 'MID_WICKET', 'MID_ON']);

@Component({
  selector: 'cs-decision-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="card panel">
      <div class="header">
        <h3>
          {{ window.type === 'BOWLING_CHANGE' ? 'Pick your bowler' : 'Set the field' }}
          <span class="muted small">for over {{ window.over }}.{{ window.ball }}</span>
        </h3>
        <div class="timer" [class.warn]="secondsRemaining <= 5">{{ secondsRemaining }}s</div>
      </div>

      <ng-container *ngIf="window.type === 'BOWLING_CHANGE'">
        <div class="grid">
          <label>Bowler name
            <input [(ngModel)]="bowler" placeholder="e.g. J. Bumrah">
          </label>
          <label>Bowler type
            <select [(ngModel)]="bowlerType">
              <option>PACE</option>
              <option>SPIN</option>
              <option>MEDIUM</option>
            </select>
          </label>
        </div>
      </ng-container>

      <ng-container *ngIf="window.type === 'FIELD_SET'">
        <p class="muted small">Assign 9 fielders to zones. Max 5 on the leg side outside the circle.</p>
        <div class="positions">
          <div *ngFor="let p of positions(); let i = index" class="position">
            <span class="slot">#{{ p.slot }}</span>
            <select [(ngModel)]="p.zone" (ngModelChange)="onZoneChange(i, $event)">
              <option *ngFor="let z of zones" [value]="z">{{ z }}</option>
            </select>
            <label class="circle">
              <input type="checkbox" [(ngModel)]="p.insideCircle"> inside ring
            </label>
          </div>
        </div>
        <p class="muted small" *ngIf="legSideOutsideCount() > 5">
          <span class="danger">{{ legSideOutsideCount() }} on leg side outside circle — illegal.</span>
        </p>
      </ng-container>

      <button (click)="onSubmit()" [disabled]="!canSubmit()">
        Submit decision
      </button>
    </div>
  `,
  styles: [`
    .panel .header { display: flex; align-items: center; justify-content: space-between; }
    .timer {
      font-size: 1.5rem; font-weight: 800; color: var(--accent-2);
      padding: 0.25rem 0.6rem; background: var(--panel-2); border-radius: 8px;
    }
    .timer.warn { color: var(--danger); }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem 1rem; margin: 0.5rem 0 1rem; }
    label { display: flex; flex-direction: column; color: var(--muted); font-size: 0.85rem; }
    label > input, label > select { margin-top: 0.25rem; }
    .positions { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.5rem; margin: 0.75rem 0; }
    .position { display: flex; align-items: center; gap: 0.5rem; background: var(--panel-2); padding: 0.4rem 0.6rem; border-radius: 8px; }
    .slot { font-weight: 700; color: var(--accent-2); min-width: 2rem; }
    .circle { display: flex; align-items: center; gap: 0.25rem; font-size: 0.8rem; }
    .small { font-size: 0.85rem; }
    button { margin-top: 0.75rem; }
  `]
})
export class DecisionPanelComponent {
  @Input({ required: true }) window!: WindowInput;
  @Input({ required: true }) secondsRemaining = 0;
  @Output() submit = new EventEmitter<DecisionPayload>();

  zones = ZONES;
  bowler = '';
  bowlerType = 'PACE';

  positions = signal<FieldPosition[]>(
    Array.from({ length: 9 }, (_, i) => ({
      slot: i + 1, zone: ZONES[i % ZONES.length], legSide: LEG_SIDE_ZONES.has(ZONES[i % ZONES.length]), insideCircle: i < 4
    }))
  );

  legSideOutsideCount = computed(() =>
    this.positions().filter(p => p.legSide && !p.insideCircle).length);

  canSubmit(): boolean {
    if (this.secondsRemaining <= 0) return false;
    if (this.window.type === 'BOWLING_CHANGE') {
      return !!this.bowler.trim();
    }
    return this.legSideOutsideCount() <= 5;
  }

  onZoneChange(idx: number, zone: string): void {
    const list = [...this.positions()];
    list[idx] = { ...list[idx], zone, legSide: LEG_SIDE_ZONES.has(zone) };
    this.positions.set(list);
  }

  onSubmit(): void {
    if (this.window.type === 'BOWLING_CHANGE') {
      this.submit.emit({ bowler: this.bowler.trim(), bowlerType: this.bowlerType });
    } else {
      this.submit.emit({ positions: this.positions() });
    }
  }
}
