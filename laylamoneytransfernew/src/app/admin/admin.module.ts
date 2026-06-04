import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { AdminRoutingModule } from './admin-routing.module';
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

@NgModule({
  declarations: [
    AdminLayoutComponent,
    AdminDashboardPage,
    AdminUsersPage,
    AdminUserDetailPage,
    AdminTransactionsPage,
    AdminCompliancePage,
    AdminCorridorsPage,
    AdminFxPage,
    AdminLedgerPage,
    AdminSettlementsPage,
    AdminKycReviewPage,
    AdminSupportPage,
    AdminNotificationsPage,
    AdminPayinPartnersPage,
    AdminPayinCustomersPage,
    AdminTransferConfigPage
  ],
  imports: [
    SharedModule,
    AdminRoutingModule
  ]
})
export class AdminModule {}
