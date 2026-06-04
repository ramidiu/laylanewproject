import { Component, OnInit } from '@angular/core';
import { UserService } from '../../core/services/user.service';
import { TransactionService } from '../../core/services/transaction.service';
import { ComplianceService } from '../../core/services/compliance.service';
import { PartnerService } from '../../core/services/partner.service';
import { SettlementService } from '../../core/services/settlement.service';
import { FxService } from '../../core/services/fx.service';
import { ConfigService } from '../../core/services/config.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-superadmin-dashboard',
  templateUrl: './superadmin-dashboard.page.html',
  styleUrls: ['./superadmin-dashboard.page.scss']
})
export class SuperAdminDashboardPage implements OnInit {
  cards = [
    { label: 'Users', icon: 'people-outline', route: '/superadmin/users', count: '0', color: 'navy' },
    { label: 'KYC Review', icon: 'shield-checkmark-outline', route: '/superadmin/kyc-review', count: '0', color: 'warning' },
    { label: 'Transactions', icon: 'swap-horizontal-outline', route: '/superadmin/transactions', count: '0', color: 'sky' },
    { label: 'Pay-In Partners', icon: 'cash-outline', route: '/superadmin/payin-partners', count: '0', color: 'success' },
    { label: 'Payout Partners', icon: 'business-outline', route: '/superadmin/partners', count: '0', color: 'info' },
    { label: 'Corridors', icon: 'globe-outline', route: '/superadmin/corridor-management', count: '0', color: 'navy' }
  ];

  loading = true;

  constructor(
    private userService: UserService,
    private transactionService: TransactionService,
    private complianceService: ComplianceService,
    private partnerService: PartnerService,
    private settlementService: SettlementService,
    private fxService: FxService,
    private configService: ConfigService,
    private authService: AuthService
  ) {}

  get isSuperAdmin(): boolean {
    return this.authService.getUserRoles().includes('SUPER_ADMIN');
  }

  ngOnInit(): void {
    this.loadCounts();
  }

  loadCounts(): void {
    this.loading = true;

    this.userService.listUsers({ page: 0, size: 1 }).subscribe({
      next: (res) => this.cards[0].count = res.totalElements?.toLocaleString() || '0',
      error: () => {}
    });

    // KYC Review = users with PENDING kyc status (not compliance alerts)
    this.userService.listUsers({ page: 0, size: 1, kycStatus: 'PENDING' }).subscribe({
      next: (res) => this.cards[1].count = res.totalElements?.toLocaleString() || '0',
      error: () => {}
    });

    this.transactionService.list({ page: 0, size: 1 }).subscribe({
      next: (res) => {
        this.cards[2].count = res.totalElements?.toLocaleString() || '0';
        this.loading = false;
      },
      error: () => this.loading = false
    });

    this.partnerService.getPayinPartners().subscribe({
      next: (res) => this.cards[3].count = (Array.isArray(res) ? res.length : res?.data?.length || 0).toString(),
      error: () => {}
    });

    this.partnerService.getPayoutPartners().subscribe({
      next: (res) => this.cards[4].count = (Array.isArray(res) ? res.length : res?.data?.length || 0).toString(),
      error: () => {}
    });

    this.fxService.getCorridors().subscribe({
      next: (res: any) => {
        const list = Array.isArray(res) ? res : (res?.content || []);
        this.cards[5].count = list.filter((c: any) => c.isActive).length.toString();
      },
      error: () => {}
    });
  }
}
