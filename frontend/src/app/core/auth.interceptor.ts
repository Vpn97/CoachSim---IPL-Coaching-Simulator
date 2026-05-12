import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the JWT bearer token to outgoing requests and, when the backend
 * answers with 401 (token expired / invalid) on a request that DID carry a
 * token, transparently signs the user out and bounces them to /login.
 *
 * Without this, an expired token used to leave the live page half-rendered
 * (match list public → renders; protected calls → silently failed → no
 * scoreboard) which the demo perceived as "the live page doesn't show".
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const authed = req.clone(token ? { setHeaders: { Authorization: `Bearer ${token}` } } : {});

  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && token) {
        auth.logout();
        router.navigateByUrl('/login');
      }
      return throwError(() => err);
    })
  );
};
