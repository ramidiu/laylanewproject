import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { PartnerService } from '../../core/services/partner.service';
import { FxService } from '../../core/services/fx.service';
import { ConfigService } from '../../core/services/config.service';

@Component({
  selector: 'app-corridor-management',
  templateUrl: './corridor-management.page.html',
  styleUrls: ['./corridor-management.page.scss']
})
export class CorridorManagementPage implements OnInit {
  corridors: any[] = [];
  corridorConfigs: any[] = [];
  mergedCorridors: any[] = [];
  mappings: any[] = [];
  partners: any[] = [];
  payinPartners: any[] = [];
  payoutPartners: any[] = [];
  loading = true;
  showAddForm = false;
  activeSegment: 'corridors' | 'mappings' | 'fees' = 'corridors';

  // Inline config panel
  expandedCorridor: string | null = null; // "GBP-INR" key
  configForm: any = {};
  savingConfig = false;

  // Corridor Fees
  corridorFees: any[] = [];
  showAddFeeForm = false;
  editingFeeId: number | null = null;
  newFee: any = {
    corridorId: null,
    deliveryMethod: 'BANK_DEPOSIT',
    feeType: 'FLAT',
    flatFee: 0,
    percentageFee: 0,
    currency: 'GBP'
  };
  editFeeForm: any = {};
  tierRules: any[] = [];
  newFeeTierRules: any[] = [];

  newMapping = {
    fromCurrency: '',
    toCurrency: '',
    partnerId: null as number | null,
    payinPartnerId: null as number | null
  };

  // Dynamic currency lists and filtered partners
  sendCurrencies: string[] = [];
  receiveCurrencies: string[] = [];
  filteredPartners: any[] = [];
  partnerCountryMap: Map<number, string[]> = new Map();

  // Active payment/payout types per currency (from transfer-config)
  // Used to filter corridor list to only those with both sides enabled
  activeSendCurrencies: Set<string> = new Set();
  activeReceiveCurrencies: Set<string> = new Set();

  // Edit mapping
  editingMappingId: number | null = null;
  editMappingForm: any = {}; // partnerId -> [currency codes]

  constructor(
    private partnerService: PartnerService,
    private fxService: FxService,
    private configService: ConfigService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadAll();
    this.loadActiveCurrencies();
    this.loadPartnerCountries();
  }

  loadAll(): void {
    this.loadActiveTransferConfig();
    this.loadCorridors();
    this.loadCorridorConfigs();
    this.loadMappings();
    this.loadPartners();
    this.loadPayinPartners();
    this.loadPayoutPartners();
    this.loadCorridorFees();
  }

  /** Load the set of currencies with at least one ACTIVE payment_method (send) and payout_type (receive) */
  loadActiveTransferConfig(): void {
    this.configService.getActiveCountries().subscribe({
      next: (res: any) => {
        const data = res?.data || res || [];
        this.activeSendCurrencies = new Set((data as any[]).map(c => c.currency).filter(Boolean));
        this.mergeCorridors();
      },
      error: () => { this.activeSendCurrencies = new Set(); this.mergeCorridors(); }
    });
    this.configService.getActiveReceiveCountries().subscribe({
      next: (res: any) => {
        const data = res?.data || res || [];
        this.activeReceiveCurrencies = new Set((data as any[]).map(c => c.currency).filter(Boolean));
        this.mergeCorridors();
      },
      error: () => { this.activeReceiveCurrencies = new Set(); this.mergeCorridors(); }
    });
  }

  loadCorridors(): void {
    this.loading = true;
    this.fxService.getCorridors().subscribe({
      next: (corridors) => {
        this.corridors = corridors || [];
        this.mergeCorridors();
        this.loading = false;
      },
      error: () => { this.corridors = []; this.mergeCorridors(); this.loading = false; }
    });
  }

  loadCorridorConfigs(): void {
    this.partnerService.getCorridorConfigs().subscribe({
      next: (res) => {
        this.corridorConfigs = Array.isArray(res) ? res : res?.data || [];
        this.mergeCorridors();
      },
      error: () => { this.corridorConfigs = []; this.mergeCorridors(); }
    });
  }

