import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { Observable, map } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Route-level authentication gate.
 *
 * Uses `AuthService.ensureAuthenticated()` which, when the short-lived ACCESS
 * token has expired but a REFRESH token still exists, silently refreshes the
 * session instead of bouncing the user to login. This is what lets a customer
 * re-open the app (e.g. after the app lock) and stay signed in — previously the
 * guard only checked the access-token expiry and sent them to home on lapse.
 *
 * Only when there is no usable session (no/expired refresh token, or refresh
 * fails) do we log out to the CORRECT login page for the user's role.
 *
 * Pair with `RoleGuard` for role-restricted routes:
 *     canActivate: [AuthGuard, RoleGuard]
 *     data: { roles: ['ADMIN', 'SUPER_ADMIN'] }
 */
@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
    return this.authService.ensureAuthenticated().pipe(
      map(ok => {
        if (ok) return true;
        // No usable session — send to the right login page (staff vs customer) and wipe state.
        this.authService.sessionExpiredLogout();
        return false;
      })
    );
  }
}
