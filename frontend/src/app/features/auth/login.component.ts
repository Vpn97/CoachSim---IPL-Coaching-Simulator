import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'cs-login',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink],
  template: `
    <div class="auth-wrap">
      <div class="card">
        <h2>Welcome back</h2>
        <p class="muted">Log in to take the captain's chair.</p>
        <form (ngSubmit)="submit()" #f="ngForm">
          <label>Email
            <input type="email" name="email" [(ngModel)]="email" required>
          </label>
          <label>Password
            <!--
              No minlength here: the backend only enforces a length on REGISTER.
              On login we want to accept whatever the user has (incl. legacy/seeded
              accounts like the demo "fan1234") and let the server decide.
            -->
            <input type="password" name="password" [(ngModel)]="password" required>
          </label>
          <p class="danger" *ngIf="error()">{{ error() }}</p>
          <button type="submit" [disabled]="!f.valid || loading()">
            {{ loading() ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>
        <p class="muted" style="margin-top:1rem">No account? <a routerLink="/register">Register</a></p>
      </div>
    </div>
  `,
  styles: [`
    .auth-wrap { display: flex; justify-content: center; padding: 3rem 1rem; }
    .card { width: 100%; max-width: 420px; }
    label { display: block; margin: 0.75rem 0; color: var(--muted); }
    label input { display: block; width: 100%; margin-top: 0.35rem; }
    button { margin-top: 0.5rem; width: 100%; }
  `]
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    this.error.set(null);
    this.loading.set(true);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigateByUrl('/live'),
      error: () => { this.error.set('Invalid email or password.'); this.loading.set(false); },
      complete: () => this.loading.set(false)
    });
  }
}
