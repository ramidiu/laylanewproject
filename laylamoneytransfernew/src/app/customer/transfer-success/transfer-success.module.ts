import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { TransferSuccessPage } from './transfer-success.page';

const routes: Routes = [{ path: '', component: TransferSuccessPage }];

@NgModule({
  declarations: [TransferSuccessPage],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class TransferSuccessPageModule {}
