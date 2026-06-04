import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { PayinPartnerLayoutComponent } from './layout/payin-partner-layout.component';
import { PayinPartnerDashboardPage } from './dashboard/payin-partner-dashboard.page';
import { PayinPartnerTransactionsPage } from './transactions/payin-partner-transactions.page';
import { PayinPartnerLedgerPage } from './ledger/payin-partner-ledger.page';
import { PayinPartnerSettlementsPage } from './settlements/payin-partner-settlements.page';
import { CreateCustomerPage } from './create-customer/create-customer.page';
import { PayinPartnerCustomersPage } from './customers/payin-partner-customers.page';
import { CreateTransactionPage } from './create-transaction/create-transaction.page';

const routes: Routes = [
  {
    path: '',
    component: PayinPartnerLayoutComponent,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: PayinPartnerDashboardPage },
      { path: 'customers', component: PayinPartnerCustomersPage },
      { path: 'create-customer', component: CreateCustomerPage },
      { path: 'transactions', component: PayinPartnerTransactionsPage },
      { path: 'create-transaction', component: CreateTransactionPage },
      { path: 'ledger', component: PayinPartnerLedgerPage },
      { path: 'settlements', component: PayinPartnerSettlementsPage }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class PayinPartnerRoutingModule {}