  loadMappings(): void {
    this.partnerService.getCorridorMappings().subscribe({
      next: (res) => this.mappings = Array.isArray(res) ? res : res?.data || [],
      error: () => this.mappings = []
    });
  }

  loadPartners(): void {
    this.partnerService.getPayoutPartners().subscribe({
      next: (res) => this.partners = Array.isArray(res) ? res : res?.data || [],
      error: () => {}
    });
  }

  loadPayinPartners(): void {
    this.partnerService.getPayinPartners().subscribe({
      next: (res) => this.payinPartners = Array.isArray(res) ? res : res?.data || [],
      error: () => this.payinPartners = []
    });
  }

  loadPayoutPartners(): void {
    this.partnerService.getPayoutPartners().subscribe({
      next: (res) => this.payoutPartners = Array.isArray(res) ? res : res?.data || [],
      error: () => this.payoutPartners = []
    });
  }

  mergeCorridors(): void {
    const configMap = new Map<string, any>();
    this.corridorConfigs.forEach(cfg => {
      configMap.set(`${cfg.fromCurrency}-${cfg.toCurrency}`, cfg);
    });

    const seen = new Set<string>();
    const merged: any[] = [];

    // Code added by Naresh: block corridor fallback when config empty
    // When NO active transfer config exists (both sets empty), show zero corridors
    // instead of falling through to show all 28. Only corridors backed by active
    // payment methods (send) AND active payout types (receive) are displayed.
    const hasSendConfig = this.activeSendCurrencies.size > 0;
    const hasReceiveConfig = this.activeReceiveCurrencies.size > 0;

    // Commented by Naresh: removed unsafe all-corridor fallback
    // Old: const hasActiveConfig = hasSendConfig || hasReceiveConfig;
    //      const sendActive = !hasActiveConfig || this.activeSendCurrencies.has(...)
    //      When both empty, !hasActiveConfig was true → every corridor passed.

    // Add corridors from FX corridors list
    this.corridors.forEach(c => {
      const key = `${c.sendCurrency}-${c.receiveCurrency}`;
      seen.add(key);
      const cfg = configMap.get(key);
      const sendActive = hasSendConfig && this.activeSendCurrencies.has(c.sendCurrency);
      const receiveActive = hasReceiveConfig && this.activeReceiveCurrencies.has(c.receiveCurrency);
      // Only include corridors where BOTH sides are configured/active
      if (!sendActive || !receiveActive) return;
      merged.push({
        fromCurrency: c.sendCurrency,
        toCurrency: c.receiveCurrency,
        sendCountry: c.sendCountry,
        receiveCountry: c.receiveCountry,
        isActive: c.isActive,
        corridorId: c.id,
        config: cfg || null,
        sendActive,
        receiveActive
      });
    });

    // Add any configs that don't have a matching FX corridor
    this.corridorConfigs.forEach(cfg => {
      const key = `${cfg.fromCurrency}-${cfg.toCurrency}`;
      if (!seen.has(key)) {
        const sendActive = hasSendConfig && this.activeSendCurrencies.has(cfg.fromCurrency);
        const receiveActive = hasReceiveConfig && this.activeReceiveCurrencies.has(cfg.toCurrency);
        if (!sendActive || !receiveActive) return;
        seen.add(key);
        merged.push({
          fromCurrency: cfg.fromCurrency,
          toCurrency: cfg.toCurrency,
          sendCountry: null,
          receiveCountry: null,
          isActive: cfg.isActive,
          corridorId: null,
          config: cfg,
          sendActive,
          receiveActive
        });
      }
    });

    this.mergedCorridors = merged;
    this.rebuildFeeGrid();
  }

  getPayinLabel(corridor: any): string {
    if (corridor.config?.payinPartnerName) return corridor.config.payinPartnerName;
    return 'Direct';
  }

  getPayoutLabel(corridor: any): string {
    if (corridor.config?.payoutPartnerName) return corridor.config.payoutPartnerName;
    return 'None';
  }

  hasPayinPartner(corridor: any): boolean {
    return !!corridor.config?.payinPartnerId;
  }

  hasPayoutPartner(corridor: any): boolean {
    return !!corridor.config?.payoutPartnerId;
  }

