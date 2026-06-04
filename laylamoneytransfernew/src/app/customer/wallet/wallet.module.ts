import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { WalletPage } from './wallet.page';

const routes: Routes = [
  { path: '', component: WalletPage }
];

@NgModule({
  declarations: [WalletPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class WalletPageModule {}
