import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('logs the user in and persists state to localStorage', () => {
    let observed: unknown = null;
    service.login('user@example.com', 'secret-1234').subscribe(r => (observed = r));

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({
      token: 'jwt-token',
      expiresInMs: 1000,
      user: { id: 1, email: 'user@example.com', displayName: 'Alice', role: 'ROLE_USER' }
    });

    expect(observed).toBeTruthy();
    expect(service.isLoggedIn()).toBeTrue();
    expect(service.isAdmin()).toBeFalse();
    expect(service.displayName()).toBe('Alice');
    expect(localStorage.getItem('coachsim.auth')).toContain('jwt-token');
  });

  it('recognizes admin role', () => {
    service.login('admin@example.com', 'secret-1234').subscribe();
    http.expectOne('/api/auth/login').flush({
      token: 'jwt-token',
      expiresInMs: 1000,
      user: { id: 2, email: 'admin@example.com', displayName: 'Admin', role: 'ROLE_ADMIN' }
    });
    expect(service.isAdmin()).toBeTrue();
  });
});