  corridorKey(corridor: any): string {
    return `${corridor.fromCurrency}-${corridor.toCurrency}`;
  }

  toggleConfigPanel(corridor: any): void {
    const key = this.corridorKey(corridor);
    if (this.expandedCorridor === key) {
      this.expandedCorridor = null;
      return;
    }
    this.expandedCorridor = key;
    const cfg = corridor.config;
    this.configForm = {
      payinPartnerId: cfg?.payinPartnerId || null,
      payinShareType: cfg?.payinShareType || 'PERCENTAGE',
      payinShareValue: cfg?.payinShareValue || 0,
      payoutPartnerId: cfg?.payoutPartnerId || null,
      payoutShareType: cfg?.payoutShareType || 'PERCENTAGE',
      payoutShareValue: cfg?.payoutShareValue || 0
    };
  }

  getActualCorridorFee(c: any): { amount: number; currency: string; label: string } {
    if (!c) return { amount: 5.00, currency: 'GBP', label: '5.00' };
    // Find the active BANK_DEPOSIT fee for this corridor
    const fee = this.corridorFees.find(
      (f: any) => f.corridorId === c.corridorId && f.deliveryMethod === 'BANK_DEPOSIT' && f.isActive !== false
    ) || this.corridorFees.find((f: any) => f.corridorId === c.corridorId);

    if (!fee) return { amount: 5.00, currency: c.fromCurrency || 'GBP', label: '5.00' };

    const currency = fee.currency || c.fromCurrency || 'GBP';
    if (fee.feeType === 'FLAT') {
      const amt = parseFloat(fee.flatFee) || 0;
      return { amount: amt, currency, label: amt.toFixed(2) };
    }
    if (fee.feeType === 'PERCENTAGE') {
      // Use a £100 sample to illustrate the percentage
      const pct = parseFloat(fee.percentageFee) || 0;
      const amt = 100 * pct / 100;
      return { amount: amt, currency, label: `${pct}% of amount` };
    }
    return { amount: 5.00, currency, label: '5.00' };
  }

  getFeeSplitPreview(c?: any): { payin: string; admin: string; payout: string; total: string; currency: string; label: string } {
    const feeInfo = this.getActualCorridorFee(c);
    const actualFee = feeInfo.amount;
    let payinShare = 0;
    let payoutShare = 0;

    if (this.configForm.payinShareType === 'PERCENTAGE') {
      payinShare = actualFee * (this.configForm.payinShareValue || 0) / 100;
    } else {
      payinShare = this.configForm.payinShareValue || 0;
    }

    if (this.configForm.payoutShareType === 'PERCENTAGE') {
      payoutShare = actualFee * (this.configForm.payoutShareValue || 0) / 100;
    } else {
      payoutShare = this.configForm.payoutShareValue || 0;
    }

    const adminShare = Math.max(0, actualFee - payinShare - payoutShare);
    return {
      payin: payinShare.toFixed(2),
      admin: adminShare.toFixed(2),
      payout: payoutShare.toFixed(2),
      total: actualFee.toFixed(2),
      currency: feeInfo.currency,
      label: feeInfo.label
    };
  }

  saveCorridorConfig(corridor: any): void {
    this.savingConfig = true;
    const data = {
      payinPartnerId: this.configForm.payinPartnerId || null,
      payinShareType: this.configForm.payinShareType,
      payinShareValue: this.configForm.payinShareValue || 0,
      payoutPartnerId: this.configForm.payoutPartnerId || null,
      payoutShareType: this.configForm.payoutShareType,
      payoutShareValue: this.configForm.payoutShareValue || 0
    };

    this.partnerService.updateCorridorConfig(corridor.fromCurrency, corridor.toCurrency, data).subscribe({
      next: () => {
        this.showToast('Corridor fee config saved', 'success');
        this.expandedCorridor = null;
        this.savingConfig = false;
        this.loadCorridorConfigs();
      },
      error: () => {
        this.showToast('Failed to save corridor config', 'danger');
        this.savingConfig = false;
      }
    });
  }

  cancelConfig(): void {
    this.expandedCorridor = null;
  }

  // ── Partner Mappings Tab (unchanged logic) ──

