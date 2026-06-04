import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../shared/shared.module';
import { PartnerLayoutComponent } from './layout/partner-layout.component';
import { PartnerDashboardPage } from './dashboard/partner-dashboard.page';
import { PartnerTransactionsPage } from './transactions/partner-transactions.page';
import { PartnerCompletedPage } from './completed/partner-completed.page';
import { PartnerLedgerPage } from './ledger/partner-ledger.page';
import { PartnerSettlementsPage } from './settlements/partner-settlements.page';
import { PartnerTransactionDetailPage } from './transactions/detail/partner-transaction-detail.page';

const routes: Routes = [
  {
    path: '',
    component: PartnerLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: PartnerDashboardPage },
      { path: 'transactions', component: PartnerTransactionsPage },
      { path: 'transactions/:id', component: PartnerTransactionDetailPage },
      { path: 'completed', component: PartnerCompletedPage },
      { path: 'ledger', component: PartnerLedgerPage },
      { path: 'settlements', component: PartnerSettlementsPage }
    ]
  }
];

@NgModule({
  declarations: [
    PartnerLayoutComponent,
    PartnerDashboardPage,
    PartnerTransactionsPage,
    PartnerCompletedPage,
    PartnerLedgerPage,
    PartnerSettlementsPage,
    PartnerTransactionDetailPage
  ],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class PartnerModule {}
