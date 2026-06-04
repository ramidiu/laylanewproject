import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { UsiMoneyService, UsiAdminTransaction } from '../../core/services/usi-money.service';

@Component({
  selector: 'app-sa-usi-money',
  templateUrl: './usi-money.page.html',
  styleUrls: ['./usi-money.page.scss']
})
export class SAUsiMoneyPage implements OnInit {

  rows: UsiAdminTransaction[] = [];
  filtered: UsiAdminTransaction[] = [];
  loading = false;
  statusFilter: 'all' | 'new' | 'initiated' | 'sent for pay' | 'paid' | 'cancelled' = 'all';
  searchQuery = '';
  busy: Record<string, boolean> = {};   // referenceNumber → in-flight flag
  selected = new Set<string>();
  // Sticky banner — populated after a successful create/confirm so the operator can copy the new ref.
  lastConfirmedRef: { laylaRef: string; usiRef: string; usiStatus?: string } | null = null;

  constructor(private usi: UsiMoneyService, private toastCtrl: ToastController) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.usi.listTransactions(this.statusFilter, 300).subscribe({
      next: (r) => { this.rows = r; this.applyFilter(); this.loading = false; },
      error: () => { this.rows = []; this.filtered = []; this.loading = false; }
    });
  }

  applyFilter(): void {
    const q = this.searchQuery.trim().toLowerCase();
    this.filtered = !q ? [...this.rows] : this.rows.filter(r =>
      r.referenceNumber?.toLowerCase().includes(q) ||
      (r.senderName  || '').toLowerCase().includes(q) ||
      (r.senderEmail || '').toLowerCase().includes(q) ||
      (r.beneficiaryName || '').toLowerCase().includes(q) ||
      (r.destinationCountry || '').toLowerCase().includes(q));
  }

  // ── Per-row actions ──────────────────────────────────────────────────

  doAction(row: UsiAdminTransaction): void {
    if (this.busy[row.referenceNumber]) return;
    this.busy[row.referenceNumber] = true;

    const done = (msg: string, ok: boolean) => {
      this.busy[row.referenceNumber] = false;
      this.toast(msg, ok ? 'success' : 'danger');
      this.load();
    };

    switch (row.nextAction) {
      case 'CREATE':
        this.usi.createOnUsi(row.referenceNumber).subscribe({
          next: (r) => {
            this.maybeShowRefBanner(row.referenceNumber, r);
            done(this.refMsg(r, 'Created on USI', 'transSessionId'), this.isOk(r));
          },
          error: (e) => done(this.errMsg(e), false)
        });
        break;
      case 'CONFIRM':
        this.usi.confirmOnUsi(row.referenceNumber).subscribe({
          next: (r) => {
            this.maybeShowRefBanner(row.referenceNumber, r);
            done(this.refMsg(r, 'Sent for pay — USI ref:', 'referenceNumber'), this.isOk(r));
          },
          error: (e) => done(this.errMsg(e), false)
        });
        break;
      case 'CHECK_STATUS':
        this.usi.checkStatus(row.referenceNumber).subscribe({
          next: (r) => done(this.successMsg(r, 'Status refreshed'), this.isOk(r)),
          error: (e) => done(this.errMsg(e), false)
        });
        break;
      default:
        this.busy[row.referenceNumber] = false;
    }
  }

  /** Pick the most-meaningful USI reference for display — production payment_token first, then sandbox reference_number. */
  usiRef(r: UsiAdminTransaction): string | null {
    return (r.usiPaymentToken && r.usiPaymentToken.trim()) ? r.usiPaymentToken
         : (r.usiReferenceNumber && r.usiReferenceNumber.trim()) ? r.usiReferenceNumber
         : null;
  }

  /** After a create/confirm response, surface the new USI ref in the top banner so the operator can copy it. */
  private maybeShowRefBanner(laylaRef: string, response: any): void {
    const usiRef = response?.paymentToken || response?.referenceNumber || response?.transSessionId || '';
    if (!usiRef) return;
    this.lastConfirmedRef = {
      laylaRef,
      usiRef,
      usiStatus: response?.usiStatus || response?.status
    };
  }

  dismissRefBanner(): void { this.lastConfirmedRef = null; }

  copyRef(ref: string): void {
    if (!ref) return;
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(ref).then(() => this.toast('USI ref copied: ' + ref, 'success'));
    } else {
      const ta = document.createElement('textarea');
      ta.value = ref;
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); this.toast('USI ref copied: ' + ref, 'success'); } catch {}
      document.body.removeChild(ta);
    }
  }

  /**
   * Toast variant that includes a returned USI id/ref.
   * For create → prefer transSessionId. For confirm → prefer paymentToken (production format
   * like 11036122446378), then referenceNumber (sandbox USIA…).
   */
  private refMsg(r: any, prefix: string, field: string): string {
    const id = r?.[field]
      || r?.paymentToken
      || r?.referenceNumber
      || r?.usiReferenceNumber
      || r?.transSessionId
      || '';
    if (!id) return this.successMsg(r, prefix);
    return `${prefix} ${id}`;
  }

  // ── Bulk actions (use checkbox selection) ────────────────────────────

  toggleSelect(ref: string): void {
    if (this.selected.has(ref)) this.selected.delete(ref);
    else this.selected.add(ref);
  }

  bulkRun(kind: 'CREATE' | 'CONFIRM' | 'CHECK_STATUS'): void {
    const refs = Array.from(this.selected);
    if (!refs.length) { this.toast('Select at least one row', 'warning'); return; }
    const op =
      kind === 'CREATE'       ? this.usi.bulkCreate(refs) :
      kind === 'CONFIRM'      ? this.usi.bulkConfirm(refs) :
                                this.usi.bulkStatus(refs);
    op.subscribe({
      next: (results) => {
        const ok = results.filter((r: any) => (r?.status || '').toUpperCase() === 'SUCCESS').length;
        this.toast(`${ok}/${refs.length} succeeded`, ok === refs.length ? 'success' : 'warning');
        this.selected.clear();
        this.load();
      },
      error: (e) => this.toast(this.errMsg(e), 'danger')
    });
  }

  // ── helpers ──────────────────────────────────────────────────────────

  badgeClass(usiStatus: string | null): string {
    if (!usiStatus) return 'usi-badge usi-badge--new';
    const s = usiStatus.toLowerCase();
    if (s === 'paid')                            return 'usi-badge usi-badge--paid';
    if (s === 'sent for pay')                    return 'usi-badge usi-badge--sent';
    if (s === 'initiated')                       return 'usi-badge usi-badge--init';
    if (s === 'awaiting_compliance')             return 'usi-badge usi-badge--init';
    if (s === 'cancelled' || s.includes('failed') || s === 'failed') return 'usi-badge usi-badge--fail';
    return 'usi-badge usi-badge--info';
  }

  actionLabel(a: string): string {
    return ({ CREATE: 'Create on USI', CONFIRM: 'Confirm', CHECK_STATUS: 'Refresh Status', DONE: '—' } as any)[a] || a;
  }

  private isOk(r: any): boolean {
    return (r?.status || '').toUpperCase() === 'SUCCESS' || !!r?.referenceNumber || !!r?.usiStatus;
  }

  private successMsg(r: any, fallback: string): string {
    return r?.message || fallback;
  }

  private errMsg(e: any): string {
    return e?.error?.message || e?.message || 'Operation failed';
  }

  private async toast(msg: string, color: 'success' | 'danger' | 'warning' = 'success') {
    const t = await this.toastCtrl.create({ message: msg, duration: 2500, color, position: 'top' });
    await t.present();
  }
}
