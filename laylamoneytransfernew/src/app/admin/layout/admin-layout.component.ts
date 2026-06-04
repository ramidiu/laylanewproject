import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { LanguageService } from '../../core/services/language.service';

interface MenuItem {
  label: string;
  icon: string;
  route: string;
}

interface MenuGroup {
  label: string;
  items: MenuItem[];
}

@Component({
  selector: 'app-admin-layout',
  templateUrl: './admin-layout.component.html',
  styleUrls: ['./admin-layout.component.scss']
})
export class AdminLayoutComponent implements OnInit, OnDestroy {
  sidenavCollapsed = false;
  mobileMenuOpen = false;
  menuGroups: MenuGroup[] = [];
  roleLabel = 'Admin';
  notificationCount = 0;
  private notificationSub?: Subscription;

  constructor(
    public authService: AuthService,
    private router: Router,
    private notificationService: NotificationService,
    public languageService: LanguageService
  ) {}

  ngOnInit(): void {
    this.buildMenu();
    this.notificationService.startPolling();
    this.notificationSub = this.notificationService.unreadCount$.subscribe(count => {
      this.notificationCount = count;
    });
  }

  ngOnDestroy(): void {
    this.notificationSub?.unsubscribe();
  }

  openNotifications(): void {
    this.router.navigate(['/admin/notifications']);
  }

  private buildMenu(): void {
    const user = this.authService.getCurrentUser();
    const roles: string[] = user?.roles || [];

    if (roles.includes('ADMIN') || roles.includes('SUPER_ADMIN')) {
      this.roleLabel = 'Admin';
      this.menuGroups = [
        {
          label: 'Overview',
          items: [{ label: 'Dashboard', icon: 'grid-outline', route: '/admin/dashboard' }]
        },
        {
          label: 'User Management',
          items: [
            { label: 'Users', icon: 'people-outline', route: '/admin/users' },
            { label: 'KYC Review', icon: 'document-text-outline', route: '/admin/kyc-review' }
          ]
        },
        {
          label: 'Transactions',
          items: [
            { label: 'Transactions', icon: 'swap-horizontal-outline', route: '/admin/transactions' }
          ]
        },
        {
          label: 'Partners',
          items: [
            { label: 'Pay-In Partners', icon: 'cash-outline', route: '/admin/payin-partners' },
            { label: 'Pay-In Customers', icon: 'people-outline', route: '/admin/payin-customers' }
          ]
        },
        {
          label: 'Configuration',
          items: [
            { label: 'FX Rates', icon: 'trending-up-outline', route: '/admin/fx' },
            { label: 'Corridors', icon: 'git-network-outline', route: '/admin/corridors' }
          ]
        },
        {
          label: 'Support',
          items: [
            { label: 'Support Tickets', icon: 'chatbubble-ellipses-outline', route: '/admin/support' }
          ]
        }
      ];
    } else if (roles.includes('COMPLIANCE_OFFICER')) {
      this.roleLabel = 'Compliance';
      this.menuGroups = [
        {
          label: 'Overview',
          items: [{ label: 'Dashboard', icon: 'grid-outline', route: '/admin/dashboard' }]
        },
        {
          label: 'User Management',
          items: [
            { label: 'Users', icon: 'people-outline', route: '/admin/users' }
          ]
        },
        {
          label: 'Compliance',
          items: [
            { label: 'Alerts & Cases', icon: 'shield-checkmark-outline', route: '/admin/compliance' },
            { label: 'Transactions', icon: 'swap-horizontal-outline', route: '/admin/transactions' }
          ]
        }
      ];
    } else if (roles.includes('FINANCE')) {
      this.roleLabel = 'Finance';
      this.menuGroups = [
        {
          label: 'Overview',
          items: [{ label: 'Dashboard', icon: 'grid-outline', route: '/admin/dashboard' }]
        },
        {
          label: 'Financial',
          items: [
            { label: 'Transactions', icon: 'swap-horizontal-outline', route: '/admin/transactions' }
          ]
        }
      ];
    } else if (roles.includes('TREASURY_MANAGER')) {
      this.roleLabel = 'Treasury';
      this.menuGroups = [
        {
          label: 'Overview',
          items: [{ label: 'Dashboard', icon: 'grid-outline', route: '/admin/dashboard' }]
        },
        {
          label: 'FX Management',
          items: [
            { label: 'FX Rates', icon: 'trending-up-outline', route: '/admin/fx' },
            { label: 'Referrals', icon: 'gift-outline', route: '/admin/corridors' }
          ]
        }
      ];
    } else if (roles.includes('SUPPORT')) {
      this.roleLabel = 'Support';
      this.menuGroups = [
        {
          label: 'Overview',
          items: [{ label: 'Dashboard', icon: 'grid-outline', route: '/admin/dashboard' }]
        },
        {
          label: 'View Only',
          items: [
            { label: 'Users', icon: 'people-outline', route: '/admin/users' },
            { label: 'Transactions', icon: 'swap-horizontal-outline', route: '/admin/transactions' },
            { label: 'Compliance', icon: 'shield-checkmark-outline', route: '/admin/compliance' }
          ]
        },
        {
          label: 'Support',
          items: [
            { label: 'Support Tickets', icon: 'chatbubble-ellipses-outline', route: '/admin/support' }
          ]
        }
      ];
    } else {
      // Fallback
      this.roleLabel = 'Staff';
      this.menuGroups = [
        {
          label: 'Overview',
          items: [{ label: 'Dashboard', icon: 'grid-outline', route: '/admin/dashboard' }]
        }
      ];
    }
  }

  get initials(): string {
    const user = this.authService.getCurrentUser();
    if (!user) return 'AD';
    return (user?.email || '').substring(0, 2).toUpperCase();
  }

  get userName(): string {
    const user = this.authService.getCurrentUser();
    return user ? user?.email?.split('@')[0] || 'Admin' : 'Admin';
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

  get canViewPartnerPortals(): boolean {
    const roles = this.authService.getUserRoles();
    return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN');
  }

  viewAsPayoutPartner(): void {
    sessionStorage.setItem('fb_admin_return', '/admin');
    this.router.navigate(['/partner']);
  }

  viewAsPayinPartner(): void {
    sessionStorage.setItem('fb_admin_return', '/admin');
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
