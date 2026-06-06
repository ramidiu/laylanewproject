import { Component } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-agent-layout',
  template: `
    <div class="agent-layout">
      <aside class="agent-sidenav">
        <div class="agent-sidenav__header">
          <div class="agent-sidenav__logo">
            <img src="assets/images/layla-logo.webp" alt="Layla Money Transfer" style="width:140px;height:auto;" />
          </div>
          <span class="agent-sidenav__role">Agent Portal</span>
        </div>
        <nav class="agent-sidenav__nav">
          <a routerLink="/agent/dashboard" routerLinkActive="active" class="agent-sidenav__item">
            <ion-icon name="grid-outline"></ion-icon>
            <span>Dashboard</span>
          </a>
          <a routerLink="/agent/send" routerLinkActive="active" class="agent-sidenav__item">
            <ion-icon name="send-outline"></ion-icon>
            <span>Send Money</span>
          </a>
          <a routerLink="/agent/commissions" routerLinkActive="active" class="agent-sidenav__item">
            <ion-icon name="cash-outline"></ion-icon>
            <span>Commissions</span>
          </a>
        </nav>
        <div class="agent-sidenav__footer">
          <a class="agent-sidenav__item" (click)="logout()">
            <ion-icon name="log-out-outline"></ion-icon>
            <span>Sign Out</span>
          </a>
        </div>
      </aside>
      <div class="agent-main">
        <header class="agent-toolbar">
          <div class="agent-toolbar__right">
            <div class="agent-toolbar__avatar">{{ initials }}</div>
            <span class="agent-toolbar__name">{{ userName }}</span>
          </div>
        </header>
        <main class="agent-content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `,
  styleUrls: ['./agent-layout.component.scss']
})
export class AgentLayoutComponent {
  constructor(public authService: AuthService) {}

  get initials(): string {
    const user = this.authService.getCurrentUser();
    return user ? (user?.email || '').substring(0, 2).toUpperCase() : 'AG';
  }

  get userName(): string {
    const user = this.authService.getCurrentUser();
    return user ? user?.email?.split('@')[0] || 'Agent' : 'Agent';
  }

  logout(): void {
    this.authService.logout();
  }
}
