import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Where each fielder stands. We model the eight cricket-ground zones used
 * by the backend's FieldCoverageRule, each in TWO possible depths:
 *   IN  — inside the 30-yard ring (single-saving)
 *   OUT — outside the ring (boundary rider)
 */
export type ZoneId =
  | 'THIRD_MAN' | 'POINT'      | 'COVERS'
  | 'MID_OFF'   | 'MID_ON'     | 'MID_WICKET'
  | 'SQUARE_LEG'| 'FINE_LEG';

export type FielderDepth = 'IN' | 'OUT';

export interface GroundFielder {
  zone: ZoneId;
  depth: FielderDepth;
}

const LEG_SIDE_ZONES: Record<ZoneId, boolean> = {
  THIRD_MAN:  false, POINT:      false, COVERS:     false, MID_OFF: false,
  MID_ON:     true,  MID_WICKET: true,  SQUARE_LEG: true,  FINE_LEG: true,
};

/**
 * Geometry for the 8 zones at IN and OUT depths.
 *   - center of the ground = (200, 200)
 *   - inner 30-yard ring   = radius 78
 *   - boundary             = radius 175  (drawn as a slightly-wider ellipse)
 *
 * Batter stands at the south end of the pitch (~y=270), facing north,
 * so off-side is screen-LEFT and leg-side is screen-RIGHT (right-handed batter).
 */
const ZONE_GEOMETRY: Record<ZoneId, { in: [number, number]; out: [number, number]; label: string }> = {
  THIRD_MAN:  { in: [165, 165], out: [ 90,  90], label: '3rd Man'   },
  POINT:      { in: [148, 200], out: [ 50, 200], label: 'Point'     },
  COVERS:     { in: [165, 235], out: [ 90, 310], label: 'Covers'    },
  MID_OFF:    { in: [185, 250], out: [165, 350], label: 'Mid-Off'   },
  MID_ON:     { in: [215, 250], out: [235, 350], label: 'Mid-On'    },
  MID_WICKET: { in: [235, 235], out: [310, 310], label: 'Mid-Wicket'},
  SQUARE_LEG: { in: [252, 200], out: [350, 200], label: 'Sq. Leg'   },
  FINE_LEG:   { in: [235, 165], out: [310,  90], label: 'Fine Leg'  },
};

const ALL_ZONES: ZoneId[] = Object.keys(ZONE_GEOMETRY) as ZoneId[];

/**
 * Reusable cricket-ground SVG.
 *
 *   <cs-ground [fielders]="..." (zoneTapped)="..."></cs-ground>
 *
 * The component is purely presentational: parents own the fielder list and
 * decide how clicks mutate it. We just visualise + emit (zone, depth) pairs.
 */
