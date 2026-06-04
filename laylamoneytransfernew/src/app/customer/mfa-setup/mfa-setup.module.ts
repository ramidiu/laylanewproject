import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { MfaSetupPage } from './mfa-setup.page';

const routes: Routes = [{ path: '', component: MfaSetupPage }];

@NgModule({
  declarations: [MfaSetupPage],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class MfaSetupPageModule {}
