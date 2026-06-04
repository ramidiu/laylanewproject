import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { ConfigService } from '../../core/services/config.service';

@Component({
  selector: 'app-email-templates',
  templateUrl: './email-templates.page.html',
  styleUrls: ['./email-templates.page.scss']
})
export class EmailTemplatesPage implements OnInit {
  templates: any[] = [];
  loading = true;

  editingTemplate: any = null;
  editSubject = '';
  editBody = '';

  previewHtml = '';
  showPreview = false;

  constructor(
    private configService: ConfigService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loading = true;
    this.configService.getTemplates().subscribe({
      next: (res) => {
        this.templates = Array.isArray(res) ? res : res?.data || [];
        this.loading = false;
      },
      error: () => { this.templates = []; this.loading = false; }
    });
  }

  startEdit(template: any): void {
    this.editingTemplate = template;
    this.editSubject = template.subject;
    this.editBody = template.body || template.htmlBody || '';
  }

  saveTemplate(): void {
    if (!this.editingTemplate) return;
    this.configService.updateTemplate(this.editingTemplate.id, {
      subject: this.editSubject,
      body: this.editBody
    }).subscribe({
      next: () => {
        this.showToast('Template updated', 'success');
        this.editingTemplate = null;
        this.loadTemplates();
      },
      error: () => this.showToast('Failed to update template', 'danger')
    });
  }

  cancelEdit(): void {
    this.editingTemplate = null;
  }

  previewTemplate(template: any): void {
    this.configService.previewTemplate({
      templateId: template.id,
      subject: template.subject,
      body: template.body || template.htmlBody || ''
    }).subscribe({
      next: (res) => {
        this.previewHtml = res?.html || res?.data || res?.body || '<p>Preview not available</p>';
        this.showPreview = true;
      },
      error: () => this.showToast('Failed to preview template', 'danger')
    });
  }

  closePreview(): void {
    this.showPreview = false;
    this.previewHtml = '';
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
