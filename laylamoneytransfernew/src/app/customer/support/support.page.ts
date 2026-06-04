import { Component, OnInit } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { SupportService } from '../../core/services/support.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-support',
  templateUrl: './support.page.html',
  styleUrls: ['./support.page.scss']
})
export class SupportPage implements OnInit {
  activeTab: 'submit' | 'tickets' = 'submit';
  tickets: any[] = [];
  selectedTicket: any = null;
  loading = false;
  submitting = false;
  replying = false;
  ticketsLoading = false;

  // Form fields
  subject = '';
  issueType = '';
  priority = 'MEDIUM';
  message = '';
  files: File[] = [];
  replyMessage = '';
  replyFiles: File[] = [];
  dragOver = false;

  issueTypes = [
    { value: 'PAYMENT_ISSUE', label: 'Payment Issue' },
    { value: 'TRANSFER_DELAY', label: 'Transfer Delay' },
    { value: 'ACCOUNT_ACCESS', label: 'Account Access' },
    { value: 'KYC_VERIFICATION', label: 'KYC Verification' },
    { value: 'REFUND_REQUEST', label: 'Refund Request' },
    { value: 'TECHNICAL_ISSUE', label: 'Technical Issue' },
    { value: 'GENERAL_INQUIRY', label: 'General Inquiry' }
  ];

  priorities = [
    { value: 'LOW', label: 'Low' },
    { value: 'MEDIUM', label: 'Medium' },
    { value: 'HIGH', label: 'High' },
    { value: 'URGENT', label: 'Urgent' }
  ];

  constructor(
    private supportService: SupportService,
    private toastCtrl: ToastController
  ) {}

  ngOnInit(): void {
    this.loadTickets();
  }

  switchTab(tab: 'submit' | 'tickets'): void {
    this.activeTab = tab;
    this.selectedTicket = null;
    if (tab === 'tickets') {
      this.loadTickets();
    }
  }

  loadTickets(): void {
    this.ticketsLoading = true;
    this.supportService.getMyTickets().subscribe({
      next: (tickets) => {
        this.tickets = tickets;
        this.ticketsLoading = false;
      },
      error: () => {
        this.ticketsLoading = false;
        this.showToast('Failed to load tickets', 'danger');
      }
    });
  }

  onSubmit(): void {
    if (!this.subject || !this.issueType || !this.message) {
      this.showToast('Please fill in all required fields', 'warning');
      return;
    }

    this.submitting = true;
    const formData = new FormData();
    formData.append('subject', this.subject);
    formData.append('issueType', this.issueType);
    formData.append('priority', this.priority);
    formData.append('message', this.message);
    this.files.forEach(file => formData.append('files', file));

    this.supportService.createTicket(formData).subscribe({
      next: () => {
        this.showToast('Ticket submitted successfully!', 'success');
        this.resetForm();
        this.submitting = false;
        this.switchTab('tickets');
      },
      error: () => {
        this.submitting = false;
        this.showToast('Failed to submit ticket', 'danger');
      }
    });
  }

  viewTicket(ticket: any): void {
    this.loading = true;
    this.supportService.getTicketDetail(ticket.id).subscribe({
      next: (detail) => {
        this.selectedTicket = detail;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.showToast('Failed to load ticket details', 'danger');
      }
    });
  }

  backToList(): void {
    this.selectedTicket = null;
    this.replyMessage = '';
    this.replyFiles = [];
  }

  onReply(): void {
    if (!this.replyMessage.trim()) {
      this.showToast('Please enter a reply message', 'warning');
      return;
    }

    this.replying = true;
    const formData = new FormData();
    formData.append('message', this.replyMessage);
    this.replyFiles.forEach(file => formData.append('files', file));

    this.supportService.replyToTicket(this.selectedTicket.id, formData).subscribe({
      next: () => {
        this.showToast('Reply sent!', 'success');
        this.replyMessage = '';
        this.replyFiles = [];
        this.replying = false;
        this.viewTicket(this.selectedTicket);
      },
      error: () => {
        this.replying = false;
        this.showToast('Failed to send reply', 'danger');
      }
    });
  }

  // File handling
  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.addFiles(Array.from(input.files));
    }
    input.value = '';
  }

  onReplyFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      const newFiles = Array.from(input.files);
      if (this.replyFiles.length + newFiles.length > 2) {
        this.showToast('Maximum 2 files allowed', 'warning');
        return;
      }
      this.replyFiles.push(...newFiles);
    }
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver = false;
    if (event.dataTransfer?.files) {
      this.addFiles(Array.from(event.dataTransfer.files));
    }
  }

  private addFiles(newFiles: File[]): void {
    if (this.files.length + newFiles.length > 2) {
      this.showToast('Maximum 2 files allowed', 'warning');
      return;
    }
    this.files.push(...newFiles);
  }

  removeFile(index: number): void {
    this.files.splice(index, 1);
  }

  removeReplyFile(index: number): void {
    this.replyFiles.splice(index, 1);
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      OPEN: 'Open',
      IN_PROGRESS: 'In Progress',
      AWAITING_CUSTOMER: 'Awaiting Reply',
      RESOLVED: 'Resolved',
      CLOSED: 'Closed'
    };
    return labels[status] || status;
  }

  getIssueTypeLabel(value: string): string {
    const found = this.issueTypes.find(t => t.value === value);
    return found ? found.label : value;
  }

  getPriorityLabel(value: string): string {
    const found = this.priorities.find(p => p.value === value);
    return found ? found.label : value;
  }

  private resetForm(): void {
    this.subject = '';
    this.issueType = '';
    this.priority = 'MEDIUM';
    this.message = '';
    this.files = [];
  }

  getAttachmentUrl(att: any): string {
    if (!att?.url) return '';
    if (att.url.startsWith('http')) return att.url;
    const base = environment.apiUrl.replace(/\/api$/, '');
    return base + att.url;
  }

  isImage(att: any): boolean {
    return att?.contentType?.startsWith('image/');
  }

  private async showToast(message: string, color: string): Promise<void> {
    const toast = await this.toastCtrl.create({ message, duration: 4000, position: 'top', color, cssClass: `fb-toast fb-toast-${color}`, buttons: [{ icon: 'close-outline', role: 'cancel' }] });
    await toast.present();
  }
}
