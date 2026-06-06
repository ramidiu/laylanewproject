import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { TransactionService } from '../core/services/transaction.service';
import { PdfService } from '../core/services/pdf.service';

@Component({
  selector: 'app-receipt-view',
  template: `
  <ion-content><div class="rv-page">
    <div class="rv-bar">
      <button class="rv-btn rv-btn--ghost" (click)="back()"><ion-icon name="arrow-back-outline"></ion-icon> Back</button>
      <div class="rv-actions">
        <button class="rv-btn" (click)="print()" [disabled]="loading || !!error"><ion-icon name="print-outline"></ion-icon> Print</button>
        <button class="rv-btn rv-btn--primary" (click)="download()" [disabled]="loading || !!error"><ion-icon name="download-outline"></ion-icon> Download PDF</button>
      </div>
    </div>
    <div class="rv-body">
      <div *ngIf="loading" class="rv-loading">Loading receipt…</div>
      <div *ngIf="error" class="rv-loading">{{ error }}</div>
      <iframe *ngIf="receiptHtml && !loading" #frame [srcdoc]="receiptHtml" class="rv-frame" title="Receipt"></iframe>
    </div>
  </div></ion-content>`,
  styles: [`
    .rv-page { max-width: 900px; margin: 0 auto; padding: 16px; }
    .rv-bar { display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; gap:8px; flex-wrap:wrap; }
    .rv-actions { display:flex; gap:8px; }
    .rv-btn { display:inline-flex; align-items:center; gap:6px; padding:8px 16px; border-radius:8px; border:1px solid #1B3A6B; background:#fff; color:#1B3A6B; font-weight:600; cursor:pointer; font-size:0.9rem; }
    .rv-btn--primary { background:#1B3A6B; color:#fff; }
    .rv-btn--ghost { border-color:#d1d5db; color:#374151; }
    .rv-btn:disabled { opacity:.5; cursor:not-allowed; }
    .rv-frame { width:100%; height:80vh; border:1px solid #e5e7eb; border-radius:10px; background:#fff; }
    .rv-loading { padding:48px; text-align:center; color:#6b7280; }
  `]
})
export class ReceiptViewPage implements OnInit {
  @ViewChild('frame') frame?: ElementRef<HTMLIFrameElement>;
  receiptHtml: SafeHtml | null = null;
  loading = true;
  error = '';
  txnId = '';

  constructor(
    private route: ActivatedRoute,
    private sanitizer: DomSanitizer,
    private txnService: TransactionService,
    private pdfService: PdfService
  ) {}

  ngOnInit(): void {
    this.txnId = this.route.snapshot.paramMap.get('id') || '';
    if (!this.txnId) { this.error = 'No transaction specified.'; this.loading = false; return; }
    this.txnService.getReceiptHtml(this.txnId).subscribe({
      next: (html) => { this.receiptHtml = this.sanitizer.bypassSecurityTrustHtml(html); this.loading = false; },
      error: () => { this.error = 'Could not load the receipt.'; this.loading = false; }
    });
  }

  print(): void {
    const f = this.frame?.nativeElement;
    if (f?.contentWindow) { f.contentWindow.focus(); f.contentWindow.print(); }
    else { window.print(); }
  }

  download(): void {
    this.txnService.downloadReceipt(this.txnId).subscribe({
      next: (blob) => this.pdfService.saveBlob(blob, `receipt-${this.txnId}.pdf`),
      error: () => { this.error = 'Could not download the receipt PDF.'; }
    });
  }

  back(): void { history.back(); }
}
