import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { DomSanitizer, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';
import { AlertController, ToastController } from '@ionic/angular';
import { KycService } from '../../core/services/kyc.service';
import { UserService } from '../../core/services/user.service';
import { KycDocumentResponse, KycDocumentStatus, KycStatusResponse } from '../../core/models/kyc.model';
import { UserResponse } from '../../core/models/user.model';

interface ViewerDoc extends KycDocumentResponse {
  _isImage?: boolean;
  _isPdf?: boolean;
  _rawUrl?: string;
  _safeUrl?: SafeUrl | SafeResourceUrl;
  _mime?: string;
  _loaded?: boolean;
  _loading?: boolean;
  _failed?: boolean;
}

interface DocTab {
  key: string;
  label: string;
  icon: string;
  docs: ViewerDoc[];
}

interface CompareRow {
  label: string;
  field: string;
  stored: string;
  docSpecific?: boolean;
}


/**
 * Enterprise KYC verification workspace: split-screen review of a customer's
 * profile + documents in one page. Left = customer info, comparison table,
 * checklist, notes, actions. Right = embedded multi-format document viewer
 * (PDF + JPG/JPEG/PNG/WEBP) with zoom / rotate / fit / fullscreen, plus
 * per-type document tabs. No "open in new tab" — everything is inline.
 *
 * The comparison table's "On document" column is editable so a reviewer can
 * record what the document shows and instantly see green (match) / red
 * (mismatch) indicators vs the stored profile. OCR (phase 2) auto-fills it.
 */
@Component({
  selector: 'app-kyc-workspace',
  templateUrl: './kyc-workspace.component.html',
  styleUrls: ['./kyc-workspace.component.scss']
})
export class KycWorkspaceComponent implements OnChanges, OnDestroy {
  @Input() user!: UserResponse;
  @Input() userId!: string;
  @Output() closed = new EventEmitter<void>();
  /** Emitted after an action changes server state so the parent list can refresh. */
  @Output() changed = new EventEmitter<void>();

  loading = false;
  kycStatus?: KycStatusResponse;
  tabs: DocTab[] = [];
  activeTab?: DocTab;
  activeDoc?: ViewerDoc;

  // Viewer transform state
  scale = 1;
  rotation = 0;
  fullscreen = false;

  // Comparison + checklist + notes
  compareRows: CompareRow[] = [];
  onDoc: Record<string, string> = {};
  reviewerNotes = '';
  acting = false;

  private blobUrls: string[] = [];

  constructor(
    private kycService: KycService,
    private userService: UserService,
    private sanitizer: DomSanitizer,
    private alertCtrl: AlertController,
    private toastCtrl: ToastController
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['userId'] && this.userId) {
      this.reset();
      this.load();
    }
  }

  ngOnDestroy(): void {
    this.revokeBlobs();
  }

  private reset(): void {
    this.revokeBlobs();
    this.tabs = [];
    this.activeTab = undefined;
    this.activeDoc = undefined;
    this.kycStatus = undefined;
    this.reviewerNotes = '';
    this.onDoc = {};
    this.resetViewer();
  }

  private load(): void {
    this.loading = true;
    this.kycService.getStatus(this.userId).subscribe({ next: s => this.kycStatus = s, error: () => {} });
    this.kycService.getDocuments(this.userId).subscribe({
      next: (docs: KycDocumentResponse[]) => {
        const real = (docs || []).filter(d => {
          const p = ((d as any).filePath || '').toLowerCase();
          return (d as any).realUpload !== false && !p.includes('no_image');
        });
        this.tabs = this.buildTabs(real as ViewerDoc[]);
        this.loading = false;
        const first = this.tabs.find(t => t.docs.length);
        if (first) this.selectTab(first);
      },
      error: () => { this.loading = false; }
    });
  }

  // ── Tabs ──────────────────────────────────────────────────────────────
  private buildTabs(docs: ViewerDoc[]): DocTab[] {
    const defs: { key: string; label: string; icon: string; match: (t: string) => boolean }[] = [
      { key: 'NATIONAL_ID', label: 'National ID', icon: 'card-outline', match: t => t.includes('NATIONAL') },
      { key: 'PASSPORT', label: 'Passport', icon: 'document-outline', match: t => t.includes('PASSPORT') },
      { key: 'DRIVING', label: "Driving Licence", icon: 'car-outline', match: t => t.includes('DRIV') },
      { key: 'ADDRESS', label: 'Proof of Address', icon: 'home-outline', match: t => t.includes('ADDRESS') || t.includes('UTILITY') || t.includes('BANK') },
      { key: 'SELFIE', label: 'Selfie', icon: 'happy-outline', match: t => t.includes('SELFIE') },
    ];
    const tabs: DocTab[] = [];
    const used = new Set<ViewerDoc>();
    for (const d of defs) {
      const group = docs.filter(doc => d.match((doc.documentType || '').toString().toUpperCase()));
      group.forEach(g => used.add(g));
      if (group.length) tabs.push({ key: d.key, label: d.label, icon: d.icon, docs: group });
    }
    const others = docs.filter(doc => !used.has(doc));
    if (others.length) tabs.push({ key: 'OTHER', label: 'Other Documents', icon: 'folder-outline', docs: others });
    return tabs;
  }

  selectTab(tab: DocTab): void {
    this.activeTab = tab;
    if (tab.docs.length) this.selectDoc(tab.docs[0]);
  }

  // ── Document selection + blob loading ──────────────────────────────────
  selectDoc(doc: ViewerDoc): void {
    this.activeDoc = doc;
    this.onDoc = {};            // manual "on document" values are per-document
    this.resetViewer();
    this.buildCompareRows();
    if (!doc._loaded && !doc._loading) this.loadBlob(doc);
  }

  private loadBlob(doc: ViewerDoc): void {
    doc._loading = true;
    this.kycService.getDocumentFileBlob(this.userId, doc.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        this.blobUrls.push(url);
        const mime = blob.type || this.guessMime(doc.fileName);
        doc._mime = mime;
        doc._isPdf = mime === 'application/pdf' || (doc.fileName || '').toLowerCase().endsWith('.pdf');
        doc._isImage = !doc._isPdf && mime.startsWith('image/');
        doc._rawUrl = url;
        // Angular sanitizes [src] — blob: URLs become unsafe: without bypass.
        doc._safeUrl = doc._isPdf
          ? this.sanitizer.bypassSecurityTrustResourceUrl(url)
          : this.sanitizer.bypassSecurityTrustUrl(url);
        doc._loaded = true;
        doc._loading = false;
      },
      error: () => { doc._loading = false; doc._failed = true; }
    });
  }

  private guessMime(name: string): string {
    const n = (name || '').toLowerCase();
    if (n.endsWith('.pdf')) return 'application/pdf';
    if (n.endsWith('.png')) return 'image/png';
    if (n.endsWith('.webp')) return 'image/webp';
    return 'image/jpeg';
  }

  // ── Viewer controls ─────────────────────────────────────────────────────
  zoomIn(): void { this.scale = Math.min(5, +(this.scale + 0.25).toFixed(2)); }
  zoomOut(): void { this.scale = Math.max(0.25, +(this.scale - 0.25).toFixed(2)); }
  rotateCw(): void { this.rotation = (this.rotation + 90) % 360; }
  rotateCcw(): void { this.rotation = (this.rotation + 270) % 360; }
  fit(): void { this.resetViewer(); }
  toggleFullscreen(): void { this.fullscreen = !this.fullscreen; }
  private resetViewer(): void { this.scale = 1; this.rotation = 0; }

  onWheelZoom(ev: WheelEvent): void {
    if (!ev.ctrlKey) return;
    ev.preventDefault();
    ev.deltaY < 0 ? this.zoomIn() : this.zoomOut();
  }

  // Rotation only — zoom is applied via the layout-affecting `zoom` style so the
  // scroll container actually grows and you can pan a zoomed-in image.
  get mediaTransform(): string {
    return `rotate(${this.rotation}deg)`;
  }

  // ── Comparison table ─────────────────────────────────────────────────────
  private buildCompareRows(): void {
    const u = this.user || ({} as UserResponse);
    const addr = [u.addressLine1, u.addressLine2, u.city, u.postcode].filter(Boolean).join(', ');
    const d = this.activeDoc;
    this.compareRows = [
      { label: 'Full name', field: 'name', stored: `${u.firstName || ''} ${u.lastName || ''}`.trim() },
      { label: 'Email', field: 'email', stored: u.email || '' },
      { label: 'Phone', field: 'phone', stored: u.phone || '' },
      { label: 'Address', field: 'address', stored: addr },
      { label: 'Nationality / Country', field: 'country', stored: u.country || u.countryCode || '' },
      { label: 'Date of birth', field: 'dob', stored: u.dateOfBirth || '' },
      { label: 'Document number', field: 'docNumber', stored: (d?.documentNumber || ''), docSpecific: true },
      { label: 'Expiry date', field: 'expiry', stored: (d?.expiryDate || ''), docSpecific: true }
    ];
  }

  private norm(v: string): string {
    return (v || '').toString().toLowerCase().replace(/[^a-z0-9]/g, '');
  }

  matchState(row: CompareRow): '' | 'match' | 'mismatch' {
    const typed = (this.onDoc[row.field] || '').trim();
    if (!typed) return '';
    const a = this.norm(row.stored);
    const b = this.norm(typed);
    if (!a || !b) return b && !a ? 'mismatch' : '';
    return (a === b || a.includes(b) || b.includes(a)) ? 'match' : 'mismatch';
  }

  // ── Actions ──────────────────────────────────────────────────────────────
  async approve(): Promise<void> {
    if (!this.activeDoc) return;
    const alert = await this.alertCtrl.create({
      header: 'Approve document',
      message: `Approve the ${this.docLabel(this.activeDoc)} for ${this.user.firstName} ${this.user.lastName}?`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        { text: 'Approve', handler: () => this.review(KycDocumentStatus.APPROVED) }
      ]
    });
    await alert.present();
  }

  async reject(): Promise<void> {
    await this.promptReason('Reject document', 'Reason for rejection', (reason) =>
      this.review(KycDocumentStatus.REJECTED, reason));
  }

  async requestResubmission(): Promise<void> {
    await this.promptReason('Request resubmission', 'What does the customer need to re-upload?',
      (reason) => this.review(KycDocumentStatus.REJECTED, `Resubmission requested: ${reason}`, 'Resubmission requested.'));
  }

  async suspendUser(): Promise<void> {
    const alert = await this.alertCtrl.create({
      header: 'Suspend user',
      message: `Suspend ${this.user.firstName} ${this.user.lastName}? They will be blocked from transacting until reactivated.`,
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Suspend', cssClass: 'alert-button-danger',
          handler: () => {
            this.acting = true;
            this.userService.suspendUser(this.userId).subscribe({
              next: () => { this.acting = false; this.toast('User suspended', 'warning'); this.changed.emit(); },
              error: () => { this.acting = false; this.toast('Failed to suspend user', 'danger'); }
            });
          }
        }
      ]
    });
    await alert.present();
  }

  private async promptReason(header: string, placeholder: string, run: (reason: string) => void): Promise<void> {
    const alert = await this.alertCtrl.create({
      header,
      inputs: [{ name: 'reason', type: 'textarea', placeholder, value: this.reviewerNotes }],
      buttons: [
        { text: 'Cancel', role: 'cancel' },
        {
          text: 'Confirm', cssClass: 'alert-button-danger',
          handler: (data) => {
            const reason = (data?.reason || '').trim();
            if (!reason) { this.toast('A reason is required', 'danger'); return false; }
            run(reason);
            return true;
          }
        }
      ]
    });
    await alert.present();
  }

  private review(status: KycDocumentStatus, reason?: string, successMsg?: string): void {
    if (!this.activeDoc) return;
    this.acting = true;
    this.kycService.reviewDocument(this.userId, this.activeDoc.id, { status, rejectionReason: reason }).subscribe({
      next: () => {
        this.acting = false;
        this.toast(successMsg || (status === KycDocumentStatus.APPROVED ? 'Document approved' : 'Document rejected'),
          status === KycDocumentStatus.APPROVED ? 'success' : 'warning');
        if (this.activeDoc) this.activeDoc.status = status;
        this.changed.emit();
      },
      error: (err) => {
        this.acting = false;
        this.toast(err?.error?.message || 'Action failed', 'danger');
      }
    });
  }

  close(): void { this.closed.emit(); }

  docLabel(doc: ViewerDoc): string {
    const t = (doc.documentType || '').toString().replace(/_/g, ' ').toLowerCase();
    const side = (doc.documentNumber || '').toLowerCase().includes('back') ? ' (back)' : '';
    return t.replace(/\b\w/g, c => c.toUpperCase()) + side;
  }

  statusClass(status: string): string {
    if (status === 'APPROVED') return 'success';
    if (status === 'REJECTED') return 'danger';
    return 'warning';
  }

  private revokeBlobs(): void {
    for (const u of this.blobUrls) { try { URL.revokeObjectURL(u); } catch {} }
    this.blobUrls = [];
  }

  private async toast(message: string, color: string): Promise<void> {
    const t = await this.toastCtrl.create({ message, duration: 3500, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await t.present();
  }
}