  toggleCorridor(corridor: any): void {
    this.fxService.toggleCorridor(corridor.corridorId, !corridor.isActive).subscribe({
      next: () => {
        this.showToast(`Corridor ${!corridor.isActive ? 'enabled' : 'disabled'}`, 'success');
        this.loadCorridors();
      },
      error: () => this.showToast('Failed to toggle corridor', 'danger')
    });
  }

  get mappingsFromConfigs(): any[] {
    // Single source of truth: only show mappings for currency pairs that are present
    // in mergedCorridors (already filtered by Transfer Config). Prevents rows for
    // corridors whose send/receive currency has been disabled in Transfer Config.
    const validPairs = new Set(
      this.mergedCorridors.map(m => `${m.fromCurrency}-${m.toCurrency}`)
    );
    return this.corridorConfigs.filter((cfg: any) =>
      (cfg.payinPartnerId != null || cfg.payoutPartnerId != null) &&
      validPairs.has(`${cfg.fromCurrency}-${cfg.toCurrency}`)
    );
  }

  addMapping(): void {
    if (!this.newMapping.fromCurrency || !this.newMapping.toCurrency) return;
    this.partnerService.updateCorridorConfig(this.newMapping.fromCurrency, this.newMapping.toCurrency, {
      payinPartnerId: this.newMapping.payinPartnerId || null,
      payinShareType: 'PERCENTAGE',
      payinShareValue: 0,
      payoutPartnerId: this.newMapping.partnerId || null,
      payoutShareType: 'PERCENTAGE',
      payoutShareValue: 0
    }).subscribe({
      next: () => {
        this.showToast('Corridor mapping saved', 'success');
        this.showAddForm = false;
        this.newMapping = { fromCurrency: '', toCurrency: '', partnerId: null, payinPartnerId: null };
        this.loadCorridorConfigs();
      },
      error: () => this.showToast('Failed to save mapping', 'danger')
    });
  }

  deleteMapping(mapping: any): void {
    // Clear partners from corridor_fee_config rather than deleting the row
    this.partnerService.updateCorridorConfig(mapping.fromCurrency, mapping.toCurrency, {
      payinPartnerId: null,
      payinShareType: 'PERCENTAGE',
      payinShareValue: 0,
      payoutPartnerId: null,
      payoutShareType: 'PERCENTAGE',
      payoutShareValue: 0
    }).subscribe({
      next: () => {
        this.showToast('Partner assignment removed', 'success');
        this.loadCorridorConfigs();
      },
      error: () => this.showToast('Failed to remove mapping', 'danger')
    });
  }

  getPartnerName(partnerId: number): string {
    const p = this.partners.find(x => x.id === partnerId || x.id === Number(partnerId));
    return p ? (p.partnerName || p.name) : `Partner #${partnerId}`;
  }

  getPartnerForCorridor(fromCurrency: string, toCurrency: string): number | null {
    const mapping = this.mappings.find(m => m.fromCurrency === fromCurrency && m.toCurrency === toCurrency && m.isActive);
    return mapping ? mapping.partnerId : null;
  }

  // ── Corridor Fees Tab ──────────────────────────────────────

  // Code added by Naresh: dynamic fee grid driven by active corridors × active payout types.
  // No "Add Fee" button needed — grid auto-generates from Transfer Config. Admin only edits fee values.

  // Map payout_type DB names to delivery method names used in corridor_fees
  private payoutToDeliveryFee: Record<string, string> = {
    'BANK_TRANSFER': 'BANK_DEPOSIT',
    'MOBILE_MONEY': 'MOBILE_WALLET',
    'CASH_COLLECTION': 'CASH_PICKUP',
    'UPI': 'UPI',
    'HOME_DELIVERY': 'HOME_DELIVERY',
    'AIRTIME_TOPUP': 'AIRTIME_TOPUP'
  };

  // Code added by Naresh: converted getters to cached arrays to prevent the
  // infinite-CD-loop freeze. Getters created new objects every cycle → ngFor
  // saw them as "new" → re-rendered → triggered more CD → page froze.
  // Now rebuilt only when underlying data changes (via rebuildFeeGrid).

  availableCorridorsForFees: any[] = [];
  filteredCorridorFees: any[] = [];

