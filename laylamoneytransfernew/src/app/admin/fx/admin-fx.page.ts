import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { FxService } from '../../core/services/fx.service';
import { FxRateResponse } from '../../core/models/fx.model';

@Component({
  selector: 'app-admin-fx',
  templateUrl: './admin-fx.page.html',
  styleUrls: ['./admin-fx.page.scss']
})
export class AdminFxPage implements OnInit {
  rates: FxRateResponse[] = [];
  margins: any[] = [];
  loading = true;
  editingMargin: number | null = null;
  editMarginValue = 0;

  // Manual rate override
  manualMode = false;
  pairs: { base: string; target: string }[] = [];
  manualRates: Record<string, number> = {};
  editingRate: string | null = null;   // key = "BASE_TARGET"
  editRateValue: number | null = null;
  savingRate = false;

  constructor(private fxService: FxService, private toastCtrl: ToastController) {}

  ngOnInit(): void { this.loadData(); }

  loadData(): void {
    this.loading = true;
    forkJoin({
      mode: this.fxService.getRateMode().pipe(catchError(() => of({ manualMode: false }))),
      corridors: this.fxService.getCorridors().pipe(catchError(() => of([]))),
      manualRates: this.fxService.getManualRates().pipe(catchError(() => of({})))
    }).subscribe({
      next: ({ mode, corridors, manualRates }) => {
        this.manualMode = mode.manualMode;
        this.manualRates = manualRates as Record<string, number>;
        const seen = new Set<string>();
        this.pairs = [];
        (corridors as any[]).filter((c: any) => c.isActive && c.sendCurrency === 'GBP').forEach((c: any) => {
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

  loadMargins(): void {
    this.fxService.getMargins().subscribe({
      next: (margins) => { this.margins = margins; this.loading = false; },
      error: () => { this.margins = []; this.loading = false; }
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
