import { Component, OnInit } from '@angular/core';
import { SettlementService } from '../../core/services/settlement.service';

@Component({
  selector: 'app-admin-ledger',
  templateUrl: './admin-ledger.page.html',
  styleUrls: ['./admin-ledger.page.scss']
})
export class AdminLedgerPage implements OnInit {
  activeSegment: 'platform' | 'balances' = 'platform';

  platformEntries: any[] = [];
  platformLoading = true;

  partnerBalances: any[] = [];
  balancesLoading = true;

  constructor(private settlementService: SettlementService) {}

  ngOnInit(): void {
    this.loadPlatformLedger();
    this.loadPartnerBalances();
  }

  loadPlatformLedger(): void {
    this.platformLoading = true;
    this.settlementService.getPlatformLedger().subscribe({
      next: (res) => {
        const data = res?.data || res;
        this.platformEntries = data?.entries || (Array.isArray(data) ? data : []);
        this.platformLoading = false;
      },
      error: () => { this.platformEntries = []; this.platformLoading = false; }
    });
  }

  loadPartnerBalances(): void {
    this.balancesLoading = true;
    this.settlementService.getPartnerBalances().subscribe({
      next: (res) => {
        this.partnerBalances = Array.isArray(res) ? res : res?.data || [];
        this.balancesLoading = false;
      },
      error: () => { this.partnerBalances = []; this.balancesLoading = false; }
    });
  }

  getEntryTypeColor(type: string): string {
    if (type === 'CREDIT' || type === 'INFLOW') return 'success';
    if (type === 'DEBIT' || type === 'OUTFLOW') return 'danger';
    return 'medium';
  }

  getTotalCredits(): number {
    return this.platformEntries
      .filter(e => e.entryType === 'CREDIT')
      .reduce((sum, e) => sum + (e.usdAmount || 0), 0);
  }

  getTotalDebits(): number {
    return this.platformEntries
      .filter(e => e.entryType === 'DEBIT')
      .reduce((sum, e) => sum + (e.usdAmount || 0), 0);
  }

  getNetBalance(): number {
    return this.getTotalCredits() - this.getTotalDebits();
  }
}
