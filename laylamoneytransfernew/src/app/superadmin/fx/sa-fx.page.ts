import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { FxService } from '../../core/services/fx.service';
import { ConfigService } from '../../core/services/config.service';
import { FxRateResponse } from '../../core/models/fx.model';

@Component({
  selector: 'app-sa-fx',
  templateUrl: './sa-fx.page.html',
  styleUrls: ['./sa-fx.page.scss']
})
export class SAFxPage implements OnInit {
  rates: FxRateResponse[] = [];
  margins: any[] = [];
  loading = true;
  editingMargin: number | null = null;
  editMarginValue = 0;

  // Manual rate mode
  manualMode = false;
  modeToggling = false;

  // Manual rate override
  pairs: { base: string; target: string }[] = [];
  manualRates: Record<string, number> = {};
  editingRate: string | null = null;
  editRateValue: number | null = null;
  savingRate = false;

  private activeReceiveCurrencies: Set<string> = new Set();

  // USI live rate -> customer rate preview (Phase 2, read-only)
  usiRates: any[] = [];
  usiLoading = false;
  usiError = '';

  constructor(private fxService: FxService, private configService: ConfigService, private toastCtrl: ToastController) {}

  ngOnInit(): void { this.loadData(); }

  loadData(): void {
    this.loading = true;
    forkJoin({
      mode: this.fxService.getRateMode().pipe(catchError(() => of({ manualMode: false }))),
      corridors: this.fxService.getCorridors().pipe(catchError(() => of([]))),
      manualRates: this.fxService.getManualRates().pipe(catchError(() => of({}))),
      activeReceive: this.configService.getActiveReceiveCountries().pipe(catchError(() => of([])))
    }).subscribe({
      next: ({ mode, corridors, manualRates, activeReceive }) => {
        this.manualMode = mode.manualMode;
        this.manualRates = manualRates as Record<string, number>;

        // Build active receive currencies from Transfer Config
        const receiveData: any[] = Array.isArray(activeReceive) ? activeReceive : (activeReceive as any)?.data || [];
        this.activeReceiveCurrencies = new Set<string>(receiveData.map((c: any) => c.currency).filter(Boolean));

        const seen = new Set<string>();
        this.pairs = [];
        (corridors as any[])
          .filter((c: any) => c.isActive && c.sendCurrency === 'GBP')
          .filter((c: any) => this.activeReceiveCurrencies.has(c.receiveCurrency))
          .forEach((c: any) => {
            const key = `${c.sendCurrency}_${c.receiveCurrency}`;
            if (!seen.has(key)) {
              seen.add(key);
              this.pairs.push({ base: c.sendCurrency, target: c.receiveCurrency });
            }
          });
        if (this.pairs.length === 0) { this.rates = []; this.loadMargins(); return; }
        forkJoin(
          this.pairs.map(p => this.fxService.getRateForPair(p.base, p.target).pipe(catchError(() => of(null))))
        ).subscribe({
          next: (results) => { this.rates = results.filter(r => r !== null) as FxRateResponse[]; this.loadMargins(); },
          error: () => { this.rates = []; this.loadMargins(); }
        });
      },
      error: () => { this.rates = []; this.loadMargins(); }
    });
  }

  private loadMargins(): void {
    this.fxService.getMargins().subscribe({
      next: (margins) => {
        this.margins = (margins as any[]).filter((m: any) =>
          this.activeReceiveCurrencies.size === 0 || this.activeReceiveCurrencies.has(m.receiveCurrency)
        );
        this.loading = false;
        this.loadUsiRates();
      },
      error: () => { this.margins = []; this.loading = false; this.loadUsiRates(); }
    });
  }

  /** Load live USI getCharges rates (read-only). Runs after margins so the preview can apply them. */
  loadUsiRates(): void {
    this.usiLoading = true; this.usiError = '';
    this.fxService.getUsiRates(100).subscribe({
      next: (rows) => {
        // Only show corridors whose receive currency is an active receive country
        // (mirrors the manual-rate table above). Keeps orphaned/inactive corridors
        // (e.g. a country that was never really added) out of the customer-rate view.
        const all = (rows || []) as any[];
        this.usiRates = this.activeReceiveCurrencies.size === 0
          ? all
          : all.filter((r: any) => this.activeReceiveCurrencies.has(r.receiveCurrency));
        this.usiLoading = false;
      },
      error: () => { this.usiRates = []; this.usiLoading = false; this.usiError = 'Could not load USI rates (USI may be disabled).'; }
    });
  }

  /** Margin % applying to a USI row: exact corridor+method match, else corridor-any-method, else 0. */
  marginPctFor(row: any): number {
    const exact = this.margins.find((m: any) =>
      m.sendCurrency === row.sendCurrency && m.receiveCurrency === row.receiveCurrency && m.deliveryMethod === row.deliveryMethod);
    const anyMethod = this.margins.find((m: any) =>
      m.sendCurrency === row.sendCurrency && m.receiveCurrency === row.receiveCurrency && !m.deliveryMethod);
    const m = exact || anyMethod;
    return m ? (Number(m.marginPercentage) || 0) : 0;
  }

