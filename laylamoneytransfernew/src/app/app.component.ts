import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-root',
  template: '<ion-app><ion-router-outlet></ion-router-outlet></ion-app>'
})
export class AppComponent implements OnInit {
  // Filename of the main JS bundle this page actually loaded. When a new build is
  // deployed the bundle hash changes, so we detect it by comparing this against the
  // latest index.html on the server — and reload automatically. This means a client
  // never has to manually refresh to pick up a new version.
  private runningBundle = '';

  ngOnInit(): void {
    this.runningBundle = this.mainBundle(
      Array.from(document.querySelectorAll('script[src]'))
        .map(s => (s as HTMLScriptElement).getAttribute('src') || '')
    );
    // Check on a timer and whenever the tab is brought back into focus.
    setInterval(() => this.checkForNewVersion(), 2 * 60 * 1000); // every 2 minutes
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') this.checkForNewVersion();
    });
  }

  /** Extract the hashed main bundle filename (e.g. "main.ab12cd.js") from a list of srcs. */
  private mainBundle(srcs: string[]): string {
    const hit = srcs.find(s => /(^|\/)main\.[^/]*\.js(\?|$)/.test(s));
    return hit ? hit.replace(/^.*\//, '').replace(/\?.*$/, '') : '';
  }

  private async checkForNewVersion(): Promise<void> {
    try {
      const res = await fetch('index.html', { cache: 'no-store' });
      if (!res.ok) return;
      const html = await res.text();
      const latest = this.mainBundle(html.match(/[^"'\s()]+\.js/g) || []);
      // Reload once when the deployed main bundle differs from the running one.
      if (latest && this.runningBundle && latest !== this.runningBundle
          && sessionStorage.getItem('reloadedForBuild') !== latest) {
        sessionStorage.setItem('reloadedForBuild', latest);
        location.reload();
      }
    } catch {
      /* offline or transient — ignore, try again next tick */
    }
  }
}
