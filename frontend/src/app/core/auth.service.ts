import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

export interface UserInfo {
  id: number;
  email: string;
  displayName: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
}

export interface AuthResponse {
  token: string;
  expiresInMs: number;
  user: UserInfo;
}

const STORAGE_KEY = 'coachsim.auth';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly _state = signal<AuthResponse | null>(this.loadFromStorage());

  readonly user = computed(() => this._state()?.user ?? null);
  readonly token = computed(() => this._state()?.token ?? null);

  isLoggedIn(): boolean { return !!this._state(); }
  isAdmin(): boolean { return this._state()?.user.role === 'ROLE_ADMIN'; }
  displayName(): string { return this._state()?.user.displayName ?? ''; }

  register(email: string, password: string, displayName: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', { email, password, displayName })
      .pipe(tap(r => this.set(r)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password })
      .pipe(tap(r => this.set(r)));
  }

  logout(): void {
    this._state.set(null);
    localStorage.removeItem(STORAGE_KEY);
    this.router.navigateByUrl('/login');
  }

  private set(r: AuthResponse): void {
    this._state.set(r);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(r));
  }

  private loadFromStorage(): AuthResponse | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) as AuthResponse : null;
    } catch { return null; }
  }
}
