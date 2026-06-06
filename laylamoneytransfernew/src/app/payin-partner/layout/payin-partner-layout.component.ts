import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LanguageService } from '../../core/services/language.service';

@Component({
  selector: 'app-payin-partner-layout',
  template: `
    <div class="payin-layout" [class.collapsed]="sidenavCollapsed" [class.mobile-open]="mobileMenuOpen">
      <div class="payin-backdrop" *ngIf="mobileMenuOpen" (click)="closeMobileMenu()"></div>
      <!-- Sidenav -->
      <aside class="payin-sidenav">
        <div class="payin-sidenav__header">
          <div class="payin-sidenav__logo">
            <img src="assets/images/layla-logo.webp" class="logo-white" alt="Layla Money Transfer" [style.width]="sidenavCollapsed ? '40px' : '140px'" style="height:auto;" />
          </div>
          <span class="payin-sidenav__role" *ngIf="!sidenavCollapsed">Pay-In Partner</span>
          <span class="payin-sidenav__admin-badge" *ngIf="!sidenavCollapsed && isAdminViewing">Viewing: {{ adminPartnerName }}</span>
          <button class="payin-sidenav__toggle" (click)="toggleSidenav()">
            <ion-icon [name]="sidenavCollapsed ? 'chevron-forward' : 'chevron-back'"></ion-icon>
          </button>
        </div>

        <nav class="payin-sidenav__nav">
          <a
            *ngFor="let item of menuItems"
            [routerLink]="item.route"
            class="payin-sidenav__item"
            [class.active]="isActive(item.route)"
            (click)="onNavClick()"
          >
            <ion-icon [name]="item.icon"></ion-icon>
            <span *ngIf="!sidenavCollapsed">{{ item.label }}</span>
          </a>
        </nav>

        <div class="payin-sidenav__footer">
          <a class="payin-sidenav__item payin-sidenav__item--switch" *ngIf="isAdminViewing" (click)="switchToAdmin()">
            <ion-icon name="arrow-back-outline"></ion-icon>
            <span *ngIf="!sidenavCollapsed">Switch to Admin</span>
          </a>
          <div *ngIf="isAdminViewing" style="border-top:1px solid rgba(255,255,255,0.1);margin:4px 12px;"></div>
          <a class="payin-sidenav__item" (click)="logout()">
            <ion-icon name="log-out-outline"></ion-icon>
            <span *ngIf="!sidenavCollapsed">Sign Out</span>
          </a>
        </div>
      </aside>

      <!-- Main Content -->
      <div class="payin-main">
        <!-- Top Toolbar -->
        <header class="payin-toolbar">
          <div class="payin-toolbar__left">
            <button class="payin-toolbar__menu-btn" (click)="toggleMobileMenu()">
              <ion-icon name="menu-outline"></ion-icon>
            </button>
          </div>
          <div class="payin-toolbar__right">
            <button class="payin-toolbar__lang-btn" (click)="toggleLanguage()" title="Switch Language">
              <span class="lang-label">{{ isRtl ? 'EN' : 'عر' }}</span>
            </button>
            <button class="payin-toolbar__notification-btn">
              <ion-icon name="notifications-outline"></ion-icon>
            </button>
            <div class="payin-toolbar__user">
              <div class="payin-toolbar__avatar">{{ initials }}</div>
              <span class="payin-toolbar__username">{{ userName }}</span>
            </div>
          </div>
        </header>

        <!-- Router Outlet -->
        <main class="payin-content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `,
  styleUrls: ['./payin-partner-layout.component.scss']
})
export class PayinPartnerLayoutComponent {
  sidenavCollapsed = false;
  mobileMenuOpen = false;

  menuItems = [
    { label: 'Dashboard', icon: 'grid-outline', route: '/payin-partner/dashboard' },
    { label: 'Customers', icon: 'people-outline', route: '/payin-partner/customers' },
    { label: 'Create Customer', icon: 'person-add-outline', route: '/payin-partner/create-customer' },
    { label: 'Transactions', icon: 'swap-horizontal-outline', route: '/payin-partner/transactions' },
    { label: 'Create Transaction', icon: 'add-circle-outline', route: '/payin-partner/create-transaction' },
    { label: 'Ledger', icon: 'book-outline', route: '/payin-partner/ledger' },
    { label: 'Settlements', icon: 'wallet-outline', route: '/payin-partner/settlements' }
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
    if (!user) return 'PI';
    return (user?.email || '').substring(0, 2).toUpperCase();
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
