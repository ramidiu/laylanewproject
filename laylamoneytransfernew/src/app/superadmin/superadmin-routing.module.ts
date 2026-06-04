import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SuperAdminLayoutComponent } from './layout/superadmin-layout.component';
import { SuperAdminDashboardPage } from './dashboard/superadmin-dashboard.page';
import { SAUsersPage } from './users/sa-users.page';
import { SATransactionsPage } from './transactions/sa-transactions.page';
import { SACompliancePage } from './compliance/sa-compliance.page';
import { SACorridorsPage } from './corridors/sa-corridors.page';
import { SAFxPage } from './fx/sa-fx.page';
import { PayoutPartnersPage } from './partners/payout-partners.page';
import { PayinPartnersPage } from './payin-partners/payin-partners.page';
import { CorridorManagementPage } from './corridor-management/corridor-management.page';
import { LedgerPage } from './ledger/ledger.page';
import { SettlementsPage } from './settlements/settlements.page';
import { SettlementRatesPage } from './settlement-rates/settlement-rates.page';
import { TransferConfigPage } from './transfer-config/transfer-config.page';
import { SAUsiMoneyPage } from './usi-money/usi-money.page';
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

const routes: Routes = [
  {
    path: '',
    component: SuperAdminLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: SuperAdminDashboardPage },
      { path: 'users', component: SAUsersPage },
      { path: 'users/:uuid', component: SAUserProfilePage },
      { path: 'transactions', component: SATransactionsPage },
      // Compliance / Finance / Settlements / Notifications — disabled per business decision.
      // Redirected to dashboard so direct-URL access is blocked.
      { path: 'compliance',         redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'compliance-audit',   redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'ctr-reports',        redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'sar-drafts',         redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dash-compliance',    redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dash-finance',       redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dash-engagement',    redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'ledger',             redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'settlements',        redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'settlement-rates',   redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'wallet-management',  redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'notifications',      redirectTo: 'dashboard', pathMatch: 'full' },

      { path: 'corridors', component: SACorridorsPage },
      { path: 'fx', component: SAFxPage },
      { path: 'partners', component: PayoutPartnersPage },
      { path: 'payin-partners', component: PayinPartnersPage },
      { path: 'corridor-management', component: CorridorManagementPage },
      { path: 'transfer-config',   component: TransferConfigPage },
      { path: 'usi-money',         component: SAUsiMoneyPage },
      { path: 'email-templates', component: EmailTemplatesPage },
      { path: 'audit-logs', component: AuditLogsPage },
      { path: 'kyc-review', component: SAKycReviewPage },
      { path: 'support', component: SASupportPage },
      { path: 'dash-executive', component: ExecutiveDashboardPage },
      { path: 'dash-users', component: UserMgmtDashboardPage },
      { path: 'dash-transactions', component: TxnOpsDashboardPage },
      { path: 'dash-partners', component: PartnerPerfDashboardPage },
      { path: 'dash-corridors', component: CorridorAnalyticsDashboardPage },
      { path: 'dash-support', component: SupportDashboardPage },
      { path: 'dash-system', component: SystemHealthDashboardPage },
      { path: 'demo-users', component: SADemoUsersPage },
      { path: 'security-settings', component: SecuritySettingsPage },
      { path: 'payin-transactions', component: SAPayinTransactionsPage },
      { path: 'payin-customers', component: SAPayinCustomersPage },
      { path: 'system-controls', loadChildren: () => import('./system-controls/system-controls.module').then(m => m.SystemControlsPageModule) }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class SuperAdminRoutingModule {}
