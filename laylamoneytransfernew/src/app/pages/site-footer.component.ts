import { Component } from '@angular/core';

/** Shared footer for the public info/legal pages (About, Terms, Privacy, etc.). */
@Component({
  selector: 'app-site-footer',
  template: `
  <footer class="site-footer">
    <p class="site-footer__copy">&copy; 2026 Layla Money Transfer. All Rights Reserved.</p>
    <p class="site-footer__powered">Powered by
      <a href="https://www.remitz.co.uk/" target="_blank" rel="noopener">Remitz</a>. All Rights Reserved.</p>
  </footer>`,
  styles: [`
    .site-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 8px;
      margin-top: 12px;
      padding: 18px 20px 8px;
      border-top: 1px solid #e5e7eb;
    }
    .site-footer__copy { margin: 0; font-size: 0.85rem; color: #6b7280; }
    .site-footer__powered { margin: 0; font-size: 0.8rem; color: #9ca3af; }
    .site-footer__powered a { color: #1B3A6B; text-decoration: underline; transition: color 0.15s; }
    .site-footer__powered a:hover { color: #C0203E; }
  `]
})
export class SiteFooterComponent {}
