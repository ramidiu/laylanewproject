import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LanguageService } from '../../core/services/language.service';

@Component({
  selector: 'app-partner-layout',
  template: `
    <div class="partner-layout" [class.collapsed]="sidenavCollapsed" [class.mobile-open]="mobileMenuOpen">
      <div class="partner-backdrop" *ngIf="mobileMenuOpen" (click)="closeMobileMenu()"></div>
      <aside class="partner-sidenav">
        <div class="partner-sidenav__header">
          <div class="partner-sidenav__logo">
            <img src="assets/images/layla-logo.webp" alt="Layla Money Transfer" [style.width]="sidenavCollapsed ? '40px' : '140px'" style="height:auto;" />
          </div>
          <span class="partner-sidenav__role" *ngIf="!sidenavCollapsed">Payout Partner</span>
          <span class="partner-sidenav__admin-badge" *ngIf="!sidenavCollapsed && isAdminViewing">Viewing: {{ adminPartnerName }}</span>
          <button class="partner-sidenav__toggle" (click)="toggleSidenav()">
            <ion-icon [name]="sidenavCollapsed ? 'chevron-forward' : 'chevron-back'"></ion-icon>
          </button>
        </div>
        <nav class="partner-sidenav__nav">
          <a
            *ngFor="let item of menuItems"
            [routerLink]="item.route"
            class="partner-sidenav__item"
            [class.active]="isActive(item.route)"
            (click)="onNavClick()"
          >
            <ion-icon [name]="item.icon"></ion-icon>
            <span *ngIf="!sidenavCollapsed">{{ item.label }}</span>
          </a>
        </nav>
        <div class="partner-sidenav__footer">
          <a class="partner-sidenav__item partner-sidenav__item--switch" *ngIf="isAdminViewing" (click)="switchToAdmin()">
            <ion-icon name="arrow-back-outline"></ion-icon>
            <span *ngIf="!sidenavCollapsed">Switch to Admin</span>
          </a>
          <div *ngIf="isAdminViewing" style="border-top:1px solid rgba(255,255,255,0.1);margin:4px 12px;"></div>
          <a class="partner-sidenav__item" (click)="logout()">
            <ion-icon name="log-out-outline"></ion-icon>
            <span *ngIf="!sidenavCollapsed">Sign Out</span>
          </a>
        </div>
      </aside>
      <div class="partner-main">
        <header class="partner-toolbar">
          <div class="partner-toolbar__left">
            <button class="partner-toolbar__menu-btn" (click)="toggleMobileMenu()">
              <ion-icon name="menu-outline"></ion-icon>
            </button>
          </div>
          <div class="partner-toolbar__right">
            <button class="partner-toolbar__lang-btn" (click)="toggleLanguage()" title="Switch Language">
              <span class="lang-label">{{ isRtl ? 'EN' : 'عر' }}</span>
            </button>
            <button class="partner-toolbar__notification-btn">
              <ion-icon name="notifications-outline"></ion-icon>
            </button>
            <div class="partner-toolbar__user">
              <div class="partner-toolbar__avatar">{{ initials }}</div>
              <span class="partner-toolbar__name">{{ userName }}</span>
            </div>
          </div>
        </header>
        <main class="partner-content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `,
  styleUrls: ['./partner-layout.component.scss']
})
export class PartnerLayoutComponent {
  sidenavCollapsed = false;
  mobileMenuOpen = false;

  menuItems = [
    { label: 'Dashboard', icon: 'grid-outline', route: '/partner/dashboard' },
    { label: 'Pending Payouts', icon: 'time-outline', route: '/partner/transactions' },
    { label: 'Completed', icon: 'checkmark-circle-outline', route: '/partner/completed' },
    { label: 'Ledger', icon: 'book-outline', route: '/partner/ledger' }
  ];

  constructor(
    public authService: AuthService,
    private router: Router,
    public languageService: LanguageService
  ) {}

  get isAdminViewing(): boolean {
    return !!sessionStorage.getItem('fb_admin_return');
  }

  get adminReturnRoute(): string {
    return sessionStorage.getItem('fb_admin_return') || '/admin';
  }

  get adminPartnerName(): string {
    return sessionStorage.getItem('fb_admin_partner_name') || 'Partner';
  }

  switchToAdmin(): void {
    const route = this.adminReturnRoute;
    sessionStorage.removeItem('fb_admin_return');
    sessionStorage.removeItem('fb_admin_partner_id');
    sessionStorage.removeItem('fb_admin_partner_name');
    window.location.href = route;
  }

  get initials(): string {
    const user = this.authService.getCurrentUser();
    return user ? (user?.email || '').substring(0, 2).toUpperCase() : 'PP';
  }

  get userName(): string {
    const user = this.authService.getCurrentUser();
    return user ? user?.email?.split('@')[0] || 'Partner' : 'Partner';
  }

  toggleSidenav(): void {
    this.sidenavCollapsed = !this.sidenavCollapsed;
  }

  toggleMobileMenu(): void { this.mobileMenuOpen = !this.mobileMenuOpen; }
  closeMobileMenu(): void { this.mobileMenuOpen = false; }
  onNavClick(): void { if (window.innerWidth < 992) this.mobileMenuOpen = false; }

  isActive(route: string): boolean {
    return this.router.url.startsWith(route);
  }

  toggleLanguage(): void { this.languageService.toggleLanguage(); }
  get isRtl(): boolean { return this.languageService.isRtl; }

  logout(): void {
    this.authService.logout();
  }
}