@Component({
  selector: 'cs-ground',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg viewBox="0 0 400 400" class="ground" preserveAspectRatio="xMidYMid meet"
         [attr.aria-label]="'Cricket ground with ' + fielders.length + ' fielders placed'">
      <defs>
        <radialGradient id="grass" cx="50%" cy="50%" r="65%">
          <stop offset="0%"  stop-color="#2c8f4d"/>
          <stop offset="70%" stop-color="#1f6e3a"/>
          <stop offset="100%" stop-color="#15532a"/>
        </radialGradient>
      </defs>

      <!-- Boundary -->
      <ellipse cx="200" cy="200" rx="180" ry="175"
               fill="url(#grass)" stroke="#ffffff55" stroke-width="2"/>

      <!-- 30-yard ring -->
      <circle cx="200" cy="200" r="78" fill="none"
              stroke="#ffffff66" stroke-width="1.5" stroke-dasharray="4 4"/>

      <!-- Pitch -->
      <rect x="190" y="135" width="20" height="130" rx="2"
            fill="#caa15a" stroke="#8a6c33" stroke-width="0.5"/>
      <!-- Crease lines + stumps -->
      <line x1="185" y1="143" x2="215" y2="143" stroke="#fff" stroke-width="1"/>
      <line x1="185" y1="257" x2="215" y2="257" stroke="#fff" stroke-width="1"/>
      <circle cx="200" cy="138" r="2.2" fill="#f1f1f1"/>
      <circle cx="200" cy="262" r="2.2" fill="#fff7d6"/>

      <!-- Side labels -->
      <text x="20"  y="200" class="side-label off">OFF</text>
      <text x="380" y="200" class="side-label leg">LEG</text>

      <!-- Zone slots (in + out) -->
      <g *ngFor="let z of zones">
        <g class="slot"
           [class.active]="isActive(z, 'OUT')"
           [class.legside]="legSide(z)"
           (click)="onTap(z, 'OUT')">
          <circle [attr.cx]="pos(z, 'OUT')[0]" [attr.cy]="pos(z, 'OUT')[1]" r="14"/>
          <text [attr.x]="pos(z, 'OUT')[0]" [attr.y]="pos(z, 'OUT')[1] + 22" class="zone-label">
            {{ label(z) }}
          </text>
        </g>
        <g class="slot inner"
           [class.active]="isActive(z, 'IN')"
           [class.legside]="legSide(z)"
           (click)="onTap(z, 'IN')">
          <circle [attr.cx]="pos(z, 'IN')[0]" [attr.cy]="pos(z, 'IN')[1]" r="9"/>
        </g>
      </g>

      <!-- Wicket-keeper marker (always present) -->
      <g class="keeper">
        <circle cx="200" cy="125" r="7"/>
        <text x="200" y="115" class="role-label">WK</text>
      </g>
      <!-- Bowler marker -->
      <g class="bowler">
        <circle cx="200" cy="275" r="7"/>
        <text x="200" y="295" class="role-label">B</text>
      </g>

      <!-- Optional last-ball trajectory (a thin arrow from pitch to landing zone) -->
      <g *ngIf="lastBallZone() as lb" class="trajectory">
        <line x1="200" y1="200" [attr.x2]="pos(lb, 'OUT')[0]" [attr.y2]="pos(lb, 'OUT')[1]"
              stroke="var(--accent-2)" stroke-width="1.5" stroke-dasharray="3 4" opacity="0.85"/>
        <circle [attr.cx]="pos(lb, 'OUT')[0]" [attr.cy]="pos(lb, 'OUT')[1]"
                r="4" fill="var(--accent-2)" opacity="0.9">
          <animate attributeName="r" values="3;7;3" dur="1.4s" repeatCount="indefinite"/>
        </circle>
      </g>
    </svg>

    <div class="legend muted">
      <span><span class="dot in"></span> Inside ring (1 tap)</span>
      <span><span class="dot out"></span> Boundary rider</span>
      <span *ngIf="readOnly" class="readonly-note">(read-only)</span>
    </div>
  `,
  styles: [`
    :host { display: block; width: 100%; }
    .ground {
      width: 100%;
      max-width: 520px;
      aspect-ratio: 1 / 1;
      display: block;
      margin: 0 auto;
      filter: drop-shadow(0 4px 12px rgba(0,0,0,0.4));
    }

    .slot circle {
      fill: rgba(20, 27, 51, 0.55);
      stroke: rgba(255, 255, 255, 0.55);
      stroke-width: 1.5;
      transition: fill 0.15s ease, stroke 0.15s ease, r 0.15s ease;
      cursor: pointer;
    }
    .slot:hover circle {
      fill: rgba(255, 122, 24, 0.25);
      stroke: var(--accent);
    }
    .slot.active circle {
      fill: var(--accent);
      stroke: #1b1305;
      stroke-width: 2;
    }
    .slot.active.legside circle {
      fill: var(--accent-2);
      stroke: #2a1c00;
    }

    .zone-label {
      fill: rgba(255, 255, 255, 0.78);
      font-size: 9px;
      text-anchor: middle;
      pointer-events: none;
      font-weight: 600;
    }
    .side-label {
      fill: rgba(255, 255, 255, 0.4);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 1px;
      text-anchor: middle;
    }
    .side-label.leg { fill: rgba(255, 209, 102, 0.55); }
    .side-label.off { fill: rgba(255, 255, 255, 0.5); }

    .keeper circle { fill: #f1f1f1; stroke: #1b1305; stroke-width: 1.5; }
    .bowler circle { fill: #ffd166; stroke: #2a1c00; stroke-width: 1.5; }
    .role-label {
      fill: rgba(255, 255, 255, 0.9);
      font-size: 9px;
      font-weight: 700;
      text-anchor: middle;
      pointer-events: none;
    }

    .legend {
      display: flex;
      justify-content: center;
      gap: 1rem;
      margin-top: 0.5rem;
      font-size: 0.75rem;
      flex-wrap: wrap;
    }
    .legend .dot {
      display: inline-block;
      width: 10px; height: 10px; border-radius: 50%;
      vertical-align: middle;
      margin-right: 0.25rem;
      border: 1.5px solid rgba(255,255,255,0.55);
      background: rgba(20,27,51,0.6);
    }
    .legend .dot.in  { width: 8px; height: 8px; background: var(--accent); border-color: #1b1305; }
    .legend .dot.out { width: 12px; height: 12px; background: var(--accent-2); border-color: #2a1c00; }
    .readonly-note { color: var(--muted); font-style: italic; }

    /* Disable pointer events when read-only */
    :host(.read-only) .slot { pointer-events: none; }
    :host(.read-only) .slot circle { cursor: default; }
  `],
  host: { '[class.read-only]': 'readOnly' },
})
export class GroundComponent {
  @Input() fielders: GroundFielder[] = [];
  @Input() readOnly = false;
  @Input() lastBall: { zone?: ZoneId } | null = null;

  @Output() zoneTapped = new EventEmitter<{ zone: ZoneId; depth: FielderDepth }>();

  zones = ALL_ZONES;

  pos(zone: ZoneId, depth: FielderDepth): [number, number] {
    return depth === 'IN' ? ZONE_GEOMETRY[zone].in : ZONE_GEOMETRY[zone].out;
  }

  label(zone: ZoneId): string {
    return ZONE_GEOMETRY[zone].label;
  }

  legSide(zone: ZoneId): boolean {
    return LEG_SIDE_ZONES[zone];
  }

  isActive(zone: ZoneId, depth: FielderDepth): boolean {
    return this.fielders.some(f => f.zone === zone && f.depth === depth);
  }

  lastBallZone(): ZoneId | null {
    const z = this.lastBall?.zone;
    return z && ALL_ZONES.includes(z) ? z : null;
  }

  onTap(zone: ZoneId, depth: FielderDepth): void {
    if (this.readOnly) return;
    this.zoneTapped.emit({ zone, depth });
  }
}
