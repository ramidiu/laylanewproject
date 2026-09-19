import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-root',
  template: '<ion-app><ion-router-outlet></ion-router-outlet></ion-app>'
})
export class AppComponent implements OnInit {
  // Signature of ALL hashed entry bundles this page loaded (runtime, polyfills, main…).
  // When a new build is deployed, at least the `runtime` bundle's hash changes — even
  // when only a lazy-loaded feature (e.g. Send Money / Create Transaction) changed and
  // `main.js` stays identical. Comparing the whole set against the latest index.html on
  // the server lets us detect ANY new build and reload automatically, so a client never
  // has to manually refresh. (The old code watched only `main.js` and missed lazy-chunk
  // deploys — hence changes appeared only after a manual refresh.)
  private runningSig = '';

  ngOnInit(): void {
    this.runningSig = this.bundleSig(
      Array.from(document.querySelectorAll('script[src]'))
        .map(s => (s as HTMLScriptElement).getAttribute('src') || '')
    );
    // Check on a timer and whenever the tab is brought back into focus.
    setInterval(() => this.checkForNewVersion(), 2 * 60 * 1000); // every 2 minutes
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') this.checkForNewVersion();
    });
  }

  /** Build a stable signature from every hashed entry bundle (runtime/main/polyfills/…). */
  private bundleSig(srcs: string[]): string {
    return srcs
      .map(s => s.replace(/^.*\//, '').replace(/\?.*$/, ''))
      .filter(s => /^(runtime|main|polyfills|scripts|vendor)\.[^/]*\.js$/.test(s))
      .sort()
      .join('|');
  }

  private async checkForNewVersion(): Promise<void> {
    try {
      const res = await fetch('index.html', { cache: 'no-store' });
      if (!res.ok) return;
      const html = await res.text();
      const latest = this.bundleSig(html.match(/[^"'\s()]+\.js/g) || []);
      // Reload once when ANY deployed entry bundle differs from what we're running.
      if (latest && this.runningSig && latest !== this.runningSig
          && sessionStorage.getItem('reloadedForBuild') !== latest) {
        sessionStorage.setItem('reloadedForBuild', latest);
        location.reload();
      }
    } catch {
      /* offline or transient — ignore, try again next tick */
    }
  }
}
