import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin-usi-pnl',
  template: `
  <ion-content>
    <div class="pnl">
      <div class="pnl-head">
        <h1>USI Money — P&amp;L</h1>
        <p class="pnl-sub">Profit = the fee charged (no FX-margin). USI commission shown for reference.</p>
        <div class="pnl-filters">
          <label>From <input type="date" [(ngModel)]="from" /></label>
          <label>To <input type="date" [(ngModel)]="to" /></label>
          <button (click)="load()" [disabled]="loading">{{ loading ? 'Loading…' : 'Apply' }}</button>
        </div>
      </div>

      <div class="pnl-cards" *ngIf="summary">
        <div class="pnl-card"><span class="k">Paid transactions</span><span class="v">{{ summary.transactionCount }}</span></div>
        <div class="pnl-card"><span class="k">Volume</span><span class="v">{{ summary.totalVolume | number:'1.2-2' }}</span></div>
        <div class="pnl-card profit"><span class="k">Total profit (fees)</span><span class="v">{{ summary.totalProfit | number:'1.2-2' }}</span></div>
        <div class="pnl-card"><span class="k">USI commission (ref)</span><span class="v">{{ summary.totalUsiCommission | number:'1.2-2' }}</span></div>
      </div>

      <div class="pnl-grid" *ngIf="summary">
        <div class="pnl-block">
          <h2>By corridor</h2>
          <table>
            <tr><th>Corridor</th><th>#</th><th>Volume</th><th>Profit (fee)</th></tr>
            <tr *ngFor="let b of summary.byCorridor">
              <td>{{ b.key }}</td><td>{{ b.transactionCount }}</td>
              <td>{{ b.volume | number:'1.2-2' }}</td><td class="profit">{{ b.fee | number:'1.2-2' }}</td>
            </tr>
            <tr *ngIf="!summary.byCorridor?.length"><td colspan="4" class="empty">No paid USI transactions in range</td></tr>
          </table>
        </div>

        <div class="pnl-block">
          <h2>By day</h2>
          <table>
            <tr><th>Day</th><th>#</th><th>Volume</th><th>Profit (fee)</th></tr>
            <tr *ngFor="let b of summary.byDay">
              <td>{{ b.key }}</td><td>{{ b.transactionCount }}</td>
              <td>{{ b.volume | number:'1.2-2' }}</td><td class="profit">{{ b.fee | number:'1.2-2' }}</td>
            </tr>
            <tr *ngIf="!summary.byDay?.length"><td colspan="4" class="empty">—</td></tr>
          </table>
        </div>
      </div>

      <div class="pnl-block" *ngIf="summary">
        <h2>Transactions</h2>
        <table>
          <tr><th>Ref</th><th>Sender</th><th>Send</th><th>Receive</th><th>Fee (profit)</th><th>USI comm.</th><th>USI ref</th><th>Date</th></tr>
          <tr *ngFor="let t of summary.transactions">
            <td>{{ t.referenceNumber }}</td>
            <td>{{ t.senderEmail }}</td>
            <td>{{ t.sendAmount | number:'1.2-2' }} {{ t.sendCurrency }}</td>
            <td>{{ t.receiveAmount | number:'1.2-2' }} {{ t.receiveCurrency }}</td>
            <td class="profit">{{ t.fee | number:'1.2-2' }}</td>
            <td>{{ t.usiCommission | number:'1.2-2' }}</td>
            <td>{{ t.usiReference }}</td>
            <td>{{ t.createdAt }}</td>
          </tr>
          <tr *ngIf="!summary.transactions?.length"><td colspan="8" class="empty">No paid USI transactions in range</td></tr>
        </table>
      </div>

      <div class="empty" *ngIf="!summary && !loading">No data.</div>
    </div>
  </ion-content>`,
  styles: [`
    .pnl { padding: 16px; max-width: 1200px; margin: 0 auto; }
    .pnl-head h1 { margin: 0; font-size: 1.4rem; color:#1B3A6B; }
    .pnl-sub { color:#6b7280; margin: 4px 0 12px; font-size:.85rem; }
    .pnl-filters { display:flex; gap:12px; align-items:flex-end; flex-wrap:wrap; margin-bottom:16px; }
    .pnl-filters label { display:flex; flex-direction:column; font-size:.8rem; color:#374151; gap:4px; }
    .pnl-filters input { padding:6px 8px; border:1px solid #d1d5db; border-radius:6px; }
    .pnl-filters button { padding:8px 16px; background:#1B3A6B; color:#fff; border:none; border-radius:6px; cursor:pointer; }
    .pnl-cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px; margin-bottom:20px; }
    .pnl-card { background:#fff; border:1px solid #e5e7eb; border-radius:10px; padding:14px; display:flex; flex-direction:column; gap:6px; }
    .pnl-card .k { font-size:.75rem; color:#6b7280; text-transform:uppercase; letter-spacing:.03em; }
    .pnl-card .v { font-size:1.35rem; font-weight:700; color:#111827; }
    .pnl-card.profit { border-color:#16a34a; } .pnl-card.profit .v { color:#16a34a; }
    .pnl-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:16px; }
    @media (max-width:800px){ .pnl-grid { grid-template-columns:1fr; } }
    .pnl-block { background:#fff; border:1px solid #e5e7eb; border-radius:10px; padding:14px; margin-bottom:16px; overflow-x:auto; }
    .pnl-block h2 { font-size:1rem; color:#1B3A6B; margin:0 0 10px; }
    table { width:100%; border-collapse:collapse; font-size:.82rem; }
    th,td { text-align:left; padding:7px 10px; border-bottom:1px solid #f0f0f0; white-space:nowrap; }
    th { color:#6b7280; font-weight:600; text-transform:uppercase; font-size:.72rem; }
    td.profit { color:#16a34a; font-weight:600; }
    .empty { color:#9ca3af; text-align:center; padding:16px; }
  `]
})
export class AdminUsiPnlPage implements OnInit {
  from = '';
  to = '';
  summary: any = null;
  loading = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    const today = new Date();
    const past = new Date(); past.setDate(past.getDate() - 30);
    this.to = today.toISOString().slice(0, 10);
    this.from = past.toISOString().slice(0, 10);
    this.load();
  }

  load(): void {
    this.loading = true;
    const params: any = {};
    if (this.from) params.from = this.from;
    if (this.to) params.to = this.to;
    this.http.get(`${environment.apiUrl}/admin/usi/ledger/summary`, { params }).subscribe({
      next: (res: any) => { this.summary = res?.data || res; this.loading = false; },
      error: () => { this.summary = null; this.loading = false; }
    });
  }
}
