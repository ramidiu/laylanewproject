import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { ReferralPage } from './referral.page';

const routes: Routes = [{ path: '', component: ReferralPage }];

@NgModule({
  declarations: [ReferralPage],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class ReferralPageModule {}
