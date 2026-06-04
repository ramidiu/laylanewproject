import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AdminLayoutComponent } from './layout/admin-layout.component';
import { AdminDashboardPage } from './dashboard/admin-dashboard.page';
import { AdminUsersPage } from './users/admin-users.page';
import { AdminUserDetailPage } from './users/detail/admin-user-detail.page';
import { AdminTransactionsPage } from './transactions/admin-transactions.page';
import { AdminCompliancePage } from './compliance/admin-compliance.page';
import { AdminCorridorsPage } from './corridors/admin-corridors.page';
import { AdminFxPage } from './fx/admin-fx.page';
import { AdminLedgerPage } from './ledger/admin-ledger.page';
import { AdminSettlementsPage } from './settlements/admin-settlements.page';
import { AdminKycReviewPage } from './kyc-review/admin-kyc-review.page';
import { AdminSupportPage } from './support/admin-support.page';
import { AdminNotificationsPage } from './notifications/admin-notifications.page';
import { AdminPayinPartnersPage } from './payin-partners/admin-payin-partners.page';
import { AdminPayinCustomersPage } from './payin-customers/admin-payin-customers.page';
import { AdminTransferConfigPage } from './transfer-config/admin-transfer-config.page';

const routes: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AdminDashboardPage },
      { path: 'users', component: AdminUsersPage },
      { path: 'users/:id', component: AdminUserDetailPage },
      { path: 'transactions', component: AdminTransactionsPage },
      // Disabled: route via redirect so direct-URL access is blocked
      { path: 'compliance',     redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'ledger',         redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'settlements',    redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'notifications',  redirectTo: 'dashboard', pathMatch: 'full' },

      { path: 'corridors', component: AdminCorridorsPage },
      { path: 'fx', component: AdminFxPage },
      { path: 'kyc-review', component: AdminKycReviewPage },
      { path: 'support', component: AdminSupportPage },
      { path: 'payin-partners', component: AdminPayinPartnersPage },
      { path: 'payin-customers', component: AdminPayinCustomersPage },
      { path: 'transfer-config',   component: AdminTransferConfigPage }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class AdminRoutingModule {}
