import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { TransactionsPage } from './transactions.page';
import { TransactionDetailPage } from './detail/transaction-detail.page';

const routes: Routes = [
  { path: '', component: TransactionsPage },
  { path: ':id', component: TransactionDetailPage }
];

@NgModule({
  declarations: [TransactionsPage, TransactionDetailPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class TransactionsPageModule {}
