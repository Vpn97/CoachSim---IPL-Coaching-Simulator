import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'cs-register',
  standalone: true,
  imports: [FormsModule, CommonModule, RouterLink],
  template: `
    <div class="auth-wrap">
      <div class="card">
        <h2>Create your account</h2>
        <p class="muted">Join the captain's table.</p>
        <form (ngSubmit)="submit()" #f="ngForm">
          <label>Display name
            <input type="text" name="displayName" [(ngModel)]="displayName" required minlength="2">
          </label>
          <label>Email
            <input type="email" name="email" [(ngModel)]="email" required>
          </label>
          <label>Password (min 8 chars)
            <input type="password" name="password" [(ngModel)]="password" required minlength="8">
          </label>
          <p class="danger" *ngIf="error()">{{ error() }}</p>
          <button type="submit" [disabled]="!f.valid || loading()">
            {{ loading() ? 'Creating...' : 'Create account' }}
          </button>
        </form>
        <p class="muted" style="margin-top:1rem">Have an account? <a routerLink="/login">Log in</a></p>
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
export class RegisterComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  displayName = '';
  loading = signal(false);
  error = signal<string | null>(null);

  submit(): void {
    this.error.set(null);
    this.loading.set(true);
    this.auth.register(this.email, this.password, this.displayName).subscribe({
      next: () => this.router.navigateByUrl('/live'),
      error: err => {
        this.error.set(err?.status === 409 ? 'Email already registered.' : 'Could not register.');
        this.loading.set(false);
      },
      complete: () => this.loading.set(false)
    });
  }
}
