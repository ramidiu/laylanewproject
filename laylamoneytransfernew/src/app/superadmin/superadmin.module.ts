import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { SuperAdminRoutingModule } from './superadmin-routing.module';

// Layout
import { SuperAdminLayoutComponent } from './layout/superadmin-layout.component';

// Dashboard
import { SuperAdminDashboardPage } from './dashboard/superadmin-dashboard.page';

// Wrapper pages (duplicated from admin for independence)
import { SAUsersPage } from './users/sa-users.page';
import { SATransactionsPage } from './transactions/sa-transactions.page';
import { SACompliancePage } from './compliance/sa-compliance.page';
import { SACorridorsPage } from './corridors/sa-corridors.page';
import { SAFxPage } from './fx/sa-fx.page';

// New super-admin-only pages
import { PayoutPartnersPage } from './partners/payout-partners.page';
import { PayinPartnersPage } from './payin-partners/payin-partners.page';
import { CorridorManagementPage } from './corridor-management/corridor-management.page';
import { LedgerPage } from './ledger/ledger.page';
import { SettlementsPage } from './settlements/settlements.page';
import { SettlementRatesPage } from './settlement-rates/settlement-rates.page';
import { TransferConfigPage } from './transfer-config/transfer-config.page';
import { EmailTemplatesPage } from './email-templates/email-templates.page';
import { AuditLogsPage } from './audit-logs/audit-logs.page';
import { SAKycReviewPage } from './kyc-review/sa-kyc-review.page';
import { SAUserProfilePage } from './users/sa-user-profile.page';
import { SASupportPage } from './support/sa-support.page';
import { WalletManagementPage } from './wallet-management/wallet-management.page';
import { SANotificationsPage } from './notifications/sa-notifications.page';
import { SAComplianceAuditPage } from './compliance-audit/sa-compliance-audit.page';
import { SACtrReportsPage } from './ctr-reports/sa-ctr-reports.page';
import { SASarDraftsPage } from './sar-drafts/sa-sar-drafts.page';
import { ExecutiveDashboardPage } from './dashboards/executive/executive-dashboard.page';
import { UserMgmtDashboardPage } from './dashboards/user-mgmt/user-mgmt-dashboard.page';
import { TxnOpsDashboardPage } from './dashboards/txn-ops/txn-ops-dashboard.page';
import { FinanceDashboardPage } from './dashboards/finance/finance-dashboard.page';
import { ComplianceRiskDashboardPage } from './dashboards/compliance-risk/compliance-risk-dashboard.page';
import { PartnerPerfDashboardPage } from './dashboards/partner-perf/partner-perf-dashboard.page';
import { CorridorAnalyticsDashboardPage } from './dashboards/corridor-analytics/corridor-analytics-dashboard.page';
import { CustomerEngagementDashboardPage } from './dashboards/customer-engagement/customer-engagement-dashboard.page';
import { SupportDashboardPage } from './dashboards/support-dash/support-dashboard.page';
import { SystemHealthDashboardPage } from './dashboards/system-health/system-health-dashboard.page';
import { SADemoUsersPage } from './demo-users/sa-demo-users.page';
import { SecuritySettingsPage } from './security-settings/security-settings.page';
import { SAPayinTransactionsPage } from './payin-transactions/sa-payin-transactions.page';
import { SAPayinCustomersPage } from './payin-customers/sa-payin-customers.page';
import { SAUsiMoneyPage } from './usi-money/usi-money.page';

@NgModule({
  declarations: [
    SuperAdminLayoutComponent,
    SuperAdminDashboardPage,
    SAUsersPage,
    SATransactionsPage,
    SACompliancePage,
    SACorridorsPage,
    SAFxPage,
    PayoutPartnersPage,
    PayinPartnersPage,
    CorridorManagementPage,
    LedgerPage,
    SettlementsPage,
    SettlementRatesPage,
    TransferConfigPage,
    SAUsiMoneyPage,
    EmailTemplatesPage,
    AuditLogsPage,
    SAKycReviewPage,
    SAUserProfilePage,
    SASupportPage,
    WalletManagementPage,
    SANotificationsPage,
    SAComplianceAuditPage,
    SACtrReportsPage,
    SASarDraftsPage,
    ExecutiveDashboardPage,
    UserMgmtDashboardPage,
    TxnOpsDashboardPage,
    FinanceDashboardPage,
    ComplianceRiskDashboardPage,
    PartnerPerfDashboardPage,
    CorridorAnalyticsDashboardPage,
    CustomerEngagementDashboardPage,
    SupportDashboardPage,
    SystemHealthDashboardPage,
    SADemoUsersPage,
    SecuritySettingsPage,
    SAPayinTransactionsPage,
    SAPayinCustomersPage
  ],
  imports: [
    SharedModule,
    SuperAdminRoutingModule
  ]
})
export class SuperAdminModule {}
