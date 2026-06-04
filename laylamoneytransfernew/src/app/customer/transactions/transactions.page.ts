import { TransactionResponse } from '../../core/models/transaction.model';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TransactionService } from '../../core/services/transaction.service';

import { PageResponse } from '../../core/models/common.model';

@Component({
  selector: 'app-transactions',
  templateUrl: './transactions.page.html',
  styleUrls: ['./transactions.page.scss']
})
export class TransactionsPage implements OnInit {
  transactions: TransactionResponse[] = [];
  filteredTransactions: TransactionResponse[] = [];
  loading = true;
  selectedFilter = 'ALL';
  searchQuery = '';
  currentPage = 0;
  totalPages = 0;
  filters = ['ALL', 'PENDING', 'PROCESSING', 'PAID', 'FAILED', 'CANCELLED'];

  showAdvanced = false;
  startDate = '';
  endDate = '';
  minAmount: number | null = null;
  maxAmount: number | null = null;
  currencyFilter = '';
  deliveryFilter = '';
  sortOrder: 'createdAt,desc' | 'createdAt,asc' | 'sendAmount,desc' | 'sendAmount,asc' = 'createdAt,desc';

  availableCurrencies: string[] = [];
  availableDeliveryMethods: string[] = [];

  constructor(
    private transactionService: TransactionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadTransactions();
  }

  loadTransactions(loadMore = false): void {
    if (!loadMore) {
      this.loading = true;
      this.currentPage = 0;
    }

    const [sortBy, sortDir] = this.sortOrder.split(',');

    const params: any = {
      page: this.currentPage,
      size: 20,
      search: this.searchQuery || undefined,
      startDate: this.startDate || undefined,
      endDate: this.endDate || undefined,
      sortBy,
      sortDir
    };

    if (this.selectedFilter !== 'ALL') {
      params.status = this.selectedFilter;
    }

    this.transactionService.list(params).subscribe({
      next: (response: PageResponse<TransactionResponse>) => {
        if (loadMore) {
          this.transactions = [...this.transactions, ...response.content];
        } else {
          this.transactions = response.content;
        }
        this.totalPages = response.totalPages;
        this.updateAvailableOptions();
        this.applyClientFilters();
        this.loading = false;
      },
      error: () => {
        this.transactions = [];
        this.filteredTransactions = [];
        this.loading = false;
      }
    });
  }

  private updateAvailableOptions(): void {
    const currencies = new Set<string>();
    const methods = new Set<string>();
    for (const tx of this.transactions) {
      if (tx.receiveCurrency) currencies.add(tx.receiveCurrency);
      if (tx.deliveryMethod) methods.add(tx.deliveryMethod);
    }
    this.availableCurrencies = Array.from(currencies).sort();
    this.availableDeliveryMethods = Array.from(methods).sort();
  }

  applyClientFilters(): void {
    this.filteredTransactions = this.transactions.filter(tx => {
      if (this.currencyFilter && tx.receiveCurrency !== this.currencyFilter) return false;
      if (this.deliveryFilter && tx.deliveryMethod !== this.deliveryFilter) return false;
      if (this.minAmount != null && tx.sendAmount < this.minAmount) return false;
      if (this.maxAmount != null && tx.sendAmount > this.maxAmount) return false;
      return true;
    });
  }

  toggleAdvanced(): void {
    this.showAdvanced = !this.showAdvanced;
  }

  onFilterChange(filter: string): void {
    this.selectedFilter = filter;
    this.loadTransactions();
  }

  onSearch(event: any): void {
    this.searchQuery = event.detail.value || '';
    this.loadTransactions();
  }

  onDateChange(): void {
    this.loadTransactions();
  }

  onSortChange(): void {
    this.loadTransactions();
  }

  onClientFilterChange(): void {
    this.applyClientFilters();
  }

  clearAllFilters(): void {
    this.startDate = '';
    this.endDate = '';
    this.minAmount = null;
    this.maxAmount = null;
    this.currencyFilter = '';
    this.deliveryFilter = '';
    this.searchQuery = '';
    this.selectedFilter = 'ALL';
    this.sortOrder = 'createdAt,desc';
    this.loadTransactions();
  }

  get hasAdvancedFilters(): boolean {
    return !!(this.startDate || this.endDate || this.minAmount != null || this.maxAmount != null
      || this.currencyFilter || this.deliveryFilter);
  }

  loadMore(event: any): void {
    this.currentPage++;
    if (this.currentPage < this.totalPages) {
      this.loadTransactions(true);
      event.target.complete();
    } else {
      event.target.disabled = true;
    }
  }

  handleRefresh(event: any): void {
    this.loadTransactions();
    setTimeout(() => event.target.complete(), 1000);
  }

  goToDetail(id: string): void {
    this.router.navigate(['/home/transactions', id]);
  }
}
