import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Route-level authentication gate. Checks that the user has a valid, unexpired
 * JWT via `AuthService.isAuthenticated()`. Unauthenticated users are redirected
 * to `/login` with a full page reload (not Angular navigation — this ensures
 * any in-memory auth state is wiped).
 *
 * Pair with `RoleGuard` for role-restricted routes, e.g.:
 *
 *     canActivate: [AuthGuard, RoleGuard]
 *     data: { roles: ['ADMIN', 'SUPER_ADMIN'] }
 *
 * AuthGuard must run first so role checks don't see a stale authentication
 * state. See `documentremitz.md` §4 for the full role matrix.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean {
    if (this.authService.isAuthenticated()) {
      return true;
    }
    window.location.href = '/login';
    return false;
  }
}
