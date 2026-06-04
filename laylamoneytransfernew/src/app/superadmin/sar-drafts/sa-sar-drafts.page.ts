import { Component } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { ComplianceService, SarDraftResponse } from '../../core/services/compliance.service';

@Component({
  selector: 'app-sa-sar-drafts',
  templateUrl: './sa-sar-drafts.page.html',
  styleUrls: ['./sa-sar-drafts.page.scss']
})
export class SASarDraftsPage {
  alertIdInput = '';
  generating = false;
  draft: SarDraftResponse | null = null;
  errorMessage = '';

  constructor(
    private complianceService: ComplianceService,
    private toastCtrl: ToastController
  ) {}

  generate(): void {
    const id = Number(this.alertIdInput.trim());
    if (!id || Number.isNaN(id)) {
      this.errorMessage = 'Enter a valid alert ID.';
      return;
    }
    this.errorMessage = '';
    this.generating = true;
    this.draft = null;
    this.complianceService.generateSarFromAlert(id).subscribe({
      next: (res) => {
        this.draft = res;
        this.generating = false;
        this.toast(`SAR draft #${res.sarReportId} generated`, 'success');
      },
      error: (err) => {
        this.generating = false;
        this.errorMessage = err?.error?.message || err?.message || 'Failed to generate SAR — is the alert id valid and closed?';
        this.toast(this.errorMessage, 'danger');
      }
    });
  }

  copyJson(): void {
    if (!this.draft?.reportContent) return;
    navigator.clipboard.writeText(this.draft.reportContent).then(
      () => this.toast('Copied to clipboard', 'success'),
      () => this.toast('Copy failed', 'danger')
    );
  }

  downloadJson(): void {
    if (!this.draft) return;
    const blob = new Blob([this.draft.reportContent], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `sar-draft-${this.draft.sarReportId}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  clear(): void {
    this.draft = null;
    this.alertIdInput = '';
    this.errorMessage = '';
  }

  private async toast(message: string, color: string): Promise<void> {
    const t = await this.toastCtrl.create({
      message, duration: 3500, position: 'top', color,
      cssClass: `fb-toast fb-toast-${color}`,
      buttons: [{ icon: 'close-outline', role: 'cancel' }]
    });
    await t.present();
  }
}
