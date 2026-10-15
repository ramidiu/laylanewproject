import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';
import { Capacitor } from '@capacitor/core';
import { AuthService } from '../services/auth.service';

/**
 * In the NATIVE app we never want the web marketing landing page ("Send Money Abroad…", How It
 * Works / FAQ / Create account) — the app should open straight into the auth flow like a native app.
 *
 * On the web this guard is a no-op (the landing page shows as usual). In the Capacitor native shell
 * it redirects the root route to /home (if the user already has a valid session) or /login.
 */
@Injectable({ providedIn: 'root' })
export class NativeLandingGuard implements CanActivate {
  constructor(private router: Router, private auth: AuthService) {}

  canActivate(): boolean | UrlTree {
    if (!Capacitor.isNativePlatform()) {
      return true; // web browser — show the marketing landing
    }
    return this.router.parseUrl(this.auth.isAuthenticated() ? '/home' : '/login');
  }
}
