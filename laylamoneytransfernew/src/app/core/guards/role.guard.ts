import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Route-level role gate. Checks the `roles` array on `route.data` against
 * the roles claim stored in the user's JWT. If the user has at least one of
 * the required roles, the route activates; otherwise they are redirected to
 * their home route (resolved by `AuthService.getHomeRoute()` — customers land
 * on `/home`, admins on `/admin`, super-admins on `/superadmin`, partners on
 * their own portal).
 *
 * ## Usage
 * Attach `canActivate: [AuthGuard, RoleGuard]` and `data: { roles: ['ADMIN', 'SUPER_ADMIN'] }`
 * in `app-routing.module.ts`. Use AuthGuard first to redirect unauthenticated
 * users to `/login`; RoleGuard then applies the role check.
 *
 * ## Important
 * Roles listed in `data.roles` must match role names seeded in the backend
 * `V2__seed_roles_permissions.sql` migration EXACTLY. Common gotcha: the
 * `PAYIN_PARTNER` role is referenced here but not yet seeded — see
 * `documentremitz.md` §9.2.
 *
 * ## Defence in depth
 * This guard only hides UI routes. The real authorisation happens server-side
 * via `@PreAuthorize("hasAuthority('…')")` on every controller. Never rely on
 * this guard alone for security — always enforce on the backend.
 */
@Injectable({
  providedIn: 'root'
})
export class RoleGuard implements CanActivate {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  private static readonly SUPERUSER_ROLES = ['SUPER_ADMIN', 'ADMIN'];

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const requiredRoles = route.data['roles'] as string[];
    if (!requiredRoles || requiredRoles.length === 0) {
      return true;
    }

    const userRoles = this.authService.getUserRoles();
    const hasRole = requiredRoles.some(role => userRoles.includes(role));
    const isSuperuser = userRoles.some(role => RoleGuard.SUPERUSER_ROLES.includes(role));

    if (hasRole || isSuperuser) {
      return true;
    }

    this.router.navigate([this.authService.getHomeRoute()]);
    return false;
  }
}