  private rebuildFeeGrid(): void {
    // Available corridors (same as mergedCorridors but mapped for fee context)
    this.availableCorridorsForFees = this.mergedCorridors
      .filter(m => m.corridorId != null)
      .map(m => ({
        id: m.corridorId,
        sendCurrency: m.fromCurrency,
        receiveCurrency: m.toCurrency
      }));

    // Dynamic fee grid: corridor × active payout type, merged with existing corridor_fees
    const feeMap = new Map<string, any>();
    for (const f of this.corridorFees) {
      feeMap.set(`${f.corridorId}-${f.deliveryMethod}`, f);
    }

    const result: any[] = [];
    for (const corridor of this.availableCorridorsForFees) {
      const activePT = this.activePayoutTypesPerCurrency.get(corridor.receiveCurrency) || [];
      for (const pt of activePT) {
        const dm = this.payoutToDeliveryFee[pt] || pt;
        const key = `${corridor.id}-${dm}`;
        const existing = feeMap.get(key);
        if (existing) {
          result.push(existing);
        } else {
          result.push({
            id: null,
            corridorId: corridor.id,
            deliveryMethod: dm,
            feeType: 'FLAT',
            flatFee: 0,
            percentageFee: 0,
            currency: corridor.sendCurrency,
            isActive: true,
            _placeholder: true
          });
        }
      }
    }
    this.filteredCorridorFees = result;
  }

  // Active payout types grouped by currency — loaded from Transfer Config
  activePayoutTypesPerCurrency: Map<string, string[]> = new Map();

  loadCorridorFees(): void {
    this.fxService.getCorridorFees().subscribe({
      next: (fees) => {
        this.corridorFees = Array.isArray(fees) ? fees : [];
        this.rebuildFeeGrid();
      },
      error: () => { this.corridorFees = []; this.rebuildFeeGrid(); }
    });
    // Code added by Naresh: load ALL payout types (not deduped by country) to drive
    // the dynamic fee grid. Uses /admin/payout-types which returns every row, then
    // filters client-side to isActive=true.
    this.configService.getPayoutTypes().subscribe({
      next: (res: any) => {
        const data: any[] = Array.isArray(res) ? res : (res?.data || res || []);
        const map = new Map<string, string[]>();
        for (const pt of data) {
          if (!pt.isActive) continue;
          const curr = pt.currency;
          const type = pt.payoutType;
          if (!curr || !type) continue;
          if (!map.has(curr)) map.set(curr, []);
          const list = map.get(curr)!;
          if (!list.includes(type)) list.push(type);
        }
        this.activePayoutTypesPerCurrency = map;
        this.rebuildFeeGrid();
      },
      error: () => { this.activePayoutTypesPerCurrency = new Map(); this.rebuildFeeGrid(); }
    });
  }

  getCorridorLabel(corridorId: number): string {
    const c = this.corridors.find(x => x.id === corridorId);
    if (c) return `${c.sendCurrency} \u2192 ${c.receiveCurrency}`;
    return `Corridor #${corridorId}`;
  }

  getCorridorCurrencies(corridorId: number): { send: string; receive: string } | null {
    // Look up from the same source the Add-Fee dropdown uses, so the displayed pair
    // and the saved corridorId always agree. Falls back to the raw corridors list if
    // mergedCorridors hasn't loaded yet.
    const fromMerged = this.mergedCorridors.find((m: any) => m.corridorId === corridorId);
    if (fromMerged) return { send: fromMerged.fromCurrency, receive: fromMerged.toCurrency };
    const c = this.corridors.find((x: any) => x.id === corridorId);
    if (c) return { send: c.sendCurrency, receive: c.receiveCurrency };
    return null;
  }

  // Code added by Naresh: trackBy prevents ngFor from destroying/recreating DOM on every CD cycle
  trackByFee(_i: number, fee: any): string {
    return fee.id != null ? `db-${fee.id}` : `${fee.corridorId}-${fee.deliveryMethod}`;
  }

  formatDeliveryMethod(method: string): string {
    const map: Record<string, string> = {
      'BANK_DEPOSIT': 'Bank Deposit',
      'MOBILE_WALLET': 'Mobile Wallet',
      'CASH_PICKUP': 'Cash Pickup',
      'HOME_DELIVERY': 'Home Delivery',
      'AIRTIME_TOPUP': 'Airtime Topup'
    };
    return map[method] || method;
  }

