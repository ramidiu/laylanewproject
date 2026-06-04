import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from '../../core/services/user.service';
import { UserResponse } from '../../core/models/user.model';
import { PageResponse } from '../../core/models/common.model';

@Component({
  selector: 'app-admin-users',
  templateUrl: './admin-users.page.html',
  styleUrls: ['./admin-users.page.scss']
})
export class AdminUsersPage implements OnInit {
  users: UserResponse[] = [];
  loading = true;
  searchQuery = '';
  statusFilter = '';
  kycFilter = '';
  currentPage = 0;
  totalPages = 0;
  totalElements = 0;
  pageSize = 20;

  constructor(
    private userService: UserService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.userService.listUsers({
      page: this.currentPage,
      size: this.pageSize,
      search: this.searchQuery || undefined,
      status: this.statusFilter || undefined,
      kycTier: this.kycFilter || undefined,
      sort: 'alpha'
    }).subscribe({
      next: (res: PageResponse<UserResponse>) => {
        this.users = res.content;
        this.totalPages = res.totalPages;
        this.totalElements = res.totalElements;
        this.loading = false;
      },
      error: () => {
        this.users = [];
        this.loading = false;
      }
    });
  }

  onSearch(event: any): void {
    this.searchQuery = event.target?.value || '';
    this.currentPage = 0;
    this.loadUsers();
  }

  onStatusFilter(status: string): void {
    this.statusFilter = status;
    this.currentPage = 0;
    this.loadUsers();
  }

  onKycFilter(kyc: string): void {
    this.kycFilter = kyc;
    this.currentPage = 0;
    this.loadUsers();
  }

  goToPage(page: number): void {
    this.currentPage = page;
    this.loadUsers();
  }

  viewUser(id: string): void {
    this.router.navigate(['/admin/users', id]);
  }
}
