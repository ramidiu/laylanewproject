import { Component, OnInit } from '@angular/core';
import { AdminSupportService } from '../../core/services/admin-support.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin-support',
  templateUrl: './admin-support.page.html',
  styleUrls: ['./admin-support.page.scss']
})
export class AdminSupportPage implements OnInit {
  tickets: any[] = [];
  filteredTickets: any[] = [];
  selectedTicket: any = null;
  loading = true;
  replyLoading = false;

  activeFilter = 'ALL';
  priorityFilter = '';

  stats = { open: 0, inProgress: 0, awaitingCustomer: 0, resolved: 0 };

  replyMessage = '';
  replyFile: File | null = null;

  constructor(private supportService: AdminSupportService) {}

  ngOnInit(): void {
    this.loadTickets();
  }

  loadTickets(): void {
    this.loading = true;
    const statusParam = this.activeFilter === 'ALL' ? undefined : this.activeFilter;
    const priorityParam = this.priorityFilter || undefined;

    this.supportService.getAllTickets(statusParam, priorityParam).subscribe({
      next: (tickets) => {
        this.tickets = tickets;
        this.computeStats();
        this.applyFilter();
        this.loading = false;
      },
      error: () => {
        this.tickets = [];
        this.filteredTickets = [];
        this.loading = false;
      }
    });
  }

  computeStats(): void {
    this.stats = { open: 0, inProgress: 0, awaitingCustomer: 0, resolved: 0 };
    for (const t of this.tickets) {
      const s = (t.status || '').toUpperCase();
      if (s === 'OPEN') this.stats.open++;
      else if (s === 'IN_PROGRESS') this.stats.inProgress++;
      else if (s === 'AWAITING_CUSTOMER') this.stats.awaitingCustomer++;
      else if (s === 'RESOLVED') this.stats.resolved++;
    }
  }

  applyFilter(): void {
    let list = [...this.tickets];

    if (this.activeFilter !== 'ALL') {
      list = list.filter(t => (t.status || '').toUpperCase() === this.activeFilter);
    }

    if (this.priorityFilter) {
      list = list.filter(t => (t.priority || '').toUpperCase() === this.priorityFilter.toUpperCase());
    }

    this.filteredTickets = list;
  }

  setFilter(filter: string): void {
    this.activeFilter = filter;
    this.applyFilter();
  }

  onPriorityChange(value: string): void {
    this.priorityFilter = value;
    this.applyFilter();
  }

  viewTicket(ticket: any): void {
    this.supportService.getTicketDetail(ticket.id).subscribe({
      next: (detail) => {
        this.selectedTicket = detail;
      },
      error: () => {
        this.selectedTicket = ticket;
      }
    });
  }

  backToList(): void {
    this.selectedTicket = null;
    this.replyMessage = '';
    this.replyFile = null;
    this.loadTickets();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.replyFile = input.files[0];
    }
  }

  removeFile(): void {
    this.replyFile = null;
  }

  onReply(): void {
    if (!this.replyMessage.trim() || !this.selectedTicket) return;

    this.replyLoading = true;
    const formData = new FormData();
    formData.append('message', this.replyMessage.trim());
    if (this.replyFile) {
      formData.append('files', this.replyFile);
    }

    this.supportService.replyToTicket(this.selectedTicket.id, formData).subscribe({
      next: () => {
        this.replyMessage = '';
        this.replyFile = null;
        this.replyLoading = false;
        this.viewTicket(this.selectedTicket);
      },
      error: () => {
        this.replyLoading = false;
      }
    });
  }

  onStatusChange(status: string): void {
    if (!this.selectedTicket) return;
    this.supportService.updateStatus(this.selectedTicket.id, status).subscribe({
      next: () => {
        this.selectedTicket.status = status;
      }
    });
  }

  getStatusClass(status: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'OPEN') return 'info';
    if (s === 'IN_PROGRESS') return 'warning';
    if (s === 'AWAITING_CUSTOMER') return 'awaiting';
    if (s === 'RESOLVED') return 'success';
    if (s === 'CLOSED') return 'medium';
    return 'medium';
  }

  getStatusLabel(status: string): string {
    const s = (status || '').toUpperCase();
    return s.replace(/_/g, ' ');
  }

  getPriorityClass(priority: string): string {
    const p = (priority || '').toUpperCase();
    if (p === 'URGENT') return 'danger';
    if (p === 'HIGH') return 'warning';
    if (p === 'MEDIUM') return 'info';
    if (p === 'LOW') return 'medium';
    return 'medium';
  }

  isAdminMessage(msg: any): boolean {
    return msg.senderType === 'ADMIN' || msg.senderType === 'SYSTEM' || msg.senderRole === 'ADMIN' || msg.senderRole === 'SUPER_ADMIN' || msg.fromAdmin === true;
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
}