  formatFeeType(type: string): string {
    const map: Record<string, string> = {
      'FLAT': 'Flat Fee',
      'PERCENTAGE': 'Percentage',
      'TIERED': 'Tiered'
    };
    return map[type] || type;
  }

  getFeeAmountDisplay(fee: any): string {
    if (fee.feeType === 'FLAT') return `${fee.currency || 'GBP'} ${(fee.flatFee || 0).toFixed(2)}`;
    if (fee.feeType === 'PERCENTAGE') return `${(fee.percentageFee || 0).toFixed(2)}%`;
    if (fee.feeType === 'TIERED') return this.formatTierRules(fee.tierRules);
    return '-';
  }

  isTieredFee(fee: any): boolean {
    return fee.feeType === 'TIERED';
  }

  formatTierRules(tierRulesJson: string | any[]): string {
    try {
      const rules = typeof tierRulesJson === 'string' ? JSON.parse(tierRulesJson) : tierRulesJson;
      if (!Array.isArray(rules) || rules.length === 0) return 'No tiers configured';
      return rules.map((r: any) => {
        const maxLabel = r.maxAmount ? (r.maxAmount >= 1000 ? (r.maxAmount / 1000) + 'K' : r.maxAmount) : null;
        const minLabel = r.minAmount >= 1000 ? (r.minAmount / 1000) + 'K' : r.minAmount;
        const range = maxLabel ? `\u00A3${minLabel}-${maxLabel}` : `\u00A3${minLabel}+`;
        const fee = r.flatFee !== undefined && r.flatFee !== null ? `\u00A3${r.flatFee}` : `${r.percentageFee}%`;
        const feeTypeLabel = r.flatFee !== undefined && r.flatFee !== null ? ' flat' : '';
        return `${range}: ${fee}${feeTypeLabel}`;
      }).join('\n');
    } catch {
      return 'Invalid rules';
    }
  }

  onNewFeeTypeChange(feeType: string): void {
    if (feeType === 'TIERED' && this.newFeeTierRules.length === 0) {
      this.newFeeTierRules = [
        { minAmount: 0, maxAmount: 200, type: 'flat', flatFee: 4.99, percentageFee: null },
        { minAmount: 200, maxAmount: 1000, type: 'flat', flatFee: 9.99, percentageFee: null },
        { minAmount: 1000, maxAmount: 5000, type: 'percentage', flatFee: null, percentageFee: 1.5 },
        { minAmount: 5000, maxAmount: null, type: 'percentage', flatFee: null, percentageFee: 1.0 }
      ];
    }
  }

  onEditFeeTypeChange(feeType: string): void {
    if (feeType === 'TIERED' && this.tierRules.length === 0) {
      this.tierRules = [
        { minAmount: 0, maxAmount: 200, type: 'flat', flatFee: 4.99, percentageFee: null },
        { minAmount: 200, maxAmount: 1000, type: 'flat', flatFee: 9.99, percentageFee: null },
        { minAmount: 1000, maxAmount: 5000, type: 'percentage', flatFee: null, percentageFee: 1.5 },
        { minAmount: 5000, maxAmount: null, type: 'percentage', flatFee: null, percentageFee: 1.0 }
      ];
    }
  }

  addTier(target: 'new' | 'edit' = 'edit'): void {
    const rules = target === 'new' ? this.newFeeTierRules : this.tierRules;
    const lastTier = rules[rules.length - 1];
    rules.push({
      minAmount: lastTier?.maxAmount || 0,
      maxAmount: null,
      type: 'flat',
      flatFee: 0,
      percentageFee: null
    });
  }

  removeTier(index: number, target: 'new' | 'edit' = 'edit'): void {
    const rules = target === 'new' ? this.newFeeTierRules : this.tierRules;
    rules.splice(index, 1);
  }

  prepareTierRulesForSave(rules: any[]): string {
    return JSON.stringify(rules.map((t: any) => {
      const rule: any = { minAmount: t.minAmount, maxAmount: t.maxAmount || null };
      if (t.type === 'flat') { rule.flatFee = t.flatFee; }
      else { rule.percentageFee = t.percentageFee; }
      return rule;
    }));
  }

