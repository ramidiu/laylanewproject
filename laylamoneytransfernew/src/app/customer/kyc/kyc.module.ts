import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { KycPage } from './kyc.page';

const routes: Routes = [
  { path: '', component: KycPage }
];

@NgModule({
  declarations: [KycPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class KycPageModule {}
