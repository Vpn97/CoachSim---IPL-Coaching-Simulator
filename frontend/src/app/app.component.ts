import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'cs-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  template: `
    <header class="topbar">
      <a routerLink="/live" class="brand">CoachSim</a>
      <nav>
        <a routerLink="/live" routerLinkActive="active">Live</a>
        <a routerLink="/leaderboard" routerLinkActive="active">Leaderboard</a>
        <a *ngIf="auth.isLoggedIn()" routerLink="/profile" routerLinkActive="active">Profile</a>
        <a *ngIf="auth.isAdmin()" routerLink="/admin" routerLinkActive="active">Admin</a>
      </nav>
      <div class="auth">
        <ng-container *ngIf="auth.isLoggedIn(); else loginLinks">
          <span class="muted">{{ auth.displayName() }}</span>
          <button (click)="auth.logout()">Logout</button>
        </ng-container>
        <ng-template #loginLinks>
          <a routerLink="/login">Login</a>
          <a routerLink="/register">Register</a>
        </ng-template>
      </div>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: [`
    .topbar {
      display: flex;
      align-items: center;
      gap: 1.5rem;
      padding: 0.9rem 1.5rem;
      border-bottom: 1px solid var(--border);
      background: rgba(11, 16, 32, 0.85);
      backdrop-filter: blur(8px);
      position: sticky;
      top: 0;
      z-index: 10;
    }
    .brand { font-weight: 800; font-size: 1.2rem; color: var(--accent-2); }
    nav { display: flex; gap: 1rem; }
    nav a { color: var(--muted); padding: 0.25rem 0.6rem; border-radius: 6px; }
    nav a.active { color: var(--text); background: var(--panel); }
    .auth { margin-left: auto; display: flex; gap: 0.75rem; align-items: center; }
    main { padding: 1.25rem; max-width: 1200px; margin: 0 auto; }
  `]
})
export class AppComponent {
  auth = inject(AuthService);
}