  toggleAddFeeForm(): void {
    this.showAddFeeForm = !this.showAddFeeForm;
    if (this.showAddFeeForm) {
      this.newFee = {
        corridorId: null,
        deliveryMethod: 'BANK_DEPOSIT',
        feeType: 'FLAT',
        flatFee: 0,
        percentageFee: 0,
        currency: 'GBP'
      };
      this.newFeeTierRules = [];
    }
  }

  saveFee(): void {
    if (!this.newFee.corridorId) return;
    const payload: any = {
      deliveryMethod: this.newFee.deliveryMethod,
      feeType: this.newFee.feeType,
      flatFee: this.newFee.flatFee || 0,
      percentageFee: this.newFee.percentageFee || 0,
      currency: this.newFee.currency || 'GBP'
    };
    if (this.newFee.feeType === 'TIERED' && this.newFeeTierRules.length > 0) {
      payload.tierRules = this.prepareTierRulesForSave(this.newFeeTierRules);
    }
    this.fxService.createCorridorFee(this.newFee.corridorId, payload).subscribe({
      next: () => {
        this.showToast('Corridor fee created', 'success');
        this.showAddFeeForm = false;
        this.newFeeTierRules = [];
        this.loadCorridorFees();
      },
      error: () => this.showToast('Failed to create corridor fee', 'danger')
    });
  }

  startEditFee(fee: any): void {
    // Code added by Naresh: placeholder rows have id=null; use a temp key so ngIf works
    this.editingFeeId = fee.id ?? `${fee.corridorId}-${fee.deliveryMethod}`;
    this.editFeeForm = {
      deliveryMethod: fee.deliveryMethod,
      feeType: fee.feeType,
      flatFee: fee.flatFee || 0,
      percentageFee: fee.percentageFee || 0,
      currency: fee.currency || 'GBP'
    };
    this.tierRules = [];
    if (fee.feeType === 'TIERED' && fee.tierRules) {
      try {
        const rules = typeof fee.tierRules === 'string' ? JSON.parse(fee.tierRules) : fee.tierRules;
        this.tierRules = rules.map((r: any) => ({
          minAmount: r.minAmount,
          maxAmount: r.maxAmount,
          type: r.flatFee !== undefined && r.flatFee !== null ? 'flat' : 'percentage',
          flatFee: r.flatFee || null,
          percentageFee: r.percentageFee || null
        }));
      } catch {
        this.tierRules = [];
      }
    }
  }

  cancelEditFee(): void {
    this.editingFeeId = null;
    this.editFeeForm = {};
    this.tierRules = [];
  }

  saveEditFee(fee: any): void {
    const payload: any = {
      deliveryMethod: this.editFeeForm.deliveryMethod || fee.deliveryMethod,
      feeType: this.editFeeForm.feeType,
      flatFee: this.editFeeForm.flatFee || 0,
      percentageFee: this.editFeeForm.percentageFee || 0,
      currency: this.editFeeForm.currency || 'GBP'
    };
    if (this.editFeeForm.feeType === 'TIERED' && this.tierRules.length > 0) {
      payload.tierRules = this.prepareTierRulesForSave(this.tierRules);
    }

    // Code added by Naresh: placeholder rows have no DB id — create instead of update.
    // This enables the dynamic grid: Transfer Config drives which rows appear; admin
    // clicks "Configure" to set the fee values, which creates the corridor_fees row.
    const save$ = fee._placeholder
        ? this.fxService.createCorridorFee(fee.corridorId, payload)
        : this.fxService.updateCorridorFee(fee.corridorId, fee.id, payload);

    save$.subscribe({
      next: () => {
        this.showToast(fee._placeholder ? 'Corridor fee configured' : 'Corridor fee updated', 'success');
        this.editingFeeId = null;
        this.tierRules = [];
        this.loadCorridorFees();
      },
      error: () => this.showToast('Failed to save corridor fee', 'danger')
    });
  }

