import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { BeneficiariesPage } from './beneficiaries.page';

const routes: Routes = [
  { path: '', component: BeneficiariesPage }
];

@NgModule({
  declarations: [BeneficiariesPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class BeneficiariesPageModule {}
