import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/shared.module';
import { PayinPartnerRoutingModule } from './payin-partner-routing.module';
import { PayinPartnerLayoutComponent } from './layout/payin-partner-layout.component';
import { PayinPartnerDashboardPage } from './dashboard/payin-partner-dashboard.page';
import { PayinPartnerTransactionsPage } from './transactions/payin-partner-transactions.page';
import { PayinPartnerLedgerPage } from './ledger/payin-partner-ledger.page';
import { PayinPartnerSettlementsPage } from './settlements/payin-partner-settlements.page';
import { CreateCustomerPage } from './create-customer/create-customer.page';
import { PayinPartnerCustomersPage } from './customers/payin-partner-customers.page';
import { CreateTransactionPage } from './create-transaction/create-transaction.page';

@NgModule({
  declarations: [
    PayinPartnerLayoutComponent,
    PayinPartnerDashboardPage,
    PayinPartnerTransactionsPage,
    PayinPartnerLedgerPage,
    PayinPartnerSettlementsPage,
    CreateCustomerPage,
    PayinPartnerCustomersPage,
    CreateTransactionPage
  ],
  imports: [
    SharedModule,
    PayinPartnerRoutingModule
  ]
})
export class PayinPartnerModule {}