  /** Customer rate = USI rate x (1 - margin%/100). Null when USI had no rate. */
  customerRate(row: any): number | null {
    if (row?.usiRate == null) return null;
    return Number(row.usiRate) * (1 - this.marginPctFor(row) / 100);
  }

  // ── Inline margin editing in the USI table (this is the customer rate) ──────
  editingUsiKey: string | null = null;
  editUsiMarginValue = 0;

  usiRowKey(row: any): string {
    return `${row.sendCurrency}_${row.receiveCurrency}_${row.deliveryMethod}`;
  }

  startEditUsiMargin(row: any): void {
    this.editingUsiKey = this.usiRowKey(row);
    this.editUsiMarginValue = this.marginPctFor(row);
  }

  cancelUsiMargin(): void { this.editingUsiKey = null; }

  /** Live customer-rate preview while editing (uses the in-progress margin value). */
  previewCustomerRate(row: any): number | null {
    if (row?.usiRate == null) return null;
    const pct = this.editingUsiKey === this.usiRowKey(row) ? Number(this.editUsiMarginValue) : this.marginPctFor(row);
    return Number(row.usiRate) * (1 - (isNaN(pct) ? 0 : pct) / 100);
  }

  /** Save the margin for this corridor + payout method: update the existing row, or create one. */
  saveUsiMargin(row: any): void {
    const pct = Number(this.editUsiMarginValue);
    if (isNaN(pct) || pct < 0 || pct > 100) { this.showToast('Margin must be between 0 and 100%', 'warning'); return; }
    const existing = this.margins.find((m: any) =>
      m.sendCurrency === row.sendCurrency && m.receiveCurrency === row.receiveCurrency && m.deliveryMethod === row.deliveryMethod);
    const done = () => { this.editingUsiKey = null; this.showToast(`Margin saved: ${row.sendCurrency}/${row.receiveCurrency} ${row.deliveryMethod} = ${pct}%`, 'success'); this.loadData(); };
    if (existing && existing.id) {
      this.fxService.updateMargin(existing.id, pct).subscribe({ next: done, error: () => this.showToast('Failed to update margin', 'danger') });
    } else {
      this.fxService.createMargin({
        sendCurrency: row.sendCurrency, receiveCurrency: row.receiveCurrency,
        deliveryMethod: row.deliveryMethod, marginPercentage: pct, isActive: true
      }).subscribe({ next: done, error: () => this.showToast('Failed to create margin', 'danger') });
    }
  }

  toggleRateMode(): void {
    this.modeToggling = true;
    this.fxService.setRateMode(!this.manualMode).subscribe({
      next: () => {
        this.manualMode = !this.manualMode;
        this.modeToggling = false;
        this.showToast(
          this.manualMode ? 'Manual rate mode enabled — set your custom rates below' : 'Automatic rate mode enabled — rates come from live API',
          this.manualMode ? 'warning' : 'success'
        );
      },
      error: () => { this.modeToggling = false; this.showToast('Failed to change rate mode', 'danger'); }
    });
  }

  pairKey(pair: { base: string; target: string }): string {
    return `${pair.base}_${pair.target}`;
  }

  getManualRate(pair: { base: string; target: string }): number | null {
    return this.manualRates[this.pairKey(pair)] ?? null;
  }

  startEditRate(pair: { base: string; target: string }): void {
    this.editingRate = this.pairKey(pair);
    this.editRateValue = this.manualRates[this.pairKey(pair)] ?? null;
  }

  saveRate(pair: { base: string; target: string }): void {
    if (!this.editRateValue || this.editRateValue <= 0) {
      this.showToast('Rate must be greater than 0', 'warning');
      return;
    }
    this.savingRate = true;
    this.fxService.setManualRate(pair.base, pair.target, this.editRateValue).subscribe({
      next: () => {
        this.manualRates[this.pairKey(pair)] = this.editRateValue!;
        this.editingRate = null;
        this.editRateValue = null;
        this.savingRate = false;
        this.showToast(`Rate set: 1 ${pair.base} = ${this.manualRates[this.pairKey(pair)]} ${pair.target}`, 'success');
        this.loadData();
      },
      error: () => { this.savingRate = false; this.showToast('Failed to save rate', 'danger'); }
    });
  }

  clearRate(pair: { base: string; target: string }): void {
    this.fxService.clearManualRate(pair.base, pair.target).subscribe({
      next: () => {
        delete this.manualRates[this.pairKey(pair)];
        this.showToast(`Manual rate cleared for ${pair.base}/${pair.target}`, 'success');
        this.loadData();
      },
      error: () => this.showToast('Failed to clear rate', 'danger')
    });
  }

  cancelRateEdit(): void { this.editingRate = null; this.editRateValue = null; }

  loadRates(): void { this.loadData(); }

  startEditMargin(margin: any): void { this.editingMargin = margin.id; this.editMarginValue = margin.marginPercentage; }

  saveMargin(margin: any): void {
    this.fxService.updateMargin(margin.id, this.editMarginValue).subscribe({
      next: () => { this.editingMargin = null; this.showToast('Margin updated successfully', 'success'); this.loadData(); },
      error: () => this.showToast('Failed to update margin', 'danger')
    });
  }

  cancelEdit(): void { this.editingMargin = null; }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
