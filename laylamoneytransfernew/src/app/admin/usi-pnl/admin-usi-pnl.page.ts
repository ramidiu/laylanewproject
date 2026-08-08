import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin-usi-pnl',
  standalone: true,
  imports: [CommonModule, FormsModule, IonicModule],
  template: `
  <ion-content>
    <div class="pnl">
      <div class="pnl-head">
        <h1>USI Money — P&amp;L</h1>
        <p class="pnl-sub">True profit = fee + FX margin − USI commission (the amount USI charges you). Click any transaction for the full profit/loss breakdown.</p>
        <div class="pnl-filters">
          <label>From <input type="date" [(ngModel)]="from" /></label>
          <label>To <input type="date" [(ngModel)]="to" /></label>
          <button (click)="load()" [disabled]="loading">{{ loading ? 'Loading…' : 'Apply' }}</button>
        </div>
      </div>

      <div class="pnl-cards" *ngIf="summary">
        <div class="pnl-card"><span class="k">Paid transactions</span><span class="v">{{ summary.transactionCount }}</span></div>
        <div class="pnl-card"><span class="k">Volume</span><span class="v">{{ summary.totalVolume | number:'1.2-2' }}</span></div>
        <div class="pnl-card"><span class="k">Fee income</span><span class="v">{{ summary.totalFee | number:'1.2-2' }}</span></div>
        <div class="pnl-card"><span class="k">USI commission (cost)</span><span class="v">{{ summary.totalUsiCommission | number:'1.2-2' }}</span></div>
        <div class="pnl-card" [ngClass]="summary.totalRealProfit >= 0 ? 'profit' : 'loss'">
          <span class="k">True P&amp;L (after USI cost)</span>
          <span class="v">{{ summary.totalRealProfit | number:'1.2-2' }}</span>
        </div>
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
        <h2>Transactions <span class="hint">— click a row for its profit / loss breakdown</span></h2>
        <table>
          <tr>
            <th></th><th>Ref</th><th>Send</th><th>Receive</th>
            <th>Cust paid</th><th>Paid to USI</th><th>Real P&amp;L</th><th>Date</th>
          </tr>
          <ng-container *ngFor="let t of summary.transactions">
            <tr class="clickable" (click)="toggle(t.referenceNumber)">
              <td class="chev">{{ expanded === t.referenceNumber ? '▾' : '▸' }}</td>
              <td>{{ t.referenceNumber }}</td>
              <td>{{ t.sendAmount | number:'1.2-2' }} {{ t.sendCurrency }}</td>
              <td>{{ t.receiveAmount | number:'1.2-2' }} {{ t.receiveCurrency }}</td>
              <td>{{ t.custPaid | number:'1.2-2' }} {{ t.sendCurrency }}</td>
              <td>{{ t.usiTotalPaid | number:'1.2-2' }} {{ t.sendCurrency }}</td>
              <td [ngClass]="t.realProfit >= 0 ? 'profit' : 'loss'">
                {{ t.realProfit >= 0 ? '+' : '' }}{{ t.realProfit | number:'1.2-2' }}
              </td>
              <td>{{ t.createdAt }}</td>
            </tr>
            <tr *ngIf="expanded === t.referenceNumber" class="detail-row">
              <td colspan="8">
                <div class="pl">
                  <div class="pl-title">
                    Profit / Loss — {{ t.referenceNumber }}
                    <span class="usi-ref">USI ref {{ t.usiReference }} · {{ t.sendCurrency }}→{{ t.receiveCurrency }} @ {{ t.usiRate | number:'1.2-4' }}</span>
                  </div>
                  <div class="pl-cols">
                    <div class="pl-sec">
                      <div class="pl-h in">① Money IN — from customer</div>
                      <div class="pl-line"><span>Send amount</span><span>{{ t.sendAmount | number:'1.2-2' }} {{ t.sendCurrency }}</span></div>
                      <div class="pl-line"><span>Layla fee</span><span>+ {{ t.fee | number:'1.2-2' }}</span></div>
                      <div class="pl-line tot"><span>Customer paid Layla</span><span>{{ t.custPaid | number:'1.2-2' }} {{ t.sendCurrency }}</span></div>
                    </div>
                    <div class="pl-sec">
                      <div class="pl-h out">② Money OUT — to USI</div>
                      <div class="pl-line"><span>USI source (converted → {{ t.receiveAmount | number:'1.2-2' }} {{ t.receiveCurrency }})</span><span>{{ t.usiSource | number:'1.2-2' }} {{ t.sendCurrency }}</span></div>
                      <div class="pl-line"><span>USI commission</span><span>+ {{ t.usiCommission | number:'1.2-2' }}</span></div>
                      <div class="pl-line tot"><span>Layla paid USI</span><span>{{ t.usiTotalPaid | number:'1.2-2' }} {{ t.sendCurrency }}</span></div>
                    </div>
                  </div>
                  <div class="pl-result">
                    <div class="pl-h">③ Result = ① − ②</div>
                    <div class="pl-line"><span>Fee income</span><span>+ {{ t.fee | number:'1.2-2' }}</span></div>
                    <div class="pl-line"><span>FX margin (send − USI source)</span><span>+ {{ t.fxMargin | number:'1.2-2' }}</span></div>
                    <div class="pl-line"><span>USI commission (cost)</span><span>− {{ t.usiCommission | number:'1.2-2' }}</span></div>
                    <div class="pl-line net" [ngClass]="t.realProfit >= 0 ? 'profit' : 'loss'">
                      <span>{{ t.realProfit >= 0 ? 'NET PROFIT' : 'NET LOSS' }}</span>
                      <span>{{ t.realProfit >= 0 ? '+' : '' }}{{ t.realProfit | number:'1.2-2' }} {{ t.sendCurrency }}</span>
                    </div>
                  </div>
                  <p class="pl-note">
                    Customer paid Layla <b>{{ t.custPaid | number:'1.2-2' }} {{ t.sendCurrency }}</b>;
                    Layla paid USI <b>{{ t.usiTotalPaid | number:'1.2-2' }} {{ t.sendCurrency }}</b>
                    (source {{ t.usiSource | number:'1.2-2' }} + commission {{ t.usiCommission | number:'1.2-2' }}).
                    Net = customer paid − paid to USI = <b>{{ t.realProfit | number:'1.2-2' }} {{ t.sendCurrency }}</b>.
                    <span *ngIf="t.realProfit < 0"> This transaction <b>lost money</b> — USI's fixed commission exceeded the fee + FX margin (common on very small transfers).</span>
                  </p>
                </div>
              </td>
            </tr>
          </ng-container>
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
    .pnl-card.loss { border-color:#dc2626; } .pnl-card.loss .v { color:#dc2626; }
    .pnl-grid { display:grid; grid-template-columns:1fr 1fr; gap:16px; margin-bottom:16px; }
    @media (max-width:800px){ .pnl-grid { grid-template-columns:1fr; } }
    .pnl-block { background:#fff; border:1px solid #e5e7eb; border-radius:10px; padding:14px; margin-bottom:16px; overflow-x:auto; }
    .pnl-block h2 { font-size:1rem; color:#1B3A6B; margin:0 0 10px; }
    table { width:100%; border-collapse:collapse; font-size:.82rem; }
    th,td { text-align:left; padding:7px 10px; border-bottom:1px solid #f0f0f0; white-space:nowrap; }
    th { color:#6b7280; font-weight:600; text-transform:uppercase; font-size:.72rem; }
    td.profit { color:#16a34a; font-weight:700; }
    td.loss { color:#dc2626; font-weight:700; }
    .hint { font-weight:400; font-size:.75rem; color:#9ca3af; text-transform:none; letter-spacing:0; }
    tr.clickable { cursor:pointer; } tr.clickable:hover td { background:#f8fafc; }
    td.chev { color:#1B3A6B; width:18px; }
    .detail-row td { background:#f9fafb; padding:0; }
    .empty { color:#9ca3af; text-align:center; padding:16px; }
    /* profit/loss breakdown panel */
    .pl { padding:16px; }
    .pl-title { font-weight:700; color:#1B3A6B; margin-bottom:12px; white-space:normal; }
    .pl-title .usi-ref { display:block; font-weight:400; font-size:.75rem; color:#6b7280; margin-top:2px; }
    .pl-cols { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
    @media (max-width:700px){ .pl-cols { grid-template-columns:1fr; } }
    .pl-sec, .pl-result { background:#fff; border:1px solid #e5e7eb; border-radius:8px; padding:10px 12px; }
    .pl-result { margin-top:12px; }
    .pl-h { font-size:.72rem; font-weight:700; text-transform:uppercase; letter-spacing:.03em; margin-bottom:6px; color:#374151; }
    .pl-h.in { color:#16a34a; } .pl-h.out { color:#dc2626; }
    .pl-line { display:flex; justify-content:space-between; gap:16px; padding:4px 0; font-size:.85rem; white-space:normal; }
    .pl-line span:last-child { font-variant-numeric:tabular-nums; white-space:nowrap; }
    .pl-line.tot { border-top:1px solid #e5e7eb; margin-top:4px; padding-top:6px; font-weight:700; }
    .pl-line.net { border-top:2px solid #111827; margin-top:6px; padding-top:8px; font-weight:800; font-size:1rem; }
    .pl-line.net.profit { color:#16a34a; } .pl-line.net.loss { color:#dc2626; }
    .pl-note { font-size:.8rem; color:#4b5563; margin:12px 0 0; line-height:1.5; white-space:normal; }
  `]
})
export class AdminUsiPnlPage implements OnInit {
  from = '';
  to = '';
  summary: any = null;
  loading = false;
  expanded: string | null = null;

  constructor(private http: HttpClient) {}

  toggle(ref: string): void {
    this.expanded = this.expanded === ref ? null : ref;
  }

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
