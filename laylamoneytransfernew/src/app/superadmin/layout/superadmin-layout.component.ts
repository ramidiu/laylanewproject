import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { LanguageService } from '../../core/services/language.service';

@Component({
  selector: 'app-superadmin-layout',
  templateUrl: './superadmin-layout.component.html',
  styleUrls: ['./superadmin-layout.component.scss']
})
export class SuperAdminLayoutComponent implements OnInit, OnDestroy {
  sidenavCollapsed = false;
  mobileMenuOpen = false;
  notificationCount = 0;
  private notificationSub?: Subscription;

  menuGroups = [
    {
      label: 'Overview',
      items: [
        { label: 'Executive Dashboard', icon: 'bar-chart-outline', route: '/superadmin/dash-executive' },
        { label: 'Dashboard', icon: 'grid-outline', route: '/superadmin/dashboard' }
      ]
    },
    {
      label: 'User Management',
      items: [
        { label: 'User Dashboard', icon: 'bar-chart-outline', route: '/superadmin/dash-users' },
        { label: 'Users', icon: 'people-outline', route: '/superadmin/users' },
        { label: 'KYC Review', icon: 'document-text-outline', route: '/superadmin/kyc-review' }
      ]
    },
    {
      label: 'Transactions',
      items: [
        { label: 'Transaction Dashboard', icon: 'bar-chart-outline', route: '/superadmin/dash-transactions' },
        { label: 'Transactions', icon: 'swap-horizontal-outline', route: '/superadmin/transactions' }
      ]
    },
    {
      label: 'Partners',
      items: [
        { label: 'Partner Dashboard', icon: 'bar-chart-outline', route: '/superadmin/dash-partners' },
        { label: 'Payout Partners', icon: 'business-outline', route: '/superadmin/partners' },
        { label: 'Pay-In Partners', icon: 'cash-outline', route: '/superadmin/payin-partners' },
        { label: 'PayIn Transactions', icon: 'swap-horizontal-outline', route: '/superadmin/payin-transactions' },
        { label: 'PayIn Customers', icon: 'people-outline', route: '/superadmin/payin-customers' },
        { label: 'USI Money', icon: 'send-outline', route: '/superadmin/usi-money' }
      ]
    },
    {
      label: 'Configuration',
      items: [
        { label: 'Corridor Dashboard', icon: 'bar-chart-outline', route: '/superadmin/dash-corridors' },
        { label: 'Corridors', icon: 'globe-outline', route: '/superadmin/corridor-management' },
        { label: 'FX Rates', icon: 'trending-up-outline', route: '/superadmin/fx' },
        { label: 'Transfer Config', icon: 'options-outline', route: '/superadmin/transfer-config' }
      ]
    },
    {
      label: 'Support',
      items: [
        { label: 'Support Dashboard', icon: 'bar-chart-outline', route: '/superadmin/dash-support' },
        { label: 'Support Tickets', icon: 'chatbubble-ellipses-outline', route: '/superadmin/support' }
      ]
    },
    {
      label: 'System',
      items: [] as any[]
    }
  ];

  constructor(
    public authService: AuthService,
    private router: Router,
    private notificationService: NotificationService,
    public languageService: LanguageService
  ) {}

  private get isSuperAdmin(): boolean {
    return this.authService.getUserRoles().includes('SUPER_ADMIN');
  }

  ngOnInit(): void {
    const systemGroup = this.menuGroups.find(g => g.label === 'System');
    if (systemGroup) {
      if (this.isSuperAdmin) {
        systemGroup.items.push({ label: 'System Health', icon: 'pulse-outline', route: '/superadmin/dash-system' });
        systemGroup.items.push({ label: 'Demo Users', icon: 'people-circle-outline', route: '/superadmin/demo-users' });
      }
      systemGroup.items.push({ label: 'Email Templates', icon: 'mail-outline', route: '/superadmin/email-templates' });
      systemGroup.items.push({ label: 'Audit Logs', icon: 'document-text-outline', route: '/superadmin/audit-logs' });
      if (this.isSuperAdmin) {
        systemGroup.items.push({ label: 'Security Settings', icon: 'shield-outline', route: '/superadmin/security-settings' });
        systemGroup.items.push({ label: 'System Controls', icon: 'toggle-outline', route: '/superadmin/system-controls' });
      }
    }
    this.notificationService.startPolling();
    this.notificationSub = this.notificationService.unreadCount$.subscribe(count => {
      this.notificationCount = count;
    });
  }

  ngOnDestroy(): void {
    this.notificationSub?.unsubscribe();
  }

  openNotifications(): void {
    this.router.navigate(['/superadmin/notifications']);
  }

  get initials(): string {
    const user = this.authService.getCurrentUser();
    if (!user) return 'SA';
    return (user?.email || '').substring(0, 2).toUpperCase();
  }

  get userName(): string {
    const user = this.authService.getCurrentUser();
    return user ? user?.email?.split('@')[0] || 'Super Admin' : 'Super Admin';
  }

  toggleSidenav(): void {
    this.sidenavCollapsed = !this.sidenavCollapsed;
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen = false;
  }

  onNavClick(): void {
    if (window.innerWidth < 992) {
      this.mobileMenuOpen = false;
    }
  }

  isActive(route: string): boolean {
    const url = this.router.url.split('?')[0];
    return url === route || url.startsWith(route + '/');
  }

  viewAsPayoutPartner(): void {
    sessionStorage.setItem('fb_admin_return', '/superadmin');
    this.router.navigate(['/partner']);
  }

  viewAsPayinPartner(): void {
    sessionStorage.setItem('fb_admin_return', '/superadmin');
    this.router.navigate(['/payin-partner']);
  }

  toggleLanguage(): void {
    this.languageService.toggleLanguage();
  }

  get isRtl(): boolean {
    return this.languageService.isRtl;
  }

  logout(): void {
    this.authService.logout('/admin-login');
  }
}
