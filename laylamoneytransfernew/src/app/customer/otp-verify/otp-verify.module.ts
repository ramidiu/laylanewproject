import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SharedModule } from '../../shared/shared.module';
import { OtpVerifyPage } from './otp-verify.page';

const routes: Routes = [
  { path: '', component: OtpVerifyPage }
];

@NgModule({
  declarations: [OtpVerifyPage],
  imports: [
    SharedModule,
    RouterModule.forChild(routes)
  ]
})
export class OtpVerifyPageModule {}
