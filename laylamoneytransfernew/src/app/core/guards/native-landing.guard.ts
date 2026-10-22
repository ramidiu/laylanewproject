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
    if (!this.isNativeApp()) {
      return true; // real web browser — show the marketing landing
    }
    return this.router.parseUrl(this.auth.isAuthenticated() ? '/home' : '/login');
  }

  /**
   * Detect that we're running inside the mobile app (Capacitor webview) — robustly, because
   * Capacitor.isNativePlatform() can return false when the app loads a REMOTE url (the bridge is
   * not always injected on remote pages). We also look at the Capacitor global directly and at the
   * Android System WebView user-agent marker ("; wv)"), which a normal mobile browser never has — so
   * there are no false positives for real Safari/Chrome users.
   */
  private isNativeApp(): boolean {
    try { if (Capacitor.isNativePlatform()) return true; } catch { /* ignore */ }
    const w = window as any;
    const cap = w.Capacitor;
    if (cap && (cap.isNative === true ||
        (typeof cap.getPlatform === 'function' && cap.getPlatform() !== 'web'))) {
      return true;
    }
    // Android System WebView embeds "; wv)" in the UA; Chrome/Safari do not.
    if (/;\s*wv\)/.test(navigator.userAgent || '')) return true;
    return false;
  }
}
