import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  section: 'main' | 'account';
}

@Component({
  selector: 'app-tabs',
  templateUrl: './tabs.page.html',
  styleUrls: ['./tabs.page.scss']
})
export class TabsPage implements OnInit, OnDestroy {
  sidebarCollapsed = false;
  mobileMenuOpen = false;
  userName = '';
  userInitials = '';
  userEmail = '';
  notificationCount = 0;
  private notificationSub?: Subscription;

  mainNav: NavItem[] = [
    { label: 'NAV.DASHBOARD', icon: 'grid-outline', route: '/home/dashboard', section: 'main' },
    { label: 'NAV.SEND_MONEY', icon: 'send-outline', route: '/home/send', section: 'main' },
    { label: 'NAV.TRANSACTIONS', icon: 'swap-horizontal-outline', route: '/home/transactions', section: 'main' },
    { label: 'NAV.RECIPIENTS', icon: 'people-outline', route: '/home/beneficiaries', section: 'main' },
    { label: 'NAV.WALLET', icon: 'wallet-outline', route: '/home/wallet', section: 'main' },
  ];

  accountNav: NavItem[] = [
    { label: 'NAV.MY_PROFILE', icon: 'person-outline', route: '/home/profile', section: 'account' },
    { label: 'NAV.MY_DOCUMENTS', icon: 'document-text-outline', route: '/home/kyc', section: 'account' },
    { label: 'NAV.REFERRAL', icon: 'gift-outline', route: '/home/referral', section: 'account' },
    { label: 'NAV.SUPPORT', icon: 'chatbubble-ellipses-outline', route: '/home/support', section: 'account' },
  ];

  // Bottom tabs (mobile only) - subset of main nav
  bottomTabs = [
    { label: 'NAV.HOME', icon: 'home-outline', route: '/home/dashboard' },
    { label: 'NAV.SEND', icon: 'send-outline', route: '/home/send' },
    { label: 'NAV.TRANSFERS', icon: 'receipt-outline', route: '/home/transactions' },
    { label: 'NAV.RECIPIENTS', icon: 'people-outline', route: '/home/beneficiaries' },
    { label: 'NAV.MY_PROFILE', icon: 'person-outline', route: '/home/profile' },
  ];

  constructor(
    public router: Router,
    private authService: AuthService,
    private notificationService: NotificationService
  ) {}

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    if (user) {
      this.userEmail = user.email || '';
      // Extract name from email prefix as fallback
      const emailName = this.userEmail.split('@')[0] || '';
      this.userName = emailName;
      this.userInitials = (emailName[0] || 'U').toUpperCase();
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
    this.router.navigateByUrl('/home/notifications');
    this.closeMobileMenu();
  }

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen = false;
  }

  navigateTo(route: string): void {
    this.router.navigateByUrl(route);
    this.closeMobileMenu();
  }

  onLogout(): void {
    this.authService.logout();
  }

  get isFullscreenPage(): boolean {
    return this.router.url.startsWith('/home/kyc') && this.router.url.includes('fromRegistration=true');
  }

  isActive(route: string): boolean {
    return this.router.url.startsWith(route);
  }
}