  deleteFee(fee: any): void {
    if (!confirm(`Delete this ${this.formatDeliveryMethod(fee.deliveryMethod)} fee for ${this.getCorridorLabel(fee.corridorId)}?`)) return;
    this.fxService.deleteCorridorFee(fee.id).subscribe({
      next: () => {
        this.showToast('Corridor fee deleted', 'success');
        this.loadCorridorFees();
      },
      error: () => this.showToast('Failed to delete corridor fee', 'danger')
    });
  }

  startEditMapping(mapping: any): void {
    this.editingMappingId = mapping.id;
    this.editMappingForm = {
      payinPartnerId: mapping.payinPartnerId || null,
      payoutPartnerId: mapping.payoutPartnerId || null
    };
  }

  cancelEditMapping(): void {
    this.editingMappingId = null;
    this.editMappingForm = {};
  }

  saveEditMapping(mapping: any): void {
    this.partnerService.updateCorridorConfig(mapping.fromCurrency, mapping.toCurrency, {
      payinPartnerId: this.editMappingForm.payinPartnerId || null,
      payinShareType: mapping.payinShareType || 'PERCENTAGE',
      payinShareValue: mapping.payinShareValue || 0,
      payoutPartnerId: this.editMappingForm.payoutPartnerId || null,
      payoutShareType: mapping.payoutShareType || 'PERCENTAGE',
      payoutShareValue: mapping.payoutShareValue || 0
    }).subscribe({
      next: () => {
        this.showToast('Mapping updated', 'success');
        this.editingMappingId = null;
        this.loadCorridorConfigs();
      },
      error: () => this.showToast('Failed to update mapping', 'danger')
    });
  }

  getPayinPartnerForCorridor(fromCurrency: string, toCurrency: string): string {
    const config = this.corridorConfigs.find(c => c.fromCurrency === fromCurrency && c.toCurrency === toCurrency);
    if (config?.payinPartnerName) return config.payinPartnerName;
    return 'Direct';
  }

  // --- Dynamic currency & partner filtering ---

  loadActiveCurrencies(): void {
    // Dynamic only — no hardcoded fallbacks. If the API is down, the dropdowns stay
    // empty and the admin sees an empty state rather than stale/invented options.
    this.configService.getActiveCountries().subscribe({
      next: (res: any) => {
        const countries = res?.data || res || [];
        const currencies = new Set<string>();
        (Array.isArray(countries) ? countries : []).forEach((c: any) => {
          if (c.currency) currencies.add(c.currency);
        });
        this.sendCurrencies = [...currencies].sort();
      },
      error: () => this.sendCurrencies = []
    });

    this.configService.getActiveReceiveCountries().subscribe({
      next: (res: any) => {
        const countries = res?.data || res || [];
        const currencies = new Set<string>();
        (Array.isArray(countries) ? countries : []).forEach((c: any) => {
          if (c.currency) currencies.add(c.currency);
        });
        this.receiveCurrencies = [...currencies].sort();
      },
      error: () => this.receiveCurrencies = []
    });
  }

  loadPartnerCountries(): void {
    // Load each partner's assigned countries to filter by currency
    this.partnerService.getPayoutPartners().subscribe({
      next: (res: any) => {
        const partners = Array.isArray(res) ? res : res?.data || [];
        partners.forEach((p: any) => {
          this.partnerService.getPartnerCountries(p.id).subscribe({
            next: (cRes: any) => {
              const countries = Array.isArray(cRes) ? cRes : cRes?.data || [];
              this.partnerCountryMap.set(p.id, countries.map((c: any) => c.currency || c.countryCode));
            },
            error: () => {}
          });
        });
      },
      error: () => {}
    });
  }

  onToCurrencyChange(): void {
    // Filter partners by the selected "To Currency"
    const toCurrency = this.newMapping.toCurrency;
    if (!toCurrency) {
      this.filteredPartners = this.partners;
      return;
    }
    this.filteredPartners = this.partners.filter(p => {
      const currencies = this.partnerCountryMap.get(p.id) || [];
      return currencies.length === 0 || currencies.includes(toCurrency);
    });
    // Reset partner selection if current is no longer valid
    if (this.newMapping.partnerId && !this.filteredPartners.find(p => p.id === this.newMapping.partnerId)) {
      this.newMapping.partnerId = null;
    }
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
