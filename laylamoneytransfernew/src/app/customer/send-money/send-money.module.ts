import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { SendMoneyPage } from './send-money.page';

const routes: Routes = [
  { path: '', component: SendMoneyPage }
];

@NgModule({
  declarations: [SendMoneyPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class SendMoneyPageModule {}
